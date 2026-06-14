using System.Security.Cryptography;

namespace HelpId.Api.Auth;

public interface IPasswordHasher
{
    string HashPassword(string password);
    bool VerifyPassword(string password, string passwordHash);
}

public sealed class Pbkdf2PasswordHasher : IPasswordHasher
{
    private const int SaltLength = 16;
    private const int HashLength = 32;
    private const int IterationCount = 210_000;
    private const string Algorithm = "PBKDF2-SHA256";

    public string HashPassword(string password)
    {
        var salt = RandomNumberGenerator.GetBytes(SaltLength);
        var hash = Rfc2898DeriveBytes.Pbkdf2(
            password,
            salt,
            IterationCount,
            HashAlgorithmName.SHA256,
            HashLength
        );

        return string.Join(
            '$',
            Algorithm,
            IterationCount.ToString(System.Globalization.CultureInfo.InvariantCulture),
            Base64Url.Encode(salt),
            Base64Url.Encode(hash)
        );
    }

    public bool VerifyPassword(string password, string passwordHash)
    {
        var parts = passwordHash.Split('$');
        if (parts.Length != 4 || !string.Equals(parts[0], Algorithm, StringComparison.Ordinal))
        {
            return false;
        }

        if (!int.TryParse(parts[1], out var iterationCount) || iterationCount <= 0)
        {
            return false;
        }

        try
        {
            var salt = Base64Url.Decode(parts[2]);
            var expectedHash = Base64Url.Decode(parts[3]);
            var actualHash = Rfc2898DeriveBytes.Pbkdf2(
                password,
                salt,
                iterationCount,
                HashAlgorithmName.SHA256,
                expectedHash.Length
            );

            return CryptographicOperations.FixedTimeEquals(actualHash, expectedHash);
        }
        catch (FormatException)
        {
            return false;
        }
    }
}
