namespace HelpId.Api.Profiles;

public sealed class ProfileJwtOptions
{
    public string? SigningKey { get; set; }
    public string SigningKeyEnvironmentVariable { get; set; } = "HELPID_PROFILE_JWT_SIGNING_KEY";
    public int ExpiresInHours { get; set; } = 3;
    public string TokenType { get; set; } = "public_profile";

    public TimeSpan TokenLifetime => TimeSpan.FromHours(ExpiresInHours);
    public int ExpiresInSeconds => ExpiresInHours * 3600;
}
