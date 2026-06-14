using System.Security.Claims;
using HelpId.Api.Data;
using HelpId.Api.Data.Entities;
using HelpId.Api.Profiles;
using HelpId.Api.Security;
using Microsoft.AspNetCore.Authorization;
using Microsoft.AspNetCore.Authorization.Infrastructure;
using Microsoft.AspNetCore.Http;
using Microsoft.Data.Sqlite;
using Microsoft.EntityFrameworkCore;
using Microsoft.EntityFrameworkCore.Metadata;
using Xunit;

namespace HelpId.Api.Tests;

public sealed class AuthorizationAccessTests
{

    [Fact]
    public void Role_permission_model_uses_unique_indexes_and_composite_keys()
    {
        using var context = CreateMetadataContext();
        var model = context.Model;

        Assert.Equal("Roles", Entity<Role>(model).GetTableName());
        Assert.Equal("Permissions", Entity<Permission>(model).GetTableName());
        Assert.Equal("UserRoles", Entity<UserRole>(model).GetTableName());
        Assert.Equal("RolePermissions", Entity<RolePermission>(model).GetTableName());

        Assert.True(FindIndex(Entity<Role>(model), nameof(Role.NormalizedName)).IsUnique);
        Assert.True(FindIndex(Entity<Permission>(model), nameof(Permission.Code)).IsUnique);
        Assert.Equal(
            new[] { nameof(UserRole.UserId), nameof(UserRole.RoleId) },
            Entity<UserRole>(model).FindPrimaryKey()?.Properties.Select(property => property.Name)
        );
        Assert.Equal(
            new[] { nameof(RolePermission.RoleId), nameof(RolePermission.PermissionId) },
            Entity<RolePermission>(model).FindPrimaryKey()?.Properties.Select(property => property.Name)
        );
    }

    [Fact]
    public async Task Role_permission_seed_contains_default_user_and_admin_permissions()
    {
        await using var database = await SqliteTestDatabase.CreateAsync();
        var roles = await database.Context.Roles
            .AsNoTracking()
            .Select(role => role.NormalizedName)
            .ToListAsync();

        var permissions = await database.Context.Permissions
            .AsNoTracking()
            .Select(permission => permission.Code)
            .ToListAsync();

        Assert.Contains(HelpIdAuthorizationDefaults.Roles.UserNormalizedName, roles);
        Assert.Contains(HelpIdAuthorizationDefaults.Roles.AdminNormalizedName, roles);
        Assert.Contains(HelpIdAuthorizationDefaults.Permissions.ProfileReadSelf, permissions);
        Assert.Contains(HelpIdAuthorizationDefaults.Permissions.ProfileWriteSelf, permissions);
        Assert.Contains(HelpIdAuthorizationDefaults.Permissions.EmergencyLinkMintSelf, permissions);
        Assert.Contains(HelpIdAuthorizationDefaults.Permissions.AuthSessionSelf, permissions);
        Assert.Contains(HelpIdAuthorizationDefaults.Permissions.AdminMetadataRead, permissions);

        var adminPermissionCount = await database.Context.RolePermissions
            .AsNoTracking()
            .CountAsync(rolePermission =>
                rolePermission.RoleId == HelpIdAuthorizationDefaults.Roles.AdminId
            );

        Assert.Equal(5, adminPermissionCount);
    }

    [Fact]
    public async Task Owned_resource_service_blocks_cross_user_profile_link_and_refresh_token()
    {
        await using var database = await SqliteTestDatabase.CreateAsync();
        await SeedTwoUsersAsync(database.Context);
        var service = new OwnedResourceAuthorizationService(database.Context);

        Assert.True(await service.CanAccessProfileAsync(UserAId, UserAId));
        Assert.True(await service.CanAccessPublicProfileLinkAsync(UserAId, PublicKeyA));
        Assert.True(await service.CanAccessRefreshTokenAsync(UserAId, RefreshTokenAId));

        Assert.False(await service.CanAccessProfileAsync(UserAId, UserBId));
        Assert.False(await service.CanAccessPublicProfileLinkAsync(UserAId, PublicKeyB));
        Assert.False(await service.CanAccessRefreshTokenAsync(UserAId, RefreshTokenBId));
    }

    [Fact]
    public void Current_user_context_reads_user_id_from_jwt_subject()
    {
        var httpContext = new DefaultHttpContext
        {
            User = Principal(
                new Claim(HelpIdAuthorizationDefaults.SubjectClaimType, UserAId),
                new Claim("client_user_id", UserBId)
            )
        };

        var userContext = new CurrentUserContext(new HttpContextAccessor
        {
            HttpContext = httpContext
        });

        Assert.True(userContext.IsAuthenticated);
        Assert.Equal(UserAId, userContext.GetRequiredUserId());
    }

