using Microsoft.Extensions.DependencyInjection;

namespace HelpId.Api.EmergencyLinks;

public static class EmergencyLinkServiceCollectionExtensions
{
    public static IServiceCollection AddHelpIdEmergencyLinkApi(
        this IServiceCollection services,
        IConfiguration configuration
    )
    {
        services.Configure<EmergencyLinkOptions>(configuration.GetSection("PublicWeb"));
        services.AddScoped<IEmergencyLinkService, EmergencyLinkService>();
        return services;
    }
}
