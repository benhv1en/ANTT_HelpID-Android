namespace HelpId.Api.Auth;

public static class Base64Url
{
    public static string Encode(byte[] bytes)
    {
        return Convert.ToBase64String(bytes)
            .TrimEnd('=')
            .Replace('+', '-')
            .Replace('/', '_');
    }

    public static byte[] Decode(string value)
    {
        var padded = value.Replace('-', '+').Replace('_', '/');
        var padding = padded.Length % 4;
        if (padding > 0)
        {
            padded = padded.PadRight(padded.Length + 4 - padding, '=');
        }

        return Convert.FromBase64String(padded);
    }
}