    [Fact]
    public async Task Permission_handler_requires_permission_claim_and_admin_policy_requires_admin_role()
    {
        var handler = new PermissionAuthorizationHandler();
        var requirement = new PermissionRequirement(
            HelpIdAuthorizationDefaults.Permissions.AdminMetadataRead
        );

        var authorizedContext = new AuthorizationHandlerContext(
            new[] { requirement },
            Principal(
                new Claim(ClaimTypes.Role, HelpIdAuthorizationDefaults.Roles.AdminName),
                new Claim(
                    HelpIdAuthorizationDefaults.PermissionClaimType,
                    HelpIdAuthorizationDefaults.Permissions.AdminMetadataRead
                )
            ),
            resource: null
        );
        await handler.HandleAsync(authorizedContext);

        var unauthorizedContext = new AuthorizationHandlerContext(
            new[] { requirement },
            Principal(new Claim(ClaimTypes.Role, HelpIdAuthorizationDefaults.Roles.UserName)),
            resource: null
        );
        await handler.HandleAsync(unauthorizedContext);

        Assert.True(authorizedContext.HasSucceeded);
        Assert.False(unauthorizedContext.HasSucceeded);

        var options = new AuthorizationOptions();
        HelpIdAuthorizationServiceCollectionExtensions.AddHelpIdPolicies(options);
        var adminPolicy = options.GetPolicy(HelpIdAuthorizationPolicies.AdminMetadata)
            ?? throw new InvalidOperationException("Admin policy was not configured.");

        var roleRequirement = adminPolicy.Requirements.OfType<RolesAuthorizationRequirement>().Single();
        Assert.Contains(HelpIdAuthorizationDefaults.Roles.AdminName, roleRequirement.AllowedRoles);
    }

    [Fact]
    public async Task Public_profile_requires_valid_public_token_and_returns_whitelist_only()
    {
        await using var database = await SqliteTestDatabase.CreateAsync();
        await SeedTwoUsersAsync(database.Context);
        var service = new PublicProfileAccessService(
            database.Context,
            new TestPublicProfileTokenValidator(isValid: true)
        );

        var result = await service.GetProfileAsync(PublicKeyA, "test-public-token");

        Assert.Equal(PublicProfileAccessStatus.Ok, result.Status);
        Assert.NotNull(result.Profile);
        Assert.Equal("User A", result.Profile.Name);
        Assert.Equal("O+", result.Profile.BloodGroup);
        Assert.Equal(new[] { "Allergy A" }, result.Profile.Allergies);
        Assert.Equal(new[] { "Medical note A" }, result.Profile.MedicalNotes);
        Assert.Single(result.Profile.EmergencyContacts);

        var publicProfileProperties = typeof(PublicEmergencyProfileResponse)
            .GetProperties()
            .Select(property => property.Name)
            .OrderBy(name => name)
            .ToArray();

        Assert.Equal(
            new[]
            {
                nameof(PublicEmergencyProfileResponse.Address),
                nameof(PublicEmergencyProfileResponse.Allergies),
                nameof(PublicEmergencyProfileResponse.BloodGroup),
                nameof(PublicEmergencyProfileResponse.EmergencyContacts),
                nameof(PublicEmergencyProfileResponse.MedicalNotes),
                nameof(PublicEmergencyProfileResponse.Name)
            },
            publicProfileProperties
        );
        Assert.DoesNotContain("UserId", publicProfileProperties);
        Assert.DoesNotContain("Language", publicProfileProperties);

        var blockedService = new PublicProfileAccessService(
            database.Context,
            new TestPublicProfileTokenValidator(isValid: false)
        );
        var blockedResult = await blockedService.GetProfileAsync(PublicKeyA, "invalid-public-token");

        Assert.Equal(PublicProfileAccessStatus.Forbidden, blockedResult.Status);
        Assert.Null(blockedResult.Profile);
    }

    private const string UserAId = "user-a";
    private const string UserBId = "user-b";
    private const string PublicKeyA = "HID-USER-A";
    private const string PublicKeyB = "HID-USER-B";
    private const string RefreshTokenAId = "refresh-token-a";
    private const string RefreshTokenBId = "refresh-token-b";

    private static ClaimsPrincipal Principal(params Claim[] claims)
    {
        return new ClaimsPrincipal(new ClaimsIdentity(claims, authenticationType: "Test"));
    }


    private static HelpIdDbContext CreateMetadataContext()
    {
        var options = new DbContextOptionsBuilder<HelpIdDbContext>()
            .UseSqlite("Data Source=:memory:")
            .Options;

        return new HelpIdDbContext(options);
    }

    private static IEntityType Entity<TEntity>(IModel model)
    {
        return model.FindEntityType(typeof(TEntity))
            ?? throw new InvalidOperationException($"Entity {typeof(TEntity).Name} was not configured.");
    }

    private static IIndex FindIndex(IEntityType entity, params string[] propertyNames)
    {
        return entity.GetIndexes().Single(index =>
            index.Properties.Select(property => property.Name).SequenceEqual(propertyNames)
        );
    }

