using System.Text.RegularExpressions;
using HelpId.Api.Profiles;
using HelpId.Api.Security;

namespace HelpId.Api.EmergencyLinks;

public static class EmergencyLinkEndpoints
{
    public static void MapEmergencyLinkEndpoints(this IEndpointRouteBuilder endpoints)
    {
        endpoints
            .MapPost("/api/v1/emergency-links/mint", MintAsync)
            .RequireAuthorization(HelpIdAuthorizationPolicies.EmergencyLinkMintSelf)
            .WithTags("EmergencyLinks");

        endpoints
            .MapGet("/api/v1/public/profile", GetPublicProfileAsync)
            .WithTags("EmergencyLinks");
    }

    public static async Task<IResult> MintAsync(
        MintRequest request,
        ICurrentUserContext currentUserContext,
        IEmergencyLinkService emergencyLinkService,
        HttpContext httpContext,
        CancellationToken cancellationToken
    )
    {
        httpContext.Response.Headers.CacheControl = "no-store";
        httpContext.Response.Headers.Append("Referrer-Policy", "no-referrer");

        var userId = currentUserContext.GetRequiredUserId();
        var result = await emergencyLinkService.MintAsync(userId, request.PublicKey, cancellationToken);
        return result.Status switch
        {
            MintStatus.Ok when result.Response is not null => Results.Ok(result.Response),
            MintStatus.ValidationFailed => Results.Problem(
                title: "Public key format is invalid.",
                statusCode: StatusCodes.Status400BadRequest
            ),
            MintStatus.Conflict => Results.Problem(
                title: "Public key is already assigned to another user.",
                statusCode: StatusCodes.Status409Conflict
            ),
            MintStatus.KeyCollision => Results.Problem(
                title: "Could not generate a unique public key. Please try again.",
                statusCode: StatusCodes.Status503ServiceUnavailable
            ),
            _ => Results.Problem(
                title: "Request failed.",
                statusCode: StatusCodes.Status400BadRequest
            )
        };
    }

    public static async Task<IResult> GetPublicProfileAsync(
        string? key,
        string? t,
        IPublicProfileAccessService publicProfileAccessService,
        HttpContext httpContext,
        CancellationToken cancellationToken
    )
    {
        httpContext.Response.Headers.CacheControl = "no-store";
        httpContext.Response.Headers.Append("Referrer-Policy", "no-referrer");

        if (string.IsNullOrWhiteSpace(key) || string.IsNullOrWhiteSpace(t))
        {
            return Results.Problem(
                title: "Query parameters key and t are required.",
                statusCode: StatusCodes.Status400BadRequest
            );
        }

        if (!EmergencyLinkService.PublicKeyRegex.IsMatch(key))
        {
            return Results.Problem(
                title: "Public key format is invalid.",
                statusCode: StatusCodes.Status400BadRequest
            );
        }

        var result = await publicProfileAccessService.GetProfileAsync(key, t, cancellationToken);
        return result.Status switch
        {
            PublicProfileAccessStatus.Ok when result.Profile is not null =>
                Results.Ok(new { key, profile = result.Profile }),
            PublicProfileAccessStatus.NotFound =>
                Results.Problem(
                    title: "Public profile not found.",
                    statusCode: StatusCodes.Status404NotFound
                ),
            PublicProfileAccessStatus.Forbidden =>
                Results.Problem(
                    title: "Public profile token is invalid or expired.",
                    statusCode: StatusCodes.Status401Unauthorized
                ),
            _ => Results.Problem(
                title: "Request failed.",
                statusCode: StatusCodes.Status400BadRequest
            )
        };
    }
}
