using System.Security.Claims;
using System.Text.Json;
using HelpId.Api.Auth;
using HelpId.Api.Data;
using HelpId.Api.Security;
using Microsoft.AspNetCore.Http;
using Microsoft.Data.Sqlite;
using Microsoft.EntityFrameworkCore;
using Microsoft.Extensions.DependencyInjection;
using Microsoft.Extensions.Options;
using Xunit;

namespace HelpId.Api.Tests;

public sealed class AuthApiTests
{
    [Fact]
    public async Task Register_returns_created_and_duplicate_email_returns_conflict()
    {
        await using var environment = await AuthTestEnvironment.CreateAsync();

        var register = await environment.ExecuteAsync<AuthResponse>(
            await AuthEndpoints.RegisterAsync(
                new RegisterRequest("user@example.test", ValidPassword, "User", "Android"),
                environment.HttpContext,
                environment.AuthService,
                environment.TokenHasher,
                CancellationToken.None
            )
        );

        Assert.Equal(StatusCodes.Status201Created, register.StatusCode);
        Assert.NotNull(register.Body);
        Assert.NotEqual(register.Body.RefreshToken, await StoredRefreshTokenHashAsync(environment.Context));

        var duplicate = await environment.ExecuteAsync<JsonElement>(
            await AuthEndpoints.RegisterAsync(
                new RegisterRequest("USER@example.test", ValidPassword, "Duplicate", null),
                environment.HttpContext,
                environment.AuthService,
                environment.TokenHasher,
                CancellationToken.None
            )
        );

        Assert.Equal(StatusCodes.Status409Conflict, duplicate.StatusCode);
        Assert.Equal(1, await environment.Context.Users.CountAsync());
    }

    [Fact]
    public async Task Login_returns_tokens_for_valid_credentials_and_unauthorized_for_wrong_password()
    {
        await using var environment = await AuthTestEnvironment.CreateAsync();
        await RegisterUserAsync(environment);

        var wrongPassword = await environment.ExecuteAsync<JsonElement>(
            await AuthEndpoints.LoginAsync(
                new LoginRequest("user@example.test", "wrong-password", null),
                environment.HttpContext,
                environment.AuthService,
                environment.TokenHasher,
                CancellationToken.None
            )
        );

        Assert.Equal(StatusCodes.Status401Unauthorized, wrongPassword.StatusCode);

        var login = await environment.ExecuteAsync<AuthResponse>(
            await AuthEndpoints.LoginAsync(
                new LoginRequest("user@example.test", ValidPassword, "Android"),
                environment.HttpContext,
                environment.AuthService,
                environment.TokenHasher,
                CancellationToken.None
            )
        );

        Assert.Equal(StatusCodes.Status200OK, login.StatusCode);
        Assert.NotNull(login.Body);
        Assert.False(string.IsNullOrWhiteSpace(login.Body.AccessToken));
        Assert.False(string.IsNullOrWhiteSpace(login.Body.RefreshToken));

        var failedLoginCount = await environment.Context.Users
            .Where(user => user.Email == "user@example.test")
            .Select(user => user.FailedLoginCount)
            .SingleAsync();
        Assert.Equal(0, failedLoginCount);
    }

    [Fact]
    public async Task Refresh_rotates_refresh_token_and_logout_revokes_current_refresh_token()
    {
        await using var environment = await AuthTestEnvironment.CreateAsync();
        var register = await RegisterUserAsync(environment);
        var firstRefreshToken = register.RefreshToken;

        var refresh = await environment.ExecuteAsync<AuthResponse>(
            await AuthEndpoints.RefreshAsync(
                new RefreshRequest(firstRefreshToken, "Android"),
                environment.HttpContext,
                environment.AuthService,
                environment.TokenHasher,
                CancellationToken.None
            )
        );

        Assert.Equal(StatusCodes.Status200OK, refresh.StatusCode);
        Assert.NotNull(refresh.Body);
        Assert.NotEqual(firstRefreshToken, refresh.Body.RefreshToken);

        var oldTokenReuse = await environment.ExecuteAsync<JsonElement>(
            await AuthEndpoints.RefreshAsync(
                new RefreshRequest(firstRefreshToken, null),
                environment.HttpContext,
                environment.AuthService,
                environment.TokenHasher,
                CancellationToken.None
            )
        );
        Assert.Equal(StatusCodes.Status401Unauthorized, oldTokenReuse.StatusCode);

        var logout = await environment.ExecuteAsync<JsonElement>(
            await AuthEndpoints.LogoutAsync(
                new LogoutRequest(refresh.Body.RefreshToken),
                environment.AuthService,
                CancellationToken.None
            )
        );
        Assert.Equal(StatusCodes.Status204NoContent, logout.StatusCode);

        var afterLogout = await environment.ExecuteAsync<JsonElement>(
            await AuthEndpoints.RefreshAsync(
                new RefreshRequest(refresh.Body.RefreshToken, null),
                environment.HttpContext,
                environment.AuthService,
                environment.TokenHasher,
                CancellationToken.None
            )
        );
        Assert.Equal(StatusCodes.Status401Unauthorized, afterLogout.StatusCode);
    }

