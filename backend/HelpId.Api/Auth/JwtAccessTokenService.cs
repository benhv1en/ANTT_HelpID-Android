using System.Globalization;
using System.Security.Claims;
using System.Security.Cryptography;
using System.Text;
using System.Text.Json;
using HelpId.Api.Data.Entities;
using HelpId.Api.Security;
using Microsoft.Extensions.Options;

namespace HelpId.Api.Auth;

public interface IJwtAccessTokenService
{
    AccessTokenIssueResult IssueAccessToken(
        User user,
        IReadOnlyList<string> roles,
        IReadOnlyList<string> permissions
    );

    ClaimsPrincipal? ValidateAccessToken(string accessToken);
}

public sealed record AccessTokenIssueResult(string Token, DateTimeOffset ExpiresAtUtc);

public sealed class JwtAccessTokenService(IOptions<AuthOptions> options) : IJwtAccessTokenService
{
    private readonly AuthOptions _options = options.Value;

    public AccessTokenIssueResult IssueAccessToken(
        User user,
        IReadOnlyList<string> roles,
        IReadOnlyList<string> permissions
    )
    {
        var now = DateTimeOffset.UtcNow;
        var expiresAtUtc = now.Add(_options.AccessTokenLifetime);
        var payload = new Dictionary<string, object?>
        {
            ["iss"] = _options.Issuer,
            ["aud"] = _options.Audience,
            ["sub"] = user.Id,
            ["email"] = user.Email,
            ["iat"] = now.ToUnixTimeSeconds(),
            ["nbf"] = now.ToUnixTimeSeconds(),
            ["exp"] = expiresAtUtc.ToUnixTimeSeconds(),
            ["jti"] = Guid.NewGuid().ToString("N", CultureInfo.InvariantCulture),
            ["role"] = roles,
            [HelpIdAuthorizationDefaults.PermissionClaimType] = permissions
        };

        var header = new Dictionary<string, string>
        {
            ["alg"] = "HS256",
            ["typ"] = "JWT"
        };

        var encodedHeader = Base64Url.Encode(JsonSerializer.SerializeToUtf8Bytes(header));
        var encodedPayload = Base64Url.Encode(JsonSerializer.SerializeToUtf8Bytes(payload));
        var unsignedToken = $"{encodedHeader}.{encodedPayload}";
        var signature = Sign(unsignedToken);

        return new AccessTokenIssueResult($"{unsignedToken}.{signature}", expiresAtUtc);
    }

    public ClaimsPrincipal? ValidateAccessToken(string accessToken)
    {
        var tokenParts = accessToken.Split('.');
        if (tokenParts.Length != 3)
        {
            return null;
        }

        var unsignedToken = $"{tokenParts[0]}.{tokenParts[1]}";
        var expectedSignature = Sign(unsignedToken);
        if (!CryptographicOperations.FixedTimeEquals(
                Encoding.ASCII.GetBytes(expectedSignature),
                Encoding.ASCII.GetBytes(tokenParts[2])
            ))
        {
            return null;
        }

        try
        {
            using var headerDocument = JsonDocument.Parse(Base64Url.Decode(tokenParts[0]));
            if (
                !headerDocument.RootElement.TryGetProperty("alg", out var alg) ||
                !string.Equals(alg.GetString(), "HS256", StringComparison.Ordinal)
            )
            {
                return null;
            }

            using var payloadDocument = JsonDocument.Parse(Base64Url.Decode(tokenParts[1]));
            var payload = payloadDocument.RootElement;
            if (!StringPropertyEquals(payload, "iss", _options.Issuer))
            {
                return null;
            }

            if (!StringPropertyEquals(payload, "aud", _options.Audience))
            {
                return null;
            }

            if (!payload.TryGetProperty("sub", out var subject) || string.IsNullOrWhiteSpace(subject.GetString()))
            {
                return null;
            }

            var now = DateTimeOffset.UtcNow.ToUnixTimeSeconds();
            var clockSkewSeconds = TimeSpan.FromMinutes(1).TotalSeconds;
            if (!payload.TryGetProperty("exp", out var exp) || exp.GetInt64() <= now - clockSkewSeconds)
            {
                return null;
            }

            if (payload.TryGetProperty("nbf", out var nbf) && nbf.GetInt64() > now + clockSkewSeconds)
            {
                return null;
            }

            var claims = new List<Claim>
            {
                new(HelpIdAuthorizationDefaults.SubjectClaimType, subject.GetString()!),
                new(ClaimTypes.NameIdentifier, subject.GetString()!)
            };

            if (payload.TryGetProperty("email", out var email) && !string.IsNullOrWhiteSpace(email.GetString()))
            {
                claims.Add(new Claim(ClaimTypes.Email, email.GetString()!));
            }

            AddClaimsFromJsonArray(payload, "role", ClaimTypes.Role, claims);
            AddClaimsFromJsonArray(
                payload,
                HelpIdAuthorizationDefaults.PermissionClaimType,
                HelpIdAuthorizationDefaults.PermissionClaimType,
                claims
            );

            return new ClaimsPrincipal(new ClaimsIdentity(claims, JwtAuthenticationDefaults.Scheme));
        }
        catch (JsonException)
        {
            return null;
        }
        catch (FormatException)
        {
            return null;
        }
    }

    private static bool StringPropertyEquals(JsonElement element, string propertyName, string expected)
    {
        return element.TryGetProperty(propertyName, out var property) &&
            string.Equals(property.GetString(), expected, StringComparison.Ordinal);
    }

    private static void AddClaimsFromJsonArray(
        JsonElement payload,
        string propertyName,
        string claimType,
        ICollection<Claim> claims
    )
    {
        if (!payload.TryGetProperty(propertyName, out var property))
        {
            return;
        }

        if (property.ValueKind == JsonValueKind.String)
        {
            var value = property.GetString();
            if (!string.IsNullOrWhiteSpace(value))
            {
                claims.Add(new Claim(claimType, value));
            }

            return;
        }

        if (property.ValueKind != JsonValueKind.Array)
        {
            return;
        }

        foreach (var item in property.EnumerateArray())
        {
            var value = item.GetString();
            if (!string.IsNullOrWhiteSpace(value))
            {
                claims.Add(new Claim(claimType, value));
            }
        }
    }

    private string Sign(string unsignedToken)
    {
        var signingKey = ResolveSigningKey();
        using var hmac = new HMACSHA256(Encoding.UTF8.GetBytes(signingKey));
        return Base64Url.Encode(hmac.ComputeHash(Encoding.ASCII.GetBytes(unsignedToken)));
    }

    private string ResolveSigningKey()
    {
        var configuredKey = _options.SigningKey;
        var envKey = Environment.GetEnvironmentVariable(_options.SigningKeyEnvironmentVariable);
        var signingKey = string.IsNullOrWhiteSpace(configuredKey) ? envKey : configuredKey;

        if (string.IsNullOrWhiteSpace(signingKey) || Encoding.UTF8.GetByteCount(signingKey) < 32)
        {
            throw new InvalidOperationException(
                $"JWT signing key is missing or too short. Set {_options.SigningKeyEnvironmentVariable}."
            );
        }

        return signingKey;
    }
}

public static class JwtAuthenticationDefaults
{
    public const string Scheme = "HelpIdJwt";
}
