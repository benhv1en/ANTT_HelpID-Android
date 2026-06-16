using HelpId.Api.Data;
using HelpId.Api.Data.Entities;
using HelpId.Api.Security;
using Microsoft.EntityFrameworkCore;

namespace HelpId.Api.Admin;

public interface IAdminService
{
    Task<AdminStatsDto> GetStatsAsync(CancellationToken cancellationToken = default);

    Task<AdminUsersPageDto> GetUsersAsync(
        int page,
        int pageSize,
        CancellationToken cancellationToken = default
    );

    Task<AdminOperationResult> AssignRoleAsync(
        string callerUserId,
        string targetUserId,
        string roleId,
        CancellationToken cancellationToken = default
    );

    Task<AdminOperationResult> RevokeRoleAsync(
        string callerUserId,
        string targetUserId,
        string roleId,
        CancellationToken cancellationToken = default
    );
}

public sealed class AdminService(HelpIdDbContext dbContext) : IAdminService
{
    private static readonly IReadOnlySet<string> AllowedRoleIds =
        new HashSet<string>(StringComparer.Ordinal)
        {
            HelpIdAuthorizationDefaults.Roles.UserId,
            HelpIdAuthorizationDefaults.Roles.AdminId
        };

    public async Task<AdminStatsDto> GetStatsAsync(CancellationToken cancellationToken = default)
    {
        var cutoff = DateTimeOffset.UtcNow.AddDays(-7);

        var totalUsers = await dbContext.Users
            .CountAsync(u => u.DeletedAtUtc == null, cancellationToken);

        var totalProfiles = await dbContext.UserProfiles
            .CountAsync(cancellationToken);

        var totalPublicLinks = await dbContext.PublicProfileLinks
            .CountAsync(cancellationToken);

        // EF Core SQLite does not support DateTimeOffset comparisons in LINQ-to-SQL;
        // pull timestamps client-side (DateTimeOffset is tiny, admin-only endpoint).
        var auditTimestamps = await dbContext.AuditEvents
            .Select(a => a.CreatedAtUtc)
            .ToListAsync(cancellationToken);
        var auditEventsLast7Days = auditTimestamps.Count(t => t >= cutoff);

        return new AdminStatsDto(
            TotalUsers: totalUsers,
            TotalProfiles: totalProfiles,
            TotalPublicLinks: totalPublicLinks,
            AuditEventsLast7Days: auditEventsLast7Days
        );
    }

    public async Task<AdminUsersPageDto> GetUsersAsync(
        int page,
        int pageSize,
        CancellationToken cancellationToken = default
    )
    {
        // EF Core SQLite does not support DateTimeOffset in ORDER BY.
        // Pull all rows first (admin-only endpoint; user counts are manageable),
        // then sort and paginate client-side.
        var rawRows = await dbContext.Users
            .Where(u => u.DeletedAtUtc == null)
            .Select(u => new
            {
                u.Id,
                u.Email,
                u.DisplayName,
                u.LockoutUntilUtc,
                u.CreatedAtUtc,
                RoleNames = u.UserRoles.Select(ur => ur.Role.Name).ToList()
            })
            .ToListAsync(cancellationToken);

        var totalCount = rawRows.Count;
        var now = DateTimeOffset.UtcNow;

        var users = rawRows
            .OrderByDescending(r => r.CreatedAtUtc)
            .Skip((page - 1) * pageSize)
            .Take(pageSize)
            .Select(r => new AdminUserDto(
                UserId: r.Id,
                Email: r.Email,
                DisplayName: r.DisplayName,
                Roles: r.RoleNames,
                IsLocked: r.LockoutUntilUtc.HasValue && r.LockoutUntilUtc.Value > now,
                CreatedAtUtc: r.CreatedAtUtc
            ))
            .ToList();

        return new AdminUsersPageDto(
            Users: users,
            Page: page,
            PageSize: pageSize,
            TotalCount: totalCount
        );
    }

    public async Task<AdminOperationResult> AssignRoleAsync(
        string callerUserId,
        string targetUserId,
        string roleId,
        CancellationToken cancellationToken = default
    )
    {
        if (!AllowedRoleIds.Contains(roleId))
        {
            return new AdminOperationResult(
                AdminOperationStatus.BadRequest,
                $"Role '{roleId}' is not a valid assignable role."
            );
        }

        var targetExists = await dbContext.Users
            .AnyAsync(u => u.Id == targetUserId && u.DeletedAtUtc == null, cancellationToken);

        if (!targetExists)
        {
            return new AdminOperationResult(AdminOperationStatus.NotFound, "User not found.");
        }

        var alreadyHasRole = await dbContext.UserRoles
            .AnyAsync(
                ur => ur.UserId == targetUserId && ur.RoleId == roleId,
                cancellationToken
            );

        if (!alreadyHasRole)
        {
            dbContext.UserRoles.Add(new UserRole
            {
                UserId = targetUserId,
                RoleId = roleId,
                AssignedAtUtc = DateTimeOffset.UtcNow
            });

            dbContext.AuditEvents.Add(new AuditEvent
            {
                Id = NewId(),
                UserId = callerUserId,
                EventType = "admin.role.assign",
                ReasonCode = roleId,
                Success = true,
                CreatedAtUtc = DateTimeOffset.UtcNow
            });

            await dbContext.SaveChangesAsync(cancellationToken);
        }

        return new AdminOperationResult(AdminOperationStatus.Ok);
    }

    public async Task<AdminOperationResult> RevokeRoleAsync(
        string callerUserId,
        string targetUserId,
        string roleId,
        CancellationToken cancellationToken = default
    )
    {
        if (!AllowedRoleIds.Contains(roleId))
        {
            return new AdminOperationResult(
                AdminOperationStatus.BadRequest,
                $"Role '{roleId}' is not a valid revocable role."
            );
        }

        // Admin cannot revoke their own admin role
        if (string.Equals(callerUserId, targetUserId, StringComparison.Ordinal)
            && string.Equals(roleId, HelpIdAuthorizationDefaults.Roles.AdminId, StringComparison.Ordinal))
        {
            return new AdminOperationResult(
                AdminOperationStatus.Forbidden,
                "You cannot revoke your own admin role."
            );
        }

        var targetExists = await dbContext.Users
            .AnyAsync(u => u.Id == targetUserId && u.DeletedAtUtc == null, cancellationToken);

        if (!targetExists)
        {
            return new AdminOperationResult(AdminOperationStatus.NotFound, "User not found.");
        }

        var userRole = await dbContext.UserRoles
            .FirstOrDefaultAsync(
                ur => ur.UserId == targetUserId && ur.RoleId == roleId,
                cancellationToken
            );

        if (userRole is not null)
        {
            dbContext.UserRoles.Remove(userRole);

            dbContext.AuditEvents.Add(new AuditEvent
            {
                Id = NewId(),
                UserId = callerUserId,
                EventType = "admin.role.revoke",
                ReasonCode = roleId,
                Success = true,
                CreatedAtUtc = DateTimeOffset.UtcNow
            });

            await dbContext.SaveChangesAsync(cancellationToken);
        }

        return new AdminOperationResult(AdminOperationStatus.Ok);
    }

    private static string NewId() => Guid.NewGuid().ToString("N");
}
