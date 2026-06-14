using Microsoft.AspNetCore.Authentication;

namespace HelpId.Api.Auth;

public static class AuthServiceCollectionExtensions
{
    public static IServiceCollection AddHelpIdAuthApi(
        this IServiceCollection services,
        IConfiguration configuration
    )
    {
        services.Configure<AuthOptions>(configuration.GetSection("AuthJwt"));
        services.AddScoped<IAuthRequestValidator, AuthRequestValidator>();
        services.AddSingleton<IPasswordHasher, Pbkdf2PasswordHasher>();
        services.AddSingleton<ITokenHasher, Sha256TokenHasher>();
        services.AddScoped<IJwtAccessTokenService, JwtAccessTokenService>();
        services.AddScoped<IAuthService, AuthService>();

        services
            .AddAuthentication(JwtAuthenticationDefaults.Scheme)
            .AddScheme<AuthenticationSchemeOptions, JwtAuthenticationHandler>(
                JwtAuthenticationDefaults.Scheme,
                options => { }
            );

        return services;
    }
}
