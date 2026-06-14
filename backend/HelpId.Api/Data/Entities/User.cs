namespace HelpId.Api.Data.Entities;

public sealed class User
{
    public string Id { get; set; } = string.Empty;
    public string Email { get; set; } = string.Empty;
    public string NormalizedEmail { get; set; } = string.Empty;
    public string PasswordHash { get; set; } = string.Empty;
    public string? DisplayName { get; set; }
    public string? PhoneNumber { get; set; }
    public bool IsEmailVerified { get; set; }
    public int FailedLoginCount { get; set; }
    public DateTimeOffset? LockoutUntilUtc { get; set; }
    public string SecurityStamp { get; set; } = string.Empty;
    public DateTimeOffset CreatedAtUtc { get; set; }
    public DateTimeOffset UpdatedAtUtc { get; set; }
    public DateTimeOffset? LastLoginAtUtc { get; set; }
    public DateTimeOffset? DeletedAtUtc { get; set; }

    public UserProfile? Profile { get; set; }
    public ICollection<RefreshToken> RefreshTokens { get; } = new List<RefreshToken>();
    public ICollection<ProfileAllergy> Allergies { get; } = new List<ProfileAllergy>();
    public ICollection<MedicalNote> MedicalNotes { get; } = new List<MedicalNote>();
    public ICollection<EmergencyContact> EmergencyContacts { get; } = new List<EmergencyContact>();
    public ICollection<PublicProfileLink> PublicProfileLinks { get; } = new List<PublicProfileLink>();
    public ICollection<AuditEvent> AuditEvents { get; } = new List<AuditEvent>();
    public ICollection<UserRole> UserRoles { get; } = new List<UserRole>();
}
