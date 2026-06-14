namespace HelpId.Api.Data.Entities;

public sealed class PublicProfileLink
{
    public string PublicKey { get; set; } = string.Empty;
    public string UserId { get; set; } = string.Empty;
    public DateTimeOffset CreatedAtUtc { get; set; }
    public DateTimeOffset UpdatedAtUtc { get; set; }
    public DateTimeOffset? LastMintedAtUtc { get; set; }
    public DateTimeOffset? RevokedAtUtc { get; set; }

    public User User { get; set; } = null!;
}
