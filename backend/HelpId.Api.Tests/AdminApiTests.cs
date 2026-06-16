using System.Security.Claims;
using System.Text.Json;
using HelpId.Api.Admin;
using HelpId.Api.Data;
using HelpId.Api.Data.Entities;
using HelpId.Api.Security;
using Microsoft.AspNetCore.Authorization;
using Microsoft.AspNetCore.Authorization.Infrastructure;
using Microsoft.AspNetCore.Http;
using Microsoft.Data.Sqlite;
using Microsoft.EntityFrameworkCore;
using Microsoft.Extensions.DependencyInjection;
using Xunit;

namespace HelpId.Api.Tests;

public sealed class AdminApiTests
{
    // ── 1. Regular user is denied by AdminMetadata policy ────────────────────

    [Fact]
    public async Task Regular_user_is_denied_by_admin_metadata_policy()
    {
        var handler = new PermissionAuthorizationHandler();
        var requirement = new PermissionRequirement(HelpIdAuthorizationDefaults.Permissions.AdminMetadataRead);

        var userContext = new AuthorizationHandlerContext(
            new[] { requirement },
            AdminPrincipal(
                new Claim(ClaimTypes.Role, HelpIdAuthorizationDefaults.Roles.UserName),
                new Claim(HelpIdAuthorizationDefaults.PermissionClaimType, HelpIdAuthorizationDefaults.Permissions.ProfileReadSelf),
                new Claim(HelpIdAuthorizationDefaults.PermissionClaimType, HelpIdAuthorizationDefaults.Permissions.ProfileWriteSelf)
            ),
            resource: null
        );
        await handler.HandleAsync(userContext);
        Assert.False(userContext.HasSucceeded);

        var options = new AuthorizationOptions();
        HelpIdAuthorizationServiceCollectionExtensions.AddHelpIdPolicies(options);
        var adminPolicy = options.GetPolicy(HelpIdAuthorizationPolicies.AdminMetadata)
            ?? throw new InvalidOperationException("AdminMetadata policy not configured.");

        var roleRequirement = adminPolicy.Requirements.OfType<RolesAuthorizationRequirement>().Single();
        Assert.Contains(HelpIdAuthorizationDefaults.Roles.AdminName, roleRequirement.AllowedRoles);
        Assert.DoesNotContain(HelpIdAuthorizationDefaults.Roles.UserName, roleRequirement.AllowedRoles);
    }

    // ── 2. Unauthenticated is denied by AdminMetadata policy ─────────────────

    [Fact]
    public void Unauthenticated_is_denied_by_admin_metadata_policy()
    {
        var options = new AuthorizationOptions();
        HelpIdAuthorizationServiceCollectionExtensions.AddHelpIdPolicies(options);
        var adminPolicy = options.GetPolicy(HelpIdAuthorizationPolicies.AdminMetadata)
            ?? throw new InvalidOperationException("AdminMetadata policy not configured.");

        Assert.True(
            adminPolicy.Requirements.OfType<DenyAnonymousAuthorizationRequirement>().Any(),
            "AdminMetadata policy must deny anonymous users."
        );
    }

    // ── 3. GET /api/v1/admin/stats → 200 with correct schema ─────────────────

    [Fact]
    public async Task GetStats_returns_200_with_correct_schema()
    {
        await using var env = await AdminTestEnvironment.CreateAsync();
        await SeedAdminAndUserAsync(env.Context);

        var result = await env.ExecuteAsync<JsonElement>(
            await AdminEndpoints.GetStatsAsync(env.AdminService, CancellationToken.None)
        );

        Assert.Equal(StatusCodes.Status200OK, result.StatusCode);
        Assert.Equal(JsonValueKind.Object, result.Body.ValueKind);
        Assert.True(result.Body.TryGetProperty("totalUsers", out var totalUsers), "Response must have 'totalUsers'.");
        Assert.True(result.Body.TryGetProperty("totalProfiles", out var totalProfiles), "Response must have 'totalProfiles'.");
        Assert.True(result.Body.TryGetProperty("totalPublicLinks", out var totalPublicLinks), "Response must have 'totalPublicLinks'.");
        Assert.True(result.Body.TryGetProperty("auditEventsLast7Days", out _), "Response must have 'auditEventsLast7Days'.");
        Assert.Equal(2, totalUsers.GetInt32());
        Assert.Equal(2, totalProfiles.GetInt32());
        Assert.Equal(1, totalPublicLinks.GetInt32());
    }

