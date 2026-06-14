using Microsoft.AspNetCore.Authorization;

namespace HelpId.Api.Security;

public sealed class PermissionRequirement(string permissionCode) : IAuthorizationRequirement
{
    public string PermissionCode { get; } = permissionCode;
}
