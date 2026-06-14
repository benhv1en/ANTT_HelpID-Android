using HelpId.Api.Security;

namespace HelpId.Api.Profiles;

public static class ProfileEndpoints
{
    public static RouteGroupBuilder MapProfileEndpoints(this IEndpointRouteBuilder endpoints)
    {
        var group = endpoints.MapGroup("/api/v1/profile").WithTags("Profile");

        group.MapGet("/", GetProfileAsync)
            .RequireAuthorization(HelpIdAuthorizationPolicies.ProfileReadSelf);

        group.MapPut("/", UpdateProfileAsync)
            .RequireAuthorization(HelpIdAuthorizationPolicies.ProfileWriteSelf);

        return group;
    }

    public static async Task<IResult> GetProfileAsync(
        ICurrentUserContext currentUserContext,
        IProfileService profileService,
        HttpContext httpContext,
        CancellationToken cancellationToken
    )
    {
        AddSensitiveResponseHeaders(httpContext);
        var userId = currentUserContext.GetRequiredUserId();
        var result = await profileService.GetProfileAsync(userId, cancellationToken);
        return result.Status switch
        {
            ProfileOperationStatus.Ok when result.Value is not null =>
                Results.Ok(new { profile = result.Value }),
            ProfileOperationStatus.NotFound =>
                Results.Problem(
                    title: "Profile not found.",
                    statusCode: StatusCodes.Status404NotFound
                ),
            _ => Results.Problem(
                title: "Request failed.",
                statusCode: StatusCodes.Status400BadRequest
            )
        };
    }

    public static async Task<IResult> UpdateProfileAsync(
        UpdateProfileRequest request,
        ICurrentUserContext currentUserContext,
        IProfileService profileService,
        HttpContext httpContext,
        CancellationToken cancellationToken
    )
    {
        AddSensitiveResponseHeaders(httpContext);
        var userId = currentUserContext.GetRequiredUserId();
        var result = await profileService.UpdateProfileAsync(userId, request, cancellationToken);
        return result.Status switch
        {
            ProfileOperationStatus.Ok when result.Value is not null =>
                Results.Ok(new { profile = result.Value }),
            ProfileOperationStatus.ValidationFailed =>
                Results.ValidationProblem(
                    result.ValidationErrors?.ToDictionary(
                        pair => pair.Key, pair => pair.Value, StringComparer.Ordinal
                    ) ?? new Dictionary<string, string[]>(StringComparer.Ordinal),
                    statusCode: StatusCodes.Status422UnprocessableEntity
                ),
            ProfileOperationStatus.NotFound =>
                Results.Problem(
                    title: "Profile not found.",
                    statusCode: StatusCodes.Status404NotFound
                ),
            _ => Results.Problem(
                title: "Request failed.",
                statusCode: StatusCodes.Status400BadRequest
            )
        };
    }

    private static void AddSensitiveResponseHeaders(HttpContext httpContext)
    {
        httpContext.Response.Headers.CacheControl = "no-store";
        httpContext.Response.Headers.Append("Referrer-Policy", "no-referrer");
    }
}