    // ── 4. GET /api/v1/admin/users → 200, no sensitive fields ────────────────

    [Fact]
    public async Task GetUsers_returns_200_and_excludes_sensitive_fields()
    {
        var dtoProps = typeof(AdminUserDto)
            .GetProperties()
            .Select(p => p.Name)
            .ToHashSet(StringComparer.OrdinalIgnoreCase);

        string[] forbidden = ["PasswordHash", "SecurityStamp", "TokenHash", "PhoneNumber",
                               "Allergies", "MedicalNotes", "EmergencyContacts"];
        foreach (var name in forbidden)
        {
            Assert.DoesNotContain(name, dtoProps);
        }

        await using var env = await AdminTestEnvironment.CreateAsync();
        await SeedAdminAndUserAsync(env.Context);

        var result = await env.ExecuteAsync<JsonElement>(
            await AdminEndpoints.GetUsersAsync(env.AdminService, page: 1, size: 20, CancellationToken.None)
        );

        Assert.Equal(StatusCodes.Status200OK, result.StatusCode);
        Assert.Equal(2, result.Body.GetProperty("totalCount").GetInt32());
        Assert.Equal(2, result.Body.GetProperty("users").GetArrayLength());

        foreach (var user in result.Body.GetProperty("users").EnumerateArray())
        {
            Assert.False(user.TryGetProperty("passwordHash", out _));
            Assert.False(user.TryGetProperty("securityStamp", out _));
            Assert.False(user.TryGetProperty("tokenHash", out _));
            Assert.False(user.TryGetProperty("allergies", out _));
            Assert.False(user.TryGetProperty("medicalNotes", out _));
            Assert.False(user.TryGetProperty("emergencyContacts", out _));
        }
    }

    // ── 5. Admin assigns role_admin → 204, DB updated ────────────────────────

    [Fact]
    public async Task AssignRole_returns_204_and_persists_role_and_audit_event()
    {
        await using var env = await AdminTestEnvironment.CreateAsync();
        await SeedAdminAndUserAsync(env.Context);

        var result = await env.ExecuteAsync<JsonElement>(
            await AdminEndpoints.AssignRoleAsync(
                RegularUserId,
                HelpIdAuthorizationDefaults.Roles.AdminId,
                env.UserContext,
                env.AdminService,
                CancellationToken.None
            )
        );

        Assert.Equal(StatusCodes.Status204NoContent, result.StatusCode);

        var hasRole = await env.Context.UserRoles.AnyAsync(
            ur => ur.UserId == RegularUserId && ur.RoleId == HelpIdAuthorizationDefaults.Roles.AdminId
        );
        Assert.True(hasRole, "User must have admin role after assignment.");

        var audit = await env.Context.AuditEvents.SingleAsync(
            a => a.UserId == AdminId && a.EventType == "admin.role.assign"
        );
        Assert.Equal(HelpIdAuthorizationDefaults.Roles.AdminId, audit.ReasonCode);
    }

    // ── 6. Admin revokes role_admin from other user → 204, DB updated ────────

    [Fact]
    public async Task RevokeRole_returns_204_and_removes_role_and_writes_audit_event()
    {
        await using var env = await AdminTestEnvironment.CreateAsync();
        await SeedAdminAndUserAsync(env.Context);

        await env.AdminService.AssignRoleAsync(AdminId, RegularUserId, HelpIdAuthorizationDefaults.Roles.AdminId);

        var result = await env.ExecuteAsync<JsonElement>(
            await AdminEndpoints.RevokeRoleAsync(
                RegularUserId,
                HelpIdAuthorizationDefaults.Roles.AdminId,
                env.UserContext,
                env.AdminService,
                CancellationToken.None
            )
        );

        Assert.Equal(StatusCodes.Status204NoContent, result.StatusCode);

        var hasRole = await env.Context.UserRoles.AnyAsync(
            ur => ur.UserId == RegularUserId && ur.RoleId == HelpIdAuthorizationDefaults.Roles.AdminId
        );
        Assert.False(hasRole, "Admin role must have been revoked.");

        var audit = await env.Context.AuditEvents.SingleAsync(
            a => a.UserId == AdminId && a.EventType == "admin.role.revoke"
        );
        Assert.Equal(HelpIdAuthorizationDefaults.Roles.AdminId, audit.ReasonCode);
    }

