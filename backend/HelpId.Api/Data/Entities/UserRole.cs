namespace HelpId.Api.Data.Entities;

public sealed class UserRole
{
    public string UserId { get; set; } = string.Empty;
    public string RoleId { get; set; } = string.Empty;
    public DateTimeOffset AssignedAtUtc { get; set; }

    public User User { get; set; } = null!;
    public Role Role { get; set; } = null!;
}
