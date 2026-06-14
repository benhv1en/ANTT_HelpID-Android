using Microsoft.Extensions.DependencyInjection;

namespace HelpId.Api.Profiles;

public static class ProfileServiceCollectionExtensions
{
    public static IServiceCollection AddHelpIdProfileApi(
        this IServiceCollection services,
        IConfiguration configuration
    )
    {
        services.Configure<ProfileJwtOptions>(configuration.GetSection("ProfileJwt"));
        services.AddScoped<PublicProfileJwtService>();
        services.AddScoped<IPublicProfileJwtService>(
            sp => sp.GetRequiredService<PublicProfileJwtService>()
        );
        services.AddScoped<IPublicProfileTokenValidator>(
            sp => sp.GetRequiredService<PublicProfileJwtService>()
        );
        services.AddScoped<IProfileRequestValidator, ProfileRequestValidator>();
        services.AddScoped<IProfileService, ProfileService>();
        return services;
    }
}
