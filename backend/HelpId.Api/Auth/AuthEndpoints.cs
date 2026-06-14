using HelpId.Api.Security;
using Microsoft.AspNetCore.Http.HttpResults;

namespace HelpId.Api.Auth;

public static class AuthEndpoints
{
    public static RouteGroupBuilder MapAuthEndpoints(this IEndpointRouteBuilder endpoints)
    {
        var group = endpoints.MapGroup("/api/v1/auth").WithTags("Auth");

        group.MapPost("/register", RegisterAsync);
        group.MapPost("/login", LoginAsync);
        group.MapPost("/refresh", RefreshAsync);
        group.MapPost("/logout", LogoutAsync);
        group.MapGet("/me", MeAsync).RequireAuthorization();

        return group;
    }

    public static async Task<IResult> RegisterAsync(
        RegisterRequest request,
        HttpContext httpContext,
        IAuthService authService,
        ITokenHasher tokenHasher,
        CancellationToken cancellationToken
    )
    {
        var result = await authService.RegisterAsync(
            request,
            BuildClientRequestInfo(httpContext, request.DeviceName, tokenHasher),
            cancellationToken
        );

        return ToAuthResponseResult(result, created: true);
    }

    public static async Task<IResult> LoginAsync(
        LoginRequest request,
        HttpContext httpContext,
        IAuthService authService,
        ITokenHasher tokenHasher,
        CancellationToken cancellationToken
    )
    {
        var result = await authService.LoginAsync(
            request,
            BuildClientRequestInfo(httpContext, request.DeviceName, tokenHasher),
            cancellationToken
        );

        return ToAuthResponseResult(result, created: false);
    }

    public static async Task<IResult> RefreshAsync(
        RefreshRequest request,
        HttpContext httpContext,
        IAuthService authService,
        ITokenHasher tokenHasher,
        CancellationToken cancellationToken
    )
    {
        var result = await authService.RefreshAsync(
            request,
            BuildClientRequestInfo(httpContext, request.DeviceName, tokenHasher),
            cancellationToken
        );

        return ToAuthResponseResult(result, created: false);
    }

    public static async Task<IResult> LogoutAsync(
        LogoutRequest request,
        IAuthService authService,
        CancellationToken cancellationToken
    )
    {
        var result = await authService.LogoutAsync(request, cancellationToken);
        return result.Status switch
        {
            AuthOperationStatus.Ok => Results.NoContent(),
            AuthOperationStatus.ValidationFailed => ValidationProblem(result.ValidationErrors),
            _ => GenericProblem("Logout failed.", StatusCodes.Status400BadRequest)
        };
    }

    public static async Task<IResult> MeAsync(
        ICurrentUserContext currentUserContext,
        IAuthService authService,
        CancellationToken cancellationToken
    )
    {
        var userId = currentUserContext.GetRequiredUserId();
        var result = await authService.GetMeAsync(userId, cancellationToken);
        return result.Status switch
        {
            AuthOperationStatus.Ok when result.Value is not null => Results.Ok(result.Value),
            AuthOperationStatus.Unauthorized => GenericProblem(
                "Authentication is required.",
                StatusCodes.Status401Unauthorized
            ),
            _ => GenericProblem("Request failed.", StatusCodes.Status400BadRequest)
        };
    }

    private static IResult ToAuthResponseResult(
        AuthOperationResult<AuthResponse> result,
        bool created
    )
    {
        return result.Status switch
        {
            AuthOperationStatus.Ok when result.Value is not null => Results.Ok(result.Value),
            AuthOperationStatus.Created when result.Value is not null && created =>
                Results.Json(result.Value, statusCode: StatusCodes.Status201Created),
            AuthOperationStatus.ValidationFailed => ValidationProblem(result.ValidationErrors),
            AuthOperationStatus.DuplicateEmail => GenericProblem(
                "Email is already registered.",
                StatusCodes.Status409Conflict
            ),
            AuthOperationStatus.InvalidCredentials => GenericProblem(
                "Email or password is invalid.",
                StatusCodes.Status401Unauthorized
            ),
            AuthOperationStatus.LockedOut => Results.Json(
                new
                {
                    title = "Account is temporarily locked.",
                    status = StatusCodes.Status423Locked,
                    lockoutUntilUtc = result.LockoutUntilUtc
                },
                statusCode: StatusCodes.Status423Locked
            ),
            AuthOperationStatus.InvalidRefreshToken => GenericProblem(
                "Refresh token is invalid.",
                StatusCodes.Status401Unauthorized
            ),
            _ => GenericProblem("Request failed.", StatusCodes.Status400BadRequest)
        };
    }

    private static IResult ValidationProblem(IReadOnlyDictionary<string, string[]>? errors)
    {
        var validationErrors = (errors ?? EmptyValidationErrors)
            .ToDictionary(pair => pair.Key, pair => pair.Value, StringComparer.Ordinal);

        return Results.ValidationProblem(
            validationErrors,
            statusCode: StatusCodes.Status422UnprocessableEntity
        );
    }

    private static ClientRequestInfo BuildClientRequestInfo(
        HttpContext httpContext,
        string? deviceName,
        ITokenHasher tokenHasher
    )
    {
        var userAgent = httpContext.Request.Headers.UserAgent.ToString();
        var remoteIp = httpContext.Connection.RemoteIpAddress?.ToString();

        return new ClientRequestInfo(
            deviceName,
            string.IsNullOrWhiteSpace(userAgent) ? null : tokenHasher.HashOptionalValue(userAgent),
            string.IsNullOrWhiteSpace(remoteIp) ? null : tokenHasher.HashOptionalValue(remoteIp)
        );
    }

    private static IResult GenericProblem(string title, int statusCode)
    {
        return Results.Problem(title: title, statusCode: statusCode);
    }

    private static readonly IReadOnlyDictionary<string, string[]> EmptyValidationErrors =
        new Dictionary<string, string[]>(StringComparer.Ordinal);
}
