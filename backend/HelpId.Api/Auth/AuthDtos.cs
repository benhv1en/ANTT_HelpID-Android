namespace HelpId.Api.Auth;

public sealed record RegisterRequest(
    string? Email,
    string? Password,
    string? DisplayName,
    string? DeviceName
);

public sealed record LoginRequest(
    string? Email,
    string? Password,
    string? DeviceName
);

public sealed record RefreshRequest(string? RefreshToken, string? DeviceName);

public sealed record LogoutRequest(string? RefreshToken);

public sealed record AuthResponse(
    string AccessToken,
    DateTimeOffset AccessTokenExpiresAtUtc,
    string RefreshToken,
    DateTimeOffset RefreshTokenExpiresAtUtc,
    AuthUserResponse User
);

public sealed record AuthUserResponse(
    string Id,
    string Email,
    string? DisplayName,
    bool IsEmailVerified,
    IReadOnlyList<string> Roles,
    IReadOnlyList<string> Permissions
);

public sealed record ClientRequestInfo(
    string? DeviceName,
    string? UserAgentHash,
    string? IpHash
);
