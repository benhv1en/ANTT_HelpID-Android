namespace HelpId.Api.EmergencyLinks;

public sealed record MintRequest(string? PublicKey);

public sealed record MintResponse(
    string PublicKey,
    int ExpiresInSeconds,
    string Token,
    string Url
);