    private static async Task SeedTwoUsersAsync(HelpIdDbContext context)
    {
        var now = new DateTimeOffset(2026, 6, 14, 9, 0, 0, TimeSpan.Zero);

        context.Users.AddRange(
            CreateUser(UserAId, "user-a@example.test", now),
            CreateUser(UserBId, "user-b@example.test", now)
        );

        context.UserProfiles.AddRange(
            new UserProfile
            {
                UserId = UserAId,
                FullName = "User A",
                BloodGroup = "O+",
                Address = "Address A",
                Language = "vi",
                CreatedAtUtc = now,
                UpdatedAtUtc = now,
                LastUpdatedUtc = now
            },
            new UserProfile
            {
                UserId = UserBId,
                FullName = "User B",
                BloodGroup = "A+",
                Address = "Address B",
                Language = "vi",
                CreatedAtUtc = now,
                UpdatedAtUtc = now,
                LastUpdatedUtc = now
            }
        );

        context.ProfileAllergies.Add(new ProfileAllergy
        {
            Id = "allergy-a",
            UserId = UserAId,
            Value = "Allergy A",
            SortOrder = 0,
            CreatedAtUtc = now,
            UpdatedAtUtc = now
        });

        context.MedicalNotes.Add(new MedicalNote
        {
            Id = "note-a",
            UserId = UserAId,
            Value = "Medical note A",
            SortOrder = 0,
            CreatedAtUtc = now,
            UpdatedAtUtc = now
        });

        context.EmergencyContacts.Add(new EmergencyContact
        {
            Id = "contact-a",
            UserId = UserAId,
            Name = "Contact A",
            Phone = "0000000000",
            Relationship = "Test",
            SortOrder = 0,
            CreatedAtUtc = now,
            UpdatedAtUtc = now
        });

        context.PublicProfileLinks.AddRange(
            new PublicProfileLink
            {
                PublicKey = PublicKeyA,
                UserId = UserAId,
                CreatedAtUtc = now,
                UpdatedAtUtc = now,
                LastMintedAtUtc = now
            },
            new PublicProfileLink
            {
                PublicKey = PublicKeyB,
                UserId = UserBId,
                CreatedAtUtc = now,
                UpdatedAtUtc = now,
                LastMintedAtUtc = now
            }
        );

        context.RefreshTokens.AddRange(
            new RefreshToken
            {
                Id = RefreshTokenAId,
                UserId = UserAId,
                TokenHash = "refresh-token-hash-a",
                TokenFamilyId = "refresh-family-a",
                CreatedAtUtc = now,
                ExpiresAtUtc = now.AddDays(30)
            },
            new RefreshToken
            {
                Id = RefreshTokenBId,
                UserId = UserBId,
                TokenHash = "refresh-token-hash-b",
                TokenFamilyId = "refresh-family-b",
                CreatedAtUtc = now,
                ExpiresAtUtc = now.AddDays(30)
            }
        );

        await context.SaveChangesAsync();
    }

    private static User CreateUser(string userId, string email, DateTimeOffset now)
    {
        return new User
        {
            Id = userId,
            Email = email,
            NormalizedEmail = email.ToUpperInvariant(),
            PasswordHash = "password-hash",
            SecurityStamp = $"{userId}-security-stamp",
            CreatedAtUtc = now,
            UpdatedAtUtc = now
        };
    }

    private sealed class TestPublicProfileTokenValidator(bool isValid) : IPublicProfileTokenValidator
    {
        public ValueTask<PublicProfileTokenValidationResult> ValidateAsync(
            string publicKey,
            string publicProfileJwt,
            CancellationToken cancellationToken = default
        )
        {
            return ValueTask.FromResult(isValid
                ? PublicProfileTokenValidationResult.Valid(
                    DateTimeOffset.UtcNow.AddMinutes(-5),
                    DateTimeOffset.UtcNow.AddMinutes(30)
                )
                : PublicProfileTokenValidationResult.Invalid());
        }
    }

    private sealed class SqliteTestDatabase : IAsyncDisposable
    {
        private SqliteTestDatabase(SqliteConnection connection, HelpIdDbContext context)
        {
            Connection = connection;
            Context = context;
        }

        public HelpIdDbContext Context { get; }

        private SqliteConnection Connection { get; }

        public static async Task<SqliteTestDatabase> CreateAsync()
        {
            var connection = new SqliteConnection("Data Source=:memory:");
            await connection.OpenAsync();

            var options = new DbContextOptionsBuilder<HelpIdDbContext>()
                .UseSqlite(connection)
                .Options;

            var context = new HelpIdDbContext(options);
            await context.Database.EnsureCreatedAsync();

            return new SqliteTestDatabase(connection, context);
        }

        public async ValueTask DisposeAsync()
        {
            await Context.DisposeAsync();
            await Connection.DisposeAsync();
        }
    }
}
