namespace HelpId.Api.Auth;

public sealed class AuthOptions
{
    public string Issuer { get; set; } = "HelpId.Api";
    public string Audience { get; set; } = "HelpId.Android";
    public int AccessTokenMinutes { get; set; } = 15;
    public int RefreshTokenDays { get; set; } = 30;
    public string? SigningKey { get; set; }
    public string SigningKeyEnvironmentVariable { get; set; } = "HELPID_AUTH_JWT_SIGNING_KEY";
    public int LockoutFailedAttempts { get; set; } = 5;
    public int LockoutMinutes { get; set; } = 15;
    public int PasswordMinLength { get; set; } = 12;
    public int PasswordMaxLength { get; set; } = 128;

    public TimeSpan AccessTokenLifetime => TimeSpan.FromMinutes(AccessTokenMinutes);
    public TimeSpan RefreshTokenLifetime => TimeSpan.FromDays(RefreshTokenDays);
    public TimeSpan LockoutDuration => TimeSpan.FromMinutes(LockoutMinutes);
}
