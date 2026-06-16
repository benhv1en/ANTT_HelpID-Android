using HelpId.Api.Security;

namespace HelpId.Api.Admin;

public static class AdminEndpoints
{
    public static RouteGroupBuilder MapAdminEndpoints(this IEndpointRouteBuilder endpoints)
    {
        var group = endpoints
            .MapGroup("/api/v1/admin")
            .WithTags("Admin")
            .RequireAuthorization(HelpIdAuthorizationPolicies.AdminMetadata);

        group.MapGet("/stats", GetStatsAsync);
        group.MapGet("/users", GetUsersAsync);
        group.MapPost("/users/{userId}/roles/{roleId}", AssignRoleAsync);
        group.MapDelete("/users/{userId}/roles/{roleId}", RevokeRoleAsync);

        return group;
    }

    public static async Task<IResult> GetStatsAsync(
        IAdminService adminService,
        CancellationToken cancellationToken
    )
    {
        var stats = await adminService.GetStatsAsync(cancellationToken);
        return Results.Ok(stats);
    }

    public static async Task<IResult> GetUsersAsync(
        IAdminService adminService,
        int page = 1,
        int size = 20,
        CancellationToken cancellationToken = default
    )
    {
        if (page < 1)
        {
            return Results.Problem(
                title: "page must be >= 1.",
                statusCode: StatusCodes.Status400BadRequest
            );
        }

        if (size < 1 || size > 100)
        {
            return Results.Problem(
                title: "size must be between 1 and 100.",
                statusCode: StatusCodes.Status400BadRequest
            );
        }

        var result = await adminService.GetUsersAsync(page, size, cancellationToken);
        return Results.Ok(result);
    }

    public static async Task<IResult> AssignRoleAsync(
        string userId,
        string roleId,
        ICurrentUserContext currentUserContext,
        IAdminService adminService,
        CancellationToken cancellationToken
    )
    {
        var callerUserId = currentUserContext.GetRequiredUserId();
        var result = await adminService.AssignRoleAsync(
            callerUserId, userId, roleId, cancellationToken
        );
        return ToOperationResult(result);
    }

    public static async Task<IResult> RevokeRoleAsync(
        string userId,
        string roleId,
        ICurrentUserContext currentUserContext,
        IAdminService adminService,
        CancellationToken cancellationToken
    )
    {
        var callerUserId = currentUserContext.GetRequiredUserId();
        var result = await adminService.RevokeRoleAsync(
            callerUserId, userId, roleId, cancellationToken
        );
        return ToOperationResult(result);
    }

    private static IResult ToOperationResult(AdminOperationResult result) =>
        result.Status switch
        {
            AdminOperationStatus.Ok => Results.NoContent(),
            AdminOperationStatus.NotFound => Results.Problem(
                title: result.ErrorMessage ?? "Not found.",
                statusCode: StatusCodes.Status404NotFound
            ),
            AdminOperationStatus.BadRequest => Results.Problem(
                title: result.ErrorMessage ?? "Bad request.",
                statusCode: StatusCodes.Status400BadRequest
            ),
            AdminOperationStatus.Forbidden => Results.Problem(
                title: result.ErrorMessage ?? "Forbidden.",
                statusCode: StatusCodes.Status403Forbidden
            ),
            _ => Results.Problem(
                title: "Request failed.",
                statusCode: StatusCodes.Status400BadRequest
            )
        };
}
