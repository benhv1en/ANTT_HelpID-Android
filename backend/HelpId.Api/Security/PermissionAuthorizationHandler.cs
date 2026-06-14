using Microsoft.AspNetCore.Authorization;

namespace HelpId.Api.Security;

public sealed class PermissionAuthorizationHandler : AuthorizationHandler<PermissionRequirement>
{
    protected override Task HandleRequirementAsync(
        AuthorizationHandlerContext context,
        PermissionRequirement requirement
    )
    {
        var isAuthenticated = context.User.Identity?.IsAuthenticated == true;
        if (!isAuthenticated)
        {
            return Task.CompletedTask;
        }

        var hasPermission = context.User.Claims.Any(claim =>
            claim.Type == HelpIdAuthorizationDefaults.PermissionClaimType &&
            string.Equals(claim.Value, requirement.PermissionCode, StringComparison.Ordinal)
        );

        if (hasPermission)
        {
            context.Succeed(requirement);
        }

        return Task.CompletedTask;
    }
}
