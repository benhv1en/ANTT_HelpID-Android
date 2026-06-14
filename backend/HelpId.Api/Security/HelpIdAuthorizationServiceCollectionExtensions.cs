using HelpId.Api.Profiles;
using Microsoft.AspNetCore.Authorization;
using Microsoft.Extensions.DependencyInjection;
using Microsoft.Extensions.DependencyInjection.Extensions;

namespace HelpId.Api.Security;

public static class HelpIdAuthorizationServiceCollectionExtensions
{
    public static IServiceCollection AddHelpIdAuthorization(this IServiceCollection services)
    {
        services.AddHttpContextAccessor();
        services.AddScoped<ICurrentUserContext, CurrentUserContext>();
        services.AddScoped<IOwnedResourceAuthorizationService, OwnedResourceAuthorizationService>();
        services.AddScoped<IPublicProfileAccessService, PublicProfileAccessService>();
        services.TryAddScoped<IPublicProfileTokenValidator, RejectingPublicProfileTokenValidator>();
        services.AddScoped<IAuthorizationHandler, PermissionAuthorizationHandler>();

        services.AddAuthorization(AddHelpIdPolicies);

        return services;
    }

    public static void AddHelpIdPolicies(AuthorizationOptions options)
    {
        options.AddPolicy(
            HelpIdAuthorizationPolicies.ProfileReadSelf,
            policy => policy
                .RequireAuthenticatedUser()
                .AddRequirements(new PermissionRequirement(
                    HelpIdAuthorizationDefaults.Permissions.ProfileReadSelf
                ))
        );

        options.AddPolicy(
            HelpIdAuthorizationPolicies.ProfileWriteSelf,
            policy => policy
                .RequireAuthenticatedUser()
                .AddRequirements(new PermissionRequirement(
                    HelpIdAuthorizationDefaults.Permissions.ProfileWriteSelf
                ))
        );

        options.AddPolicy(
            HelpIdAuthorizationPolicies.EmergencyLinkMintSelf,
            policy => policy
                .RequireAuthenticatedUser()
                .AddRequirements(new PermissionRequirement(
                    HelpIdAuthorizationDefaults.Permissions.EmergencyLinkMintSelf
                ))
        );

        options.AddPolicy(
            HelpIdAuthorizationPolicies.AuthSessionSelf,
            policy => policy
                .RequireAuthenticatedUser()
                .AddRequirements(new PermissionRequirement(
                    HelpIdAuthorizationDefaults.Permissions.AuthSessionSelf
                ))
        );

        options.AddPolicy(
            HelpIdAuthorizationPolicies.AdminMetadata,
            policy => policy
                .RequireAuthenticatedUser()
                .RequireRole(HelpIdAuthorizationDefaults.Roles.AdminName)
                .AddRequirements(new PermissionRequirement(
                    HelpIdAuthorizationDefaults.Permissions.AdminMetadataRead
                ))
        );
    }
}
