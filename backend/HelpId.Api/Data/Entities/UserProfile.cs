namespace HelpId.Api.Data.Entities;

public sealed class UserProfile
{
    public string UserId { get; set; } = string.Empty;
    public string FullName { get; set; } = string.Empty;
    public string BloodGroup { get; set; } = string.Empty;
    public string Address { get; set; } = string.Empty;
    public string Language { get; set; } = "en";
    public DateTimeOffset CreatedAtUtc { get; set; }
    public DateTimeOffset UpdatedAtUtc { get; set; }
    public DateTimeOffset LastUpdatedUtc { get; set; }

    public User User { get; set; } = null!;
}
