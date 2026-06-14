using System.Security.Claims;
using Microsoft.AspNetCore.Http;

namespace HelpId.Api.Security;

public interface ICurrentUserContext
{
    bool IsAuthenticated { get; }
    string GetRequiredUserId();
}

public sealed class CurrentUserContext(IHttpContextAccessor httpContextAccessor) : ICurrentUserContext
{
    public bool IsAuthenticated =>
        httpContextAccessor.HttpContext?.User.Identity?.IsAuthenticated == true;

    public string GetRequiredUserId()
    {
        var user = httpContextAccessor.HttpContext?.User;
        var userId = user?.FindFirstValue(ClaimTypes.NameIdentifier)
            ?? user?.FindFirstValue(HelpIdAuthorizationDefaults.SubjectClaimType);

        if (string.IsNullOrWhiteSpace(userId))
        {
            throw new InvalidOperationException("Authenticated request does not contain a JWT subject.");
        }

        return userId;
    }
}
