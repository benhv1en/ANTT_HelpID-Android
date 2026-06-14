namespace HelpId.Api.Security;

public static class HelpIdAuthorizationDefaults
{
    public const string PermissionClaimType = "permission";
    public const string SubjectClaimType = "sub";
    public static readonly TimeSpan PublicProfileTokenMaxLifetime = TimeSpan.FromHours(3);

    public static readonly DateTimeOffset SeedCreatedAtUtc =
        new(2026, 1, 1, 0, 0, 0, TimeSpan.Zero);

    public static class Roles
    {
        public const string UserId = "role_user";
        public const string UserName = "User";
        public const string UserNormalizedName = "USER";

        public const string AdminId = "role_admin";
        public const string AdminName = "Admin";
        public const string AdminNormalizedName = "ADMIN";
    }

    public static class Permissions
    {
        public const string ProfileReadSelfId = "permission_profile_read_self";
        public const string ProfileReadSelf = "profile:read:self";

        public const string ProfileWriteSelfId = "permission_profile_write_self";
        public const string ProfileWriteSelf = "profile:write:self";

        public const string EmergencyLinkMintSelfId = "permission_emergency_link_mint_self";
        public const string EmergencyLinkMintSelf = "emergency_link:mint:self";

        public const string AuthSessionSelfId = "permission_auth_session_self";
        public const string AuthSessionSelf = "auth:session:self";

        public const string AdminMetadataReadId = "permission_admin_metadata_read";
        public const string AdminMetadataRead = "admin:metadata:read";
    }
}
