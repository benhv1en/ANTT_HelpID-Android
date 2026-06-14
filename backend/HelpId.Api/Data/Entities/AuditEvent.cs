namespace HelpId.Api.Data.Entities;

public sealed class AuditEvent
{
    public string Id { get; set; } = string.Empty;
    public string? UserId { get; set; }
    public string EventType { get; set; } = string.Empty;
    public string? ReasonCode { get; set; }
    public bool Success { get; set; }
    public DateTimeOffset CreatedAtUtc { get; set; }
    public string? IpHash { get; set; }
    public string? UserAgentHash { get; set; }

    public User? User { get; set; }
}
