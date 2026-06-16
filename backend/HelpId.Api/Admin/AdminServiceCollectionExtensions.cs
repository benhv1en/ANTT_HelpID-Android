using Microsoft.Extensions.DependencyInjection;

namespace HelpId.Api.Admin;

public static class AdminServiceCollectionExtensions
{
    public static IServiceCollection AddHelpIdAdminApi(this IServiceCollection services)
    {
        services.AddScoped<IAdminService, AdminService>();
        return services;
    }
}