    [Fact]
    public async Task Login_locks_account_after_repeated_wrong_passwords()
    {
        await using var environment = await AuthTestEnvironment.CreateAsync();
        await RegisterUserAsync(environment);

        ApiResult<JsonElement>? result = null;
        for (var attempt = 0; attempt < 5; attempt += 1)
        {
            result = await environment.ExecuteAsync<JsonElement>(
                await AuthEndpoints.LoginAsync(
                    new LoginRequest("user@example.test", "wrong-password", null),
                    environment.HttpContext,
                    environment.AuthService,
                    environment.TokenHasher,
                    CancellationToken.None
                )
            );
        }

        Assert.NotNull(result);
        Assert.Equal(StatusCodes.Status423Locked, result!.StatusCode);

        var lockedCorrectPassword = await environment.ExecuteAsync<JsonElement>(
            await AuthEndpoints.LoginAsync(
                new LoginRequest("user@example.test", ValidPassword, null),
                environment.HttpContext,
                environment.AuthService,
                environment.TokenHasher,
                CancellationToken.None
            )
        );

        Assert.Equal(StatusCodes.Status423Locked, lockedCorrectPassword.StatusCode);
    }

    [Fact]
    public async Task Login_rejects_sql_injection_input_without_creating_session()
    {
        await using var environment = await AuthTestEnvironment.CreateAsync();
        await RegisterUserAsync(environment);
        var injection = "' OR 1=1 --";

        var invalidEmail = await environment.ExecuteAsync<JsonElement>(
            await AuthEndpoints.LoginAsync(
                new LoginRequest(injection, injection, null),
                environment.HttpContext,
                environment.AuthService,
                environment.TokenHasher,
                CancellationToken.None
            )
        );

        Assert.Equal(StatusCodes.Status400BadRequest, invalidEmail.StatusCode);

        var wrongPasswordInjection = await environment.ExecuteAsync<JsonElement>(
            await AuthEndpoints.LoginAsync(
                new LoginRequest("user@example.test", injection, null),
                environment.HttpContext,
                environment.AuthService,
                environment.TokenHasher,
                CancellationToken.None
            )
        );

        Assert.Equal(StatusCodes.Status401Unauthorized, wrongPasswordInjection.StatusCode);
        Assert.Equal(1, await environment.Context.Users.CountAsync());
        Assert.Equal(1, await environment.Context.RefreshTokens.CountAsync());
    }

    [Fact]
    public async Task Me_returns_current_user_from_access_token_subject()
    {
        await using var environment = await AuthTestEnvironment.CreateAsync();
        var register = await RegisterUserAsync(environment);
        var principal = environment.JwtAccessTokenService.ValidateAccessToken(register.AccessToken)
            ?? throw new InvalidOperationException("Access token was not valid in test setup.");

        environment.HttpContext.User = principal;
        var userContext = new CurrentUserContext(new HttpContextAccessor
        {
            HttpContext = environment.HttpContext
        });

        var me = await environment.ExecuteAsync<AuthUserResponse>(
            await AuthEndpoints.MeAsync(
                userContext,
                environment.AuthService,
                CancellationToken.None
            )
        );

        Assert.Equal(StatusCodes.Status200OK, me.StatusCode);
        Assert.NotNull(me.Body);
        Assert.Equal("user@example.test", me.Body.Email);
        Assert.Contains(HelpIdAuthorizationDefaults.Roles.UserName, me.Body.Roles);
        Assert.Contains(HelpIdAuthorizationDefaults.Permissions.AuthSessionSelf, me.Body.Permissions);
    }

