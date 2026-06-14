namespace HelpId.Api.Data.Entities;

public sealed class Permission
{
    public string Id { get; set; } = string.Empty;
    public string Code { get; set; } = string.Empty;
    public string? Description { get; set; }
    public DateTimeOffset CreatedAtUtc { get; set; }

    public ICollection<RolePermission> RolePermissions { get; } = new List<RolePermission>();
}