    // ── 7. Admin cannot revoke their own admin role ───────────────────────────

    [Fact]
    public async Task Admin_cannot_revoke_own_admin_role()
    {
        await using var env = await AdminTestEnvironment.CreateAsync();
        await SeedAdminAndUserAsync(env.Context);

        // Caller (AdminId) attempts to revoke their own admin role
        var result = await env.ExecuteAsync<JsonElement>(
            await AdminEndpoints.RevokeRoleAsync(
                AdminId,
                HelpIdAuthorizationDefaults.Roles.AdminId,
                env.UserContext,
                env.AdminService,
                CancellationToken.None
            )
        );

        Assert.Equal(StatusCodes.Status403Forbidden, result.StatusCode);

        var stillHasRole = await env.Context.UserRoles.AnyAsync(
            ur => ur.UserId == AdminId && ur.RoleId == HelpIdAuthorizationDefaults.Roles.AdminId
        );
        Assert.True(stillHasRole, "Admin's own role must not have been removed.");
    }

    // ── 8. SQL injection in userId path param ────────────────────────────────

    [Fact]
    public async Task Sql_injection_in_userId_returns_not_found_and_db_is_intact()
    {
        await using var env = await AdminTestEnvironment.CreateAsync();
        await SeedAdminAndUserAsync(env.Context);
        var injection = "'; DROP TABLE Users; --";

        var assignResult = await env.ExecuteAsync<JsonElement>(
            await AdminEndpoints.AssignRoleAsync(
                injection,
                HelpIdAuthorizationDefaults.Roles.AdminId,
                env.UserContext,
                env.AdminService,
                CancellationToken.None
            )
        );
        Assert.Equal(StatusCodes.Status404NotFound, assignResult.StatusCode);

        var revokeResult = await env.ExecuteAsync<JsonElement>(
            await AdminEndpoints.RevokeRoleAsync(
                injection,
                HelpIdAuthorizationDefaults.Roles.AdminId,
                env.UserContext,
                env.AdminService,
                CancellationToken.None
            )
        );
        Assert.Equal(StatusCodes.Status404NotFound, revokeResult.StatusCode);

        // LINQ parameterises queries — schema must be intact
        var userCount = await env.Context.Users.CountAsync();
        Assert.Equal(2, userCount);
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private const string AdminId = "admin-user";
    private const string RegularUserId = "regular-user";
    private static readonly DateTimeOffset SeedTime = new(2026, 6, 16, 0, 0, 0, TimeSpan.Zero);

    private static ClaimsPrincipal AdminPrincipal(params Claim[] claims)
    {
        return new ClaimsPrincipal(new ClaimsIdentity(claims, authenticationType: "Test"));
    }

    private static async Task SeedAdminAndUserAsync(HelpIdDbContext context)
    {
        context.Users.AddRange(
            new User
            {
                Id = AdminId,
                Email = "admin@example.test",
                NormalizedEmail = "ADMIN@EXAMPLE.TEST",
                PasswordHash = "hash-admin",
                SecurityStamp = "stamp-admin",
                CreatedAtUtc = SeedTime,
                UpdatedAtUtc = SeedTime
            },
            new User
            {
                Id = RegularUserId,
                Email = "user@example.test",
                NormalizedEmail = "USER@EXAMPLE.TEST",
                PasswordHash = "hash-user",
                SecurityStamp = "stamp-user",
                CreatedAtUtc = SeedTime,
                UpdatedAtUtc = SeedTime
            }
        );

        context.UserProfiles.AddRange(
            new UserProfile
            {
                UserId = AdminId,
                FullName = "Admin User",
                Language = "vi",
                CreatedAtUtc = SeedTime,
                UpdatedAtUtc = SeedTime,
                LastUpdatedUtc = SeedTime
            },
            new UserProfile
            {
                UserId = RegularUserId,
                FullName = "Regular User",
                Language = "vi",
                CreatedAtUtc = SeedTime,
                UpdatedAtUtc = SeedTime,
                LastUpdatedUtc = SeedTime
            }
        );

        context.PublicProfileLinks.Add(new PublicProfileLink
        {
            PublicKey = "HID-ADMIN",
            UserId = AdminId,
            CreatedAtUtc = SeedTime,
            UpdatedAtUtc = SeedTime,
            LastMintedAtUtc = SeedTime
        });

        context.UserRoles.Add(new UserRole
        {
            UserId = AdminId,
            RoleId = HelpIdAuthorizationDefaults.Roles.AdminId,
            AssignedAtUtc = SeedTime
        });

        context.UserRoles.Add(new UserRole
        {
            UserId = RegularUserId,
            RoleId = HelpIdAuthorizationDefaults.Roles.UserId,
            AssignedAtUtc = SeedTime
        });

        await context.SaveChangesAsync();
    }

    private sealed class AdminTestEnvironment : IAsyncDisposable
    {
        private readonly SqliteConnection _connection;
        private readonly ServiceProvider _serviceProvider;

        public HelpIdDbContext Context { get; }
        public IAdminService AdminService { get; }
        public DefaultHttpContext HttpContext { get; }
        public SimpleCurrentUserContext UserContext { get; }

        private AdminTestEnvironment(
            SqliteConnection connection,
            HelpIdDbContext context,
            IAdminService adminService,
            ServiceProvider serviceProvider
        )
        {
            _connection = connection;
            _serviceProvider = serviceProvider;
            Context = context;
            AdminService = adminService;
            UserContext = new SimpleCurrentUserContext { UserId = AdminId };
            HttpContext = new DefaultHttpContext
            {
                RequestServices = serviceProvider,
                Response = { Body = new MemoryStream() }
            };
        }

        public static async Task<AdminTestEnvironment> CreateAsync()
        {
            var connection = new SqliteConnection("Data Source=:memory:");
            await connection.OpenAsync();

            var options = new DbContextOptionsBuilder<HelpIdDbContext>()
                .UseSqlite(connection)
                .Options;

            var context = new HelpIdDbContext(options);
            await context.Database.EnsureCreatedAsync();

            var adminService = new AdminService(context);

            var serviceProvider = new ServiceCollection()
                .AddLogging()
                .AddOptions()
                .Configure<Microsoft.AspNetCore.Http.Json.JsonOptions>(o => { })
                .BuildServiceProvider();

            return new AdminTestEnvironment(connection, context, adminService, serviceProvider);
        }

        public async Task<ApiResult<T>> ExecuteAsync<T>(IResult result)
        {
            HttpContext.Response.Body = new MemoryStream();
            HttpContext.Response.StatusCode = StatusCodes.Status200OK;

            await result.ExecuteAsync(HttpContext);

            HttpContext.Response.Body.Position = 0;
            if (HttpContext.Response.Body.Length == 0)
            {
                return new ApiResult<T>(HttpContext.Response.StatusCode, default);
            }

            var body = await JsonSerializer.DeserializeAsync<T>(
                HttpContext.Response.Body,
                new JsonSerializerOptions(JsonSerializerDefaults.Web)
            );
            return new ApiResult<T>(HttpContext.Response.StatusCode, body);
        }

        public async ValueTask DisposeAsync()
        {
            await Context.DisposeAsync();
            await _connection.DisposeAsync();
            await _serviceProvider.DisposeAsync();
        }
    }

    private sealed class SimpleCurrentUserContext : ICurrentUserContext
    {
        public string? UserId { get; set; }
        public bool IsAuthenticated => UserId is not null;
        public string GetRequiredUserId() =>
            UserId ?? throw new InvalidOperationException("No user set in test context.");
    }

    private sealed record ApiResult<T>(int StatusCode, T? Body);
}
