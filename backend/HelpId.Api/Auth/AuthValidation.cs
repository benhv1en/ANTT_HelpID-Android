using System.Net.Mail;
using HelpId.Api.Data.Schema;
using Microsoft.Extensions.Options;

namespace HelpId.Api.Auth;

public interface IAuthRequestValidator
{
    IReadOnlyDictionary<string, string[]> ValidateRegister(RegisterRequest request);
    IReadOnlyDictionary<string, string[]> ValidateLogin(LoginRequest request);
    IReadOnlyDictionary<string, string[]> ValidateRefreshToken(string? refreshToken);
}

public sealed class AuthRequestValidator(IOptions<AuthOptions> options) : IAuthRequestValidator
{
    private readonly AuthOptions _options = options.Value;

    public IReadOnlyDictionary<string, string[]> ValidateRegister(RegisterRequest request)
    {
        var errors = new Dictionary<string, string[]>(StringComparer.Ordinal);
        AddEmailErrors(errors, request.Email);
        AddPasswordErrors(errors, request.Password);

        if (request.DisplayName is { Length: > SchemaConstants.DisplayNameMaxLength })
        {
            errors[nameof(request.DisplayName)] = new[]
            {
                $"DisplayName must be {SchemaConstants.DisplayNameMaxLength} characters or fewer."
            };
        }

        AddDeviceNameErrors(errors, request.DeviceName);
        return errors;
    }

    public IReadOnlyDictionary<string, string[]> ValidateLogin(LoginRequest request)
    {
        var errors = new Dictionary<string, string[]>(StringComparer.Ordinal);
        AddEmailErrors(errors, request.Email);

        if (string.IsNullOrWhiteSpace(request.Password))
        {
            errors[nameof(request.Password)] = new[] { "Password is required." };
        }

        AddDeviceNameErrors(errors, request.DeviceName);
        return errors;
    }

    public IReadOnlyDictionary<string, string[]> ValidateRefreshToken(string? refreshToken)
    {
        var errors = new Dictionary<string, string[]>(StringComparer.Ordinal);
        if (string.IsNullOrWhiteSpace(refreshToken))
        {
            errors[nameof(refreshToken)] = new[] { "Refresh token is required." };
        }

        return errors;
    }

    private static void AddEmailErrors(Dictionary<string, string[]> errors, string? email)
    {
        if (string.IsNullOrWhiteSpace(email))
        {
            errors[nameof(email)] = new[] { "Email is required." };
            return;
        }

        if (email.Length > SchemaConstants.EmailMaxLength)
        {
            errors[nameof(email)] = new[]
            {
                $"Email must be {SchemaConstants.EmailMaxLength} characters or fewer."
            };
            return;
        }

        try
        {
            _ = new MailAddress(email);
        }
        catch (FormatException)
        {
            errors[nameof(email)] = new[] { "Email format is invalid." };
        }
    }

    private void AddPasswordErrors(Dictionary<string, string[]> errors, string? password)
    {
        if (string.IsNullOrWhiteSpace(password))
        {
            errors[nameof(password)] = new[] { "Password is required." };
            return;
        }

        if (password.Length < _options.PasswordMinLength)
        {
            errors[nameof(password)] = new[]
            {
                $"Password must be at least {_options.PasswordMinLength} characters."
            };
            return;
        }

        if (password.Length > _options.PasswordMaxLength)
        {
            errors[nameof(password)] = new[]
            {
                $"Password must be {_options.PasswordMaxLength} characters or fewer."
            };
        }
    }

    private static void AddDeviceNameErrors(
        Dictionary<string, string[]> errors,
        string? deviceName
    )
    {
        if (deviceName is { Length: > SchemaConstants.DeviceNameMaxLength })
        {
            errors[nameof(deviceName)] = new[]
            {
                $"DeviceName must be {SchemaConstants.DeviceNameMaxLength} characters or fewer."
            };
        }
    }
}