    private const string ValidPassword = "Correct horse battery staple 42!";

    private static async Task<AuthResponse> RegisterUserAsync(AuthTestEnvironment environment)
    {
        var result = await environment.ExecuteAsync<AuthResponse>(
            await AuthEndpoints.RegisterAsync(
                new RegisterRequest("user@example.test", ValidPassword, "User", "Android"),
                environment.HttpContext,
                environment.AuthService,
                environment.TokenHasher,
                CancellationToken.None
            )
        );

        Assert.Equal(StatusCodes.Status201Created, result.StatusCode);
        return result.Body ?? throw new InvalidOperationException("Register response body was empty.");
    }

    private static async Task<string> StoredRefreshTokenHashAsync(HelpIdDbContext context)
    {
        return await context.RefreshTokens
            .Select(token => token.TokenHash)
            .SingleAsync();
    }

    private sealed record ApiResult<T>(int StatusCode, T? Body);

    private sealed class AuthTestEnvironment : IAsyncDisposable
    {
        private AuthTestEnvironment(
            SqliteConnection connection,
            HelpIdDbContext context,
            IAuthService authService,
            ITokenHasher tokenHasher,
            IJwtAccessTokenService jwtAccessTokenService
        )
        {
            Connection = connection;
            Context = context;
            AuthService = authService;
            TokenHasher = tokenHasher;
            JwtAccessTokenService = jwtAccessTokenService;
            ServiceProvider = new ServiceCollection()
                .AddLogging()
                .AddOptions()
                .Configure<Microsoft.AspNetCore.Http.Json.JsonOptions>(options => { })
                .BuildServiceProvider();
            HttpContext = new DefaultHttpContext
            {
                RequestServices = ServiceProvider
            };
            HttpContext.Request.Headers.UserAgent = "HelpId test";
        }

        public HelpIdDbContext Context { get; }
        public IAuthService AuthService { get; }
        public ITokenHasher TokenHasher { get; }
        public IJwtAccessTokenService JwtAccessTokenService { get; }
        public DefaultHttpContext HttpContext { get; }

        private SqliteConnection Connection { get; }
        private ServiceProvider ServiceProvider { get; }

        public static async Task<AuthTestEnvironment> CreateAsync()
        {
            var connection = new SqliteConnection("Data Source=:memory:");
            await connection.OpenAsync();

            var options = new DbContextOptionsBuilder<HelpIdDbContext>()
                .UseSqlite(connection)
                .Options;
            var context = new HelpIdDbContext(options);
            await context.Database.EnsureCreatedAsync();

            var authOptions = Options.Create(new AuthOptions
            {
                SigningKey = "0123456789abcdef0123456789abcdef",
                LockoutFailedAttempts = 5,
                LockoutMinutes = 15
            });

            var validator = new AuthRequestValidator(authOptions);
            var passwordHasher = new Pbkdf2PasswordHasher();
            var tokenHasher = new Sha256TokenHasher();
            var jwtAccessTokenService = new JwtAccessTokenService(authOptions);
            var authService = new AuthService(
                context,
                validator,
                passwordHasher,
                tokenHasher,
                jwtAccessTokenService,
                authOptions
            );

            return new AuthTestEnvironment(
                connection,
                context,
                authService,
                tokenHasher,
                jwtAccessTokenService
            );
        }

        public async Task<ApiResult<T>> ExecuteAsync<T>(IResult result)
        {
            HttpContext.Response.Body = new MemoryStream();
            HttpContext.Response.StatusCode = StatusCodes.Status200OK;

            await result.ExecuteAsync(HttpContext);

            HttpContext.Response.Body.Position = 0;
            if (HttpContext.Response.Body.Length == 0)
            {
                return new ApiResult<T>(HttpContext.Response.StatusCode, default);
            }

            var body = await JsonSerializer.DeserializeAsync<T>(
                HttpContext.Response.Body,
                new JsonSerializerOptions(JsonSerializerDefaults.Web)
            );

            return new ApiResult<T>(HttpContext.Response.StatusCode, body);
        }

        public async ValueTask DisposeAsync()
        {
            await Context.DisposeAsync();
            await Connection.DisposeAsync();
            await ServiceProvider.DisposeAsync();
        }
    }
}
