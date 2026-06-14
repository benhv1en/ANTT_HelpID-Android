using System.Security.Cryptography;
using HelpId.Api.Data;
using HelpId.Api.Data.Entities;
using HelpId.Api.Security;
using Microsoft.EntityFrameworkCore;
using Microsoft.Extensions.Options;

namespace HelpId.Api.Auth;

public interface IAuthService
{
    Task<AuthOperationResult<AuthResponse>> RegisterAsync(
        RegisterRequest request,
        ClientRequestInfo clientInfo,
        CancellationToken cancellationToken = default
    );

    Task<AuthOperationResult<AuthResponse>> LoginAsync(
        LoginRequest request,
        ClientRequestInfo clientInfo,
        CancellationToken cancellationToken = default
    );

    Task<AuthOperationResult<AuthResponse>> RefreshAsync(
        RefreshRequest request,
        ClientRequestInfo clientInfo,
        CancellationToken cancellationToken = default
    );

    Task<AuthOperationResult> LogoutAsync(
        LogoutRequest request,
        CancellationToken cancellationToken = default
    );

    Task<AuthOperationResult<AuthUserResponse>> GetMeAsync(
        string userId,
        CancellationToken cancellationToken = default
    );
}

public sealed class AuthService(
    HelpIdDbContext dbContext,
    IAuthRequestValidator validator,
    IPasswordHasher passwordHasher,
    ITokenHasher tokenHasher,
    IJwtAccessTokenService jwtAccessTokenService,
    IOptions<AuthOptions> options
) : IAuthService
{
    private readonly AuthOptions _options = options.Value;

    public async Task<AuthOperationResult<AuthResponse>> RegisterAsync(
        RegisterRequest request,
        ClientRequestInfo clientInfo,
        CancellationToken cancellationToken = default
    )
    {
        var validationErrors = validator.ValidateRegister(request);
        if (validationErrors.Count > 0)
        {
            return AuthOperationResult<AuthResponse>.ValidationFailed(validationErrors);
        }

        var email = request.Email!.Trim();
        var normalizedEmail = NormalizeEmail(email);
        var now = DateTimeOffset.UtcNow;

        var emailExists = await dbContext.Users
            .AsNoTracking()
            .AnyAsync(user => user.NormalizedEmail == normalizedEmail, cancellationToken);
        if (emailExists)
        {
            return AuthOperationResult<AuthResponse>.DuplicateEmail();
        }

        var user = new User
        {
            Id = Guid.NewGuid().ToString("N"),
            Email = email,
            NormalizedEmail = normalizedEmail,
            PasswordHash = passwordHasher.HashPassword(request.Password!),
            DisplayName = NormalizeOptional(request.DisplayName),
            SecurityStamp = Guid.NewGuid().ToString("N"),
            CreatedAtUtc = now,
            UpdatedAtUtc = now
        };

        dbContext.Users.Add(user);
        dbContext.UserProfiles.Add(new UserProfile
        {
            UserId = user.Id,
            CreatedAtUtc = now,
            UpdatedAtUtc = now,
            LastUpdatedUtc = now
        });
        dbContext.UserRoles.Add(new UserRole
        {
            UserId = user.Id,
            RoleId = HelpIdAuthorizationDefaults.Roles.UserId,
            AssignedAtUtc = now
        });

        var roles = new[] { HelpIdAuthorizationDefaults.Roles.UserName };
        var permissions = UserRolePermissionCodes;
        var response = CreateAuthResponse(user, roles, permissions, clientInfo, now);

        try
        {
            await dbContext.SaveChangesAsync(cancellationToken);
        }
        catch (DbUpdateException)
        {
            return AuthOperationResult<AuthResponse>.DuplicateEmail();
        }

        return AuthOperationResult<AuthResponse>.Created(response);
    }

    public async Task<AuthOperationResult<AuthResponse>> LoginAsync(
        LoginRequest request,
        ClientRequestInfo clientInfo,
        CancellationToken cancellationToken = default
    )
    {
        var validationErrors = validator.ValidateLogin(request);
        if (validationErrors.Count > 0)
        {
            return AuthOperationResult<AuthResponse>.ValidationFailed(validationErrors);
        }

        var normalizedEmail = NormalizeEmail(request.Email!.Trim());
        var user = await dbContext.Users
            .SingleOrDefaultAsync(
                candidate => candidate.NormalizedEmail == normalizedEmail && candidate.DeletedAtUtc == null,
                cancellationToken
            );

        if (user is null)
        {
            return AuthOperationResult<AuthResponse>.InvalidCredentials();
        }

        var now = DateTimeOffset.UtcNow;
        if (user.LockoutUntilUtc is { } lockoutUntil && lockoutUntil > now)
        {
            return AuthOperationResult<AuthResponse>.LockedOut(lockoutUntil);
        }

        if (!passwordHasher.VerifyPassword(request.Password!, user.PasswordHash))
        {
            user.FailedLoginCount += 1;
            user.UpdatedAtUtc = now;

            if (user.FailedLoginCount >= _options.LockoutFailedAttempts)
            {
                user.LockoutUntilUtc = now.Add(_options.LockoutDuration);
                await dbContext.SaveChangesAsync(cancellationToken);
                return AuthOperationResult<AuthResponse>.LockedOut(user.LockoutUntilUtc.Value);
            }

            await dbContext.SaveChangesAsync(cancellationToken);
            return AuthOperationResult<AuthResponse>.InvalidCredentials();
        }

        user.FailedLoginCount = 0;
        user.LockoutUntilUtc = null;
        user.LastLoginAtUtc = now;
        user.UpdatedAtUtc = now;

        var principalData = await LoadUserPrincipalDataAsync(user.Id, cancellationToken);
        var response = CreateAuthResponse(
            user,
            principalData.Roles,
            principalData.Permissions,
            clientInfo,
            now
        );

        await dbContext.SaveChangesAsync(cancellationToken);
        return AuthOperationResult<AuthResponse>.Ok(response);
    }

    public async Task<AuthOperationResult<AuthResponse>> RefreshAsync(
        RefreshRequest request,
        ClientRequestInfo clientInfo,
        CancellationToken cancellationToken = default
    )
    {
        var validationErrors = validator.ValidateRefreshToken(request.RefreshToken);
        if (validationErrors.Count > 0)
        {
            return AuthOperationResult<AuthResponse>.ValidationFailed(validationErrors);
        }

        var tokenHash = tokenHasher.HashToken(request.RefreshToken!);
        var refreshToken = await dbContext.RefreshTokens
            .Include(token => token.User)
            .SingleOrDefaultAsync(token => token.TokenHash == tokenHash, cancellationToken);

        var now = DateTimeOffset.UtcNow;
        if (
            refreshToken is null ||
            refreshToken.RevokedAtUtc is not null ||
            refreshToken.ExpiresAtUtc <= now ||
            refreshToken.User.DeletedAtUtc is not null
        )
        {
            return AuthOperationResult<AuthResponse>.InvalidRefreshToken();
        }

        var principalData = await LoadUserPrincipalDataAsync(refreshToken.UserId, cancellationToken);
        var response = CreateAuthResponse(
            refreshToken.User,
            principalData.Roles,
            principalData.Permissions,
            clientInfo,
            now,
            refreshToken.TokenFamilyId
        );

        refreshToken.RevokedAtUtc = now;
        refreshToken.ReplacedByTokenId = dbContext.ChangeTracker
            .Entries<RefreshToken>()
            .Select(entry => entry.Entity)
            .Single(token => token.TokenHash == tokenHasher.HashToken(response.RefreshToken))
            .Id;

        await dbContext.SaveChangesAsync(cancellationToken);
        return AuthOperationResult<AuthResponse>.Ok(response);
    }

    public async Task<AuthOperationResult> LogoutAsync(
        LogoutRequest request,
        CancellationToken cancellationToken = default
    )
    {
        var validationErrors = validator.ValidateRefreshToken(request.RefreshToken);
        if (validationErrors.Count > 0)
        {
            return AuthOperationResult.ValidationFailed(validationErrors);
        }

        var tokenHash = tokenHasher.HashToken(request.RefreshToken!);
        var refreshToken = await dbContext.RefreshTokens
            .SingleOrDefaultAsync(token => token.TokenHash == tokenHash, cancellationToken);

        if (refreshToken is not null && refreshToken.RevokedAtUtc is null)
        {
            refreshToken.RevokedAtUtc = DateTimeOffset.UtcNow;
            await dbContext.SaveChangesAsync(cancellationToken);
        }

        return AuthOperationResult.Ok();
    }

    public async Task<AuthOperationResult<AuthUserResponse>> GetMeAsync(
        string userId,
        CancellationToken cancellationToken = default
    )
    {
        var user = await dbContext.Users
            .AsNoTracking()
            .SingleOrDefaultAsync(
                candidate => candidate.Id == userId && candidate.DeletedAtUtc == null,
                cancellationToken
            );

        if (user is null)
        {
            return AuthOperationResult<AuthUserResponse>.Unauthorized();
        }

        var principalData = await LoadUserPrincipalDataAsync(user.Id, cancellationToken);
        return AuthOperationResult<AuthUserResponse>.Ok(ToUserResponse(
            user,
            principalData.Roles,
            principalData.Permissions
        ));
    }

    private AuthResponse CreateAuthResponse(
        User user,
        IReadOnlyList<string> roles,
        IReadOnlyList<string> permissions,
        ClientRequestInfo clientInfo,
        DateTimeOffset now,
        string? tokenFamilyId = null
    )
    {
        var accessToken = jwtAccessTokenService.IssueAccessToken(user, roles, permissions);
        var refreshToken = CreateRefreshToken(
            user.Id,
            tokenFamilyId ?? Guid.NewGuid().ToString("N"),
            clientInfo,
            now
        );

        dbContext.RefreshTokens.Add(refreshToken.Entity);

        return new AuthResponse(
            accessToken.Token,
            accessToken.ExpiresAtUtc,
            refreshToken.PlainTextToken,
            refreshToken.Entity.ExpiresAtUtc,
            ToUserResponse(user, roles, permissions)
        );
    }

    private RefreshTokenIssueResult CreateRefreshToken(
        string userId,
        string tokenFamilyId,
        ClientRequestInfo clientInfo,
        DateTimeOffset now
    )
    {
        var plainTextToken = Base64Url.Encode(RandomNumberGenerator.GetBytes(64));
        var entity = new RefreshToken
        {
            Id = Guid.NewGuid().ToString("N"),
            UserId = userId,
            TokenHash = tokenHasher.HashToken(plainTextToken),
            TokenFamilyId = tokenFamilyId,
            CreatedAtUtc = now,
            ExpiresAtUtc = now.Add(_options.RefreshTokenLifetime),
            DeviceName = NormalizeOptional(clientInfo.DeviceName),
            UserAgentHash = NormalizeOptional(clientInfo.UserAgentHash),
            CreatedByIpHash = NormalizeOptional(clientInfo.IpHash)
        };

        return new RefreshTokenIssueResult(plainTextToken, entity);
    }

    private async Task<UserPrincipalData> LoadUserPrincipalDataAsync(
        string userId,
        CancellationToken cancellationToken
    )
    {
        var roles = await dbContext.UserRoles
            .AsNoTracking()
            .Where(userRole => userRole.UserId == userId)
            .Join(
                dbContext.Roles,
                userRole => userRole.RoleId,
                role => role.Id,
                (_, role) => role.Name
            )
            .OrderBy(roleName => roleName)
            .ToListAsync(cancellationToken);

        var roleIds = await dbContext.UserRoles
            .AsNoTracking()
            .Where(userRole => userRole.UserId == userId)
            .Select(userRole => userRole.RoleId)
            .ToListAsync(cancellationToken);

        var permissions = await dbContext.RolePermissions
            .AsNoTracking()
            .Where(rolePermission => roleIds.Contains(rolePermission.RoleId))
            .Join(
                dbContext.Permissions,
                rolePermission => rolePermission.PermissionId,
                permission => permission.Id,
                (_, permission) => permission.Code
            )
            .Distinct()
            .OrderBy(permissionCode => permissionCode)
            .ToListAsync(cancellationToken);

        return new UserPrincipalData(roles, permissions);
    }

    private static AuthUserResponse ToUserResponse(
        User user,
        IReadOnlyList<string> roles,
        IReadOnlyList<string> permissions
    )
    {
        return new AuthUserResponse(
            user.Id,
            user.Email,
            user.DisplayName,
            user.IsEmailVerified,
            roles,
            permissions
        );
    }

    private static string NormalizeEmail(string email) => email.Trim().ToUpperInvariant();

    private static string? NormalizeOptional(string? value)
    {
        var normalized = value?.Trim();
        return string.IsNullOrWhiteSpace(normalized) ? null : normalized;
    }

    private static readonly string[] UserRolePermissionCodes =
    {
        HelpIdAuthorizationDefaults.Permissions.AuthSessionSelf,
        HelpIdAuthorizationDefaults.Permissions.EmergencyLinkMintSelf,
        HelpIdAuthorizationDefaults.Permissions.ProfileReadSelf,
        HelpIdAuthorizationDefaults.Permissions.ProfileWriteSelf
    };

    private sealed record RefreshTokenIssueResult(string PlainTextToken, RefreshToken Entity);

    private sealed record UserPrincipalData(
        IReadOnlyList<string> Roles,
        IReadOnlyList<string> Permissions
    );
}
