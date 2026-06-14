using System.Security.Cryptography;
using System.Text;
using System.Text.Json;
using HelpId.Api.Auth;
using Microsoft.Extensions.Options;

namespace HelpId.Api.Profiles;

public interface IPublicProfileJwtService
{
    PublicProfileTokenIssueResult IssueToken(string publicKey);
}

public sealed record PublicProfileTokenIssueResult(
    string Token,
    DateTimeOffset ExpiresAtUtc,
    int ExpiresInSeconds
);

public sealed class PublicProfileJwtService(IOptions<ProfileJwtOptions> options)
    : IPublicProfileJwtService, IPublicProfileTokenValidator
{
    private readonly ProfileJwtOptions _options = options.Value;

    public PublicProfileTokenIssueResult IssueToken(string publicKey)
    {
        var now = DateTimeOffset.UtcNow;
        var expiresAtUtc = now.Add(_options.TokenLifetime);

        var payload = new Dictionary<string, object>
        {
            ["k"] = publicKey,
            ["typ"] = _options.TokenType,
            ["iat"] = now.ToUnixTimeSeconds(),
            ["exp"] = expiresAtUtc.ToUnixTimeSeconds(),
            ["jti"] = Base64Url.Encode(RandomNumberGenerator.GetBytes(16))
        };

        var header = new Dictionary<string, string>
        {
            ["alg"] = "HS256",
            ["typ"] = "JWT"
        };

        var encodedHeader = Base64Url.Encode(JsonSerializer.SerializeToUtf8Bytes(header));
        var encodedPayload = Base64Url.Encode(JsonSerializer.SerializeToUtf8Bytes(payload));
        var unsignedToken = $"{encodedHeader}.{encodedPayload}";

        return new PublicProfileTokenIssueResult(
            $"{unsignedToken}.{Sign(unsignedToken)}",
            expiresAtUtc,
            _options.ExpiresInSeconds
        );
    }

    public ValueTask<PublicProfileTokenValidationResult> ValidateAsync(
        string publicKey,
        string publicProfileJwt,
        CancellationToken cancellationToken = default
    )
    {
        return ValueTask.FromResult(ValidateToken(publicKey, publicProfileJwt));
    }

    private PublicProfileTokenValidationResult ValidateToken(string publicKey, string token)
    {
        var parts = token.Split('.');
        if (parts.Length != 3)
        {
            return PublicProfileTokenValidationResult.Invalid();
        }

        var unsignedToken = $"{parts[0]}.{parts[1]}";
        var expectedSignature = Sign(unsignedToken);
        if (!CryptographicOperations.FixedTimeEquals(
                Encoding.ASCII.GetBytes(expectedSignature),
                Encoding.ASCII.GetBytes(parts[2])
            ))
        {
            return PublicProfileTokenValidationResult.Invalid();
        }

        try
        {
            using var headerDoc = JsonDocument.Parse(Base64Url.Decode(parts[0]));
            if (!headerDoc.RootElement.TryGetProperty("alg", out var alg) ||
                !string.Equals(alg.GetString(), "HS256", StringComparison.Ordinal))
            {
                return PublicProfileTokenValidationResult.Invalid();
            }

            using var payloadDoc = JsonDocument.Parse(Base64Url.Decode(parts[1]));
            var payload = payloadDoc.RootElement;

            if (!payload.TryGetProperty("k", out var keyProp) ||
                !string.Equals(keyProp.GetString(), publicKey, StringComparison.Ordinal))
            {
                return PublicProfileTokenValidationResult.Invalid();
            }

            if (!payload.TryGetProperty("typ", out var typProp) ||
                !string.Equals(typProp.GetString(), _options.TokenType, StringComparison.Ordinal))
            {
                return PublicProfileTokenValidationResult.Invalid();
            }

            if (!payload.TryGetProperty("iat", out var iatProp) ||
                !payload.TryGetProperty("exp", out var expProp))
            {
                return PublicProfileTokenValidationResult.Invalid();
            }

            var issuedAt = DateTimeOffset.FromUnixTimeSeconds(iatProp.GetInt64());
            var expiresAt = DateTimeOffset.FromUnixTimeSeconds(expProp.GetInt64());

            return PublicProfileTokenValidationResult.Valid(issuedAt, expiresAt);
        }
        catch (JsonException)
        {
            return PublicProfileTokenValidationResult.Invalid();
        }
        catch (FormatException)
        {
            return PublicProfileTokenValidationResult.Invalid();
        }
    }

    private string Sign(string unsignedToken)
    {
        var key = ResolveSigningKey();
        using var hmac = new HMACSHA256(Encoding.UTF8.GetBytes(key));
        return Base64Url.Encode(hmac.ComputeHash(Encoding.ASCII.GetBytes(unsignedToken)));
    }

    private string ResolveSigningKey()
    {
        var configKey = _options.SigningKey;
        var envKey = Environment.GetEnvironmentVariable(_options.SigningKeyEnvironmentVariable);
        var key = string.IsNullOrWhiteSpace(configKey) ? envKey : configKey;

        if (string.IsNullOrWhiteSpace(key) || Encoding.UTF8.GetByteCount(key) < 32)
        {
            throw new InvalidOperationException(
                $"Profile JWT signing key is missing or too short. Set {_options.SigningKeyEnvironmentVariable}."
            );
        }

        return key;
    }
}
