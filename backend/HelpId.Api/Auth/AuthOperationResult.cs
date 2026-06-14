namespace HelpId.Api.Auth;

public enum AuthOperationStatus
{
    Ok,
    Created,
    ValidationFailed,
    DuplicateEmail,
    InvalidCredentials,
    LockedOut,
    InvalidRefreshToken,
    Unauthorized
}

public sealed record AuthOperationResult<T>(
    AuthOperationStatus Status,
    T? Value,
    IReadOnlyDictionary<string, string[]>? ValidationErrors = null,
    DateTimeOffset? LockoutUntilUtc = null
)
{
    public static AuthOperationResult<T> Ok(T value) => new(AuthOperationStatus.Ok, value);
    public static AuthOperationResult<T> Created(T value) => new(AuthOperationStatus.Created, value);

    public static AuthOperationResult<T> ValidationFailed(
        IReadOnlyDictionary<string, string[]> errors
    ) => new(AuthOperationStatus.ValidationFailed, default, errors);

    public static AuthOperationResult<T> DuplicateEmail() =>
        new(AuthOperationStatus.DuplicateEmail, default);

    public static AuthOperationResult<T> InvalidCredentials() =>
        new(AuthOperationStatus.InvalidCredentials, default);

    public static AuthOperationResult<T> LockedOut(DateTimeOffset lockoutUntilUtc) =>
        new(AuthOperationStatus.LockedOut, default, LockoutUntilUtc: lockoutUntilUtc);

    public static AuthOperationResult<T> InvalidRefreshToken() =>
        new(AuthOperationStatus.InvalidRefreshToken, default);

    public static AuthOperationResult<T> Unauthorized() =>
        new(AuthOperationStatus.Unauthorized, default);
}

public sealed record AuthOperationResult(
    AuthOperationStatus Status,
    IReadOnlyDictionary<string, string[]>? ValidationErrors = null
)
{
    public static AuthOperationResult Ok() => new(AuthOperationStatus.Ok);

    public static AuthOperationResult ValidationFailed(
        IReadOnlyDictionary<string, string[]> errors
    ) => new(AuthOperationStatus.ValidationFailed, errors);
}
