using System.Security.Cryptography;
using System.Text;

namespace HelpId.Api.Auth;

public interface ITokenHasher
{
    string HashToken(string token);
    string HashOptionalValue(string? value);
}

public sealed class Sha256TokenHasher : ITokenHasher
{
    public string HashToken(string token) => Hash(token);

    public string HashOptionalValue(string? value) =>
        string.IsNullOrWhiteSpace(value) ? string.Empty : Hash(value);

    private static string Hash(string value)
    {
        var bytes = SHA256.HashData(Encoding.UTF8.GetBytes(value));
        return Base64Url.Encode(bytes);
    }
}
