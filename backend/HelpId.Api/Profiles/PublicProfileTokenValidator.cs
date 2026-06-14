namespace HelpId.Api.Profiles;

public interface IPublicProfileTokenValidator
{
    ValueTask<PublicProfileTokenValidationResult> ValidateAsync(
        string publicKey,
        string publicProfileJwt,
        CancellationToken cancellationToken = default
    );
}

public readonly record struct PublicProfileTokenValidationResult(
    bool IsValid,
    DateTimeOffset IssuedAtUtc,
    DateTimeOffset ExpiresAtUtc
)
{
    public static PublicProfileTokenValidationResult Valid(
        DateTimeOffset issuedAtUtc,
        DateTimeOffset expiresAtUtc
    ) => new(true, issuedAtUtc, expiresAtUtc);

    public static PublicProfileTokenValidationResult Invalid() => new(false, default, default);
}

public sealed class RejectingPublicProfileTokenValidator : IPublicProfileTokenValidator
{
    public ValueTask<PublicProfileTokenValidationResult> ValidateAsync(
        string publicKey,
        string publicProfileJwt,
        CancellationToken cancellationToken = default
    )
    {
        return ValueTask.FromResult(PublicProfileTokenValidationResult.Invalid());
    }
}
