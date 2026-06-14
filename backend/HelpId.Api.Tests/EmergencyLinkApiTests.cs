using System.Text.Json;
using HelpId.Api.Auth;
using HelpId.Api.Data;
using HelpId.Api.EmergencyLinks;
using HelpId.Api.Profiles;
using HelpId.Api.Security;
using Microsoft.AspNetCore.Http;
using Microsoft.Data.Sqlite;
using Microsoft.EntityFrameworkCore;
using Microsoft.Extensions.DependencyInjection;
using Microsoft.Extensions.Options;
using Xunit;

namespace HelpId.Api.Tests;

public sealed class EmergencyLinkApiTests
{
    [Fact]
    public async Task Mint_without_key_generates_valid_hid_format_key_and_reuses_on_second_call()
    {
        await using var env = await EmergencyLinkTestEnvironment.CreateAsync();
        var user = await env.RegisterAsync("user@example.test");
        env.SetCurrentUser(user.User.Id);

        var first = await env.ExecuteAsync<MintResponse>(
            await EmergencyLinkEndpoints.MintAsync(
                new MintRequest(null),
                env.UserContext,
                env.EmergencyLinkService,
                env.HttpContext,
                CancellationToken.None
            )
        );

        Assert.Equal(StatusCodes.Status200OK, first.StatusCode);
        Assert.NotNull(first.Body);
        Assert.Matches(@"^HID-[A-Z0-9]{16}$", first.Body!.PublicKey);
        Assert.Equal(10800, first.Body.ExpiresInSeconds);
        Assert.False(string.IsNullOrWhiteSpace(first.Body.Token));
        Assert.Contains(first.Body.PublicKey, first.Body.Url);

        // Second mint without key should reuse the same key
        var second = await env.ExecuteAsync<MintResponse>(
            await EmergencyLinkEndpoints.MintAsync(
                new MintRequest(null),
                env.UserContext,
                env.EmergencyLinkService,
                env.HttpContext,
                CancellationToken.None
            )
        );

        Assert.Equal(StatusCodes.Status200OK, second.StatusCode);
        Assert.Equal(first.Body.PublicKey, second.Body!.PublicKey);
        Assert.NotEqual(first.Body.Token, second.Body.Token);

        Assert.Equal(1, await env.Context.PublicProfileLinks.CountAsync());
    }

    [Fact]
    public async Task Mint_with_own_key_reuses_it_and_another_user_key_returns_conflict()
    {
        await using var env = await EmergencyLinkTestEnvironment.CreateAsync();
        var userA = await env.RegisterAsync("user-a@example.test");
        var userB = await env.RegisterAsync("user-b@example.test");

        env.SetCurrentUser(userA.User.Id);
        var mintA = await env.ExecuteAsync<MintResponse>(
            await EmergencyLinkEndpoints.MintAsync(
                new MintRequest(null),
                env.UserContext,
                env.EmergencyLinkService,
                env.HttpContext,
                CancellationToken.None
            )
        );
        Assert.Equal(StatusCodes.Status200OK, mintA.StatusCode);
        var keyA = mintA.Body!.PublicKey;

        // User A mints again with own key: should succeed
        var reuse = await env.ExecuteAsync<MintResponse>(
            await EmergencyLinkEndpoints.MintAsync(
                new MintRequest(keyA),
                env.UserContext,
                env.EmergencyLinkService,
                env.HttpContext,
                CancellationToken.None
            )
        );
        Assert.Equal(StatusCodes.Status200OK, reuse.StatusCode);
        Assert.Equal(keyA, reuse.Body!.PublicKey);

        // User B tries to mint with user A's key: should conflict
        env.SetCurrentUser(userB.User.Id);
        var conflict = await env.ExecuteAsync<JsonElement>(
            await EmergencyLinkEndpoints.MintAsync(
                new MintRequest(keyA),
                env.UserContext,
                env.EmergencyLinkService,
                env.HttpContext,
                CancellationToken.None
            )
        );
        Assert.Equal(StatusCodes.Status409Conflict, conflict.StatusCode);
    }

    [Fact]
    public async Task Mint_with_invalid_key_format_returns_bad_request()
    {
        await using var env = await EmergencyLinkTestEnvironment.CreateAsync();
        var user = await env.RegisterAsync("user@example.test");
        env.SetCurrentUser(user.User.Id);

        foreach (var badKey in new[] { "INVALID", "hid-lowercase", "HID-", "HID-abc", "' OR 1=1 --" })
        {
            var result = await env.ExecuteAsync<JsonElement>(
                await EmergencyLinkEndpoints.MintAsync(
                    new MintRequest(badKey),
                    env.UserContext,
                    env.EmergencyLinkService,
                    env.HttpContext,
                    CancellationToken.None
                )
            );
            Assert.Equal(StatusCodes.Status400BadRequest, result.StatusCode);
        }

        Assert.Equal(0, await env.Context.PublicProfileLinks.CountAsync());
    }

    [Fact]
    public async Task Public_profile_returns_whitelist_for_valid_key_and_token()
    {
        await using var env = await EmergencyLinkTestEnvironment.CreateAsync();
        var user = await env.RegisterAsync("user@example.test");
        env.SetCurrentUser(user.User.Id);

        await env.ProfileService.UpdateProfileAsync(
            user.User.Id,
            new UpdateProfileRequest(
                "Nguyen Van A", "O+", "District 1", "vi",
                ["Penicillin"],
                ["Diabetes"],
                [new EmergencyContactRequest("Father", "+84901234567", "Father")]
            ),
            CancellationToken.None
        );

        var mint = await env.ExecuteAsync<MintResponse>(
            await EmergencyLinkEndpoints.MintAsync(
                new MintRequest(null),
                env.UserContext,
                env.EmergencyLinkService,
                env.HttpContext,
                CancellationToken.None
            )
        );
        Assert.Equal(StatusCodes.Status200OK, mint.StatusCode);
        var publicKey = mint.Body!.PublicKey;
        var token = mint.Body.Token;

        var result = await env.ExecuteAsync<JsonElement>(
            await EmergencyLinkEndpoints.GetPublicProfileAsync(
                publicKey,
                token,
                env.PublicProfileAccessService,
                env.HttpContext,
                CancellationToken.None
            )
        );

        Assert.Equal(StatusCodes.Status200OK, result.StatusCode);
        Assert.NotEqual(JsonValueKind.Undefined, result.Body.ValueKind);
        Assert.Equal(publicKey, result.Body.GetProperty("key").GetString());
        var profile = result.Body.GetProperty("profile");
        Assert.Equal("Nguyen Van A", profile.GetProperty("name").GetString());
        Assert.Equal("O+", profile.GetProperty("bloodGroup").GetString());
        Assert.Equal(1, profile.GetProperty("allergies").GetArrayLength());
        Assert.Equal(1, profile.GetProperty("emergencyContacts").GetArrayLength());

        // Whitelist check: should not contain userId, email, language
        Assert.False(profile.TryGetProperty("userId", out _));
        Assert.False(profile.TryGetProperty("email", out _));
        Assert.False(profile.TryGetProperty("language", out _));
    }

    [Fact]
    public async Task Public_profile_rejects_invalid_token_and_wrong_key_format()
    {
        await using var env = await EmergencyLinkTestEnvironment.CreateAsync();
        var user = await env.RegisterAsync("user@example.test");
        env.SetCurrentUser(user.User.Id);

        var mint = await env.ExecuteAsync<MintResponse>(
            await EmergencyLinkEndpoints.MintAsync(
                new MintRequest(null),
                env.UserContext,
                env.EmergencyLinkService,
                env.HttpContext,
                CancellationToken.None
            )
        );
        var publicKey = mint.Body!.PublicKey;

        // Wrong token
        var wrongToken = await env.ExecuteAsync<JsonElement>(
            await EmergencyLinkEndpoints.GetPublicProfileAsync(
                publicKey,
                "invalid.token.value",
                env.PublicProfileAccessService,
                env.HttpContext,
                CancellationToken.None
            )
        );
        Assert.Equal(StatusCodes.Status401Unauthorized, wrongToken.StatusCode);

        // Wrong key format (SQL injection attempt)
        var badKey = await env.ExecuteAsync<JsonElement>(
            await EmergencyLinkEndpoints.GetPublicProfileAsync(
                "' OR 1=1 --",
                mint.Body.Token,
                env.PublicProfileAccessService,
                env.HttpContext,
                CancellationToken.None
            )
        );
        Assert.Equal(StatusCodes.Status400BadRequest, badKey.StatusCode);

        // Missing key
        var missingKey = await env.ExecuteAsync<JsonElement>(
            await EmergencyLinkEndpoints.GetPublicProfileAsync(
                null,
                mint.Body.Token,
                env.PublicProfileAccessService,
                env.HttpContext,
                CancellationToken.None
            )
        );
        Assert.Equal(StatusCodes.Status400BadRequest, missingKey.StatusCode);

        // Token mismatch with different key
        var mismatch = await env.ExecuteAsync<JsonElement>(
            await EmergencyLinkEndpoints.GetPublicProfileAsync(
                "HID-AAAAAAAAAAAAAAAA",
                mint.Body.Token,
                env.PublicProfileAccessService,
                env.HttpContext,
                CancellationToken.None
            )
        );
        Assert.Equal(StatusCodes.Status401Unauthorized, mismatch.StatusCode);
    }

    [Fact]
    public async Task Public_profile_adds_no_store_and_noindex_headers()
    {
        await using var env = await EmergencyLinkTestEnvironment.CreateAsync();
        var user = await env.RegisterAsync("user@example.test");
        env.SetCurrentUser(user.User.Id);

        env.HttpContext.Response.Body = new MemoryStream();
        env.HttpContext.Response.StatusCode = StatusCodes.Status200OK;

        await (await EmergencyLinkEndpoints.GetPublicProfileAsync(
            null,
            null,
            env.PublicProfileAccessService,
            env.HttpContext,
            CancellationToken.None
        )).ExecuteAsync(env.HttpContext);

        Assert.Equal("no-store", env.HttpContext.Response.Headers.CacheControl.ToString());
        Assert.Equal("no-cache", env.HttpContext.Response.Headers["Pragma"].ToString());
        Assert.Equal("0", env.HttpContext.Response.Headers["Expires"].ToString());
        Assert.Equal("noindex, nofollow, noarchive", env.HttpContext.Response.Headers["X-Robots-Tag"].ToString());
        Assert.Equal("no-referrer", env.HttpContext.Response.Headers["Referrer-Policy"].ToString());
    }

    private sealed class EmergencyLinkTestEnvironment : IAsyncDisposable
    {
        private readonly SqliteConnection _connection;
        private readonly ServiceProvider _serviceProvider;

        public HelpIdDbContext Context { get; }
        public IAuthService AuthService { get; }
        public ITokenHasher TokenHasher { get; }
        public IProfileService ProfileService { get; }
        public IEmergencyLinkService EmergencyLinkService { get; }
        public IPublicProfileAccessService PublicProfileAccessService { get; }
        public DefaultHttpContext HttpContext { get; }
        public SimpleCurrentUserContext UserContext { get; }

        private EmergencyLinkTestEnvironment(
            SqliteConnection connection,
            HelpIdDbContext context,
            IAuthService authService,
            ITokenHasher tokenHasher,
            IProfileService profileService,
            IEmergencyLinkService emergencyLinkService,
            IPublicProfileAccessService publicProfileAccessService,
            ServiceProvider serviceProvider
        )
        {
            _connection = connection;
            _serviceProvider = serviceProvider;
            Context = context;
            AuthService = authService;
            TokenHasher = tokenHasher;
            ProfileService = profileService;
            EmergencyLinkService = emergencyLinkService;
            PublicProfileAccessService = publicProfileAccessService;
            UserContext = new SimpleCurrentUserContext();
            HttpContext = new DefaultHttpContext
            {
                RequestServices = serviceProvider,
                Response = { Body = new MemoryStream() }
            };
        }

        public static async Task<EmergencyLinkTestEnvironment> CreateAsync()
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

            var profileJwtOptions = Options.Create(new ProfileJwtOptions
            {
                SigningKey = "profile-jwt-signing-key-must-be-32-chars"
            });

            var emergencyLinkOptions = Options.Create(new EmergencyLinkOptions
            {
                BaseUrl = "https://helper-id.vercel.app"
            });

            var passwordHasher = new Pbkdf2PasswordHasher();
            var tokenHasher = new Sha256TokenHasher();
            var jwtService = new JwtAccessTokenService(authOptions);
            var authValidator = new AuthRequestValidator(authOptions);
            var authService = new AuthService(
                context, authValidator, passwordHasher, tokenHasher, jwtService, authOptions
            );

            var profileJwtService = new PublicProfileJwtService(profileJwtOptions);
            var profileValidator = new ProfileRequestValidator();
            var profileService = new ProfileService(context, profileValidator);
            var emergencyLinkService = new EmergencyLinkService(
                context, profileJwtService, emergencyLinkOptions
            );
            var publicProfileAccessService = new PublicProfileAccessService(context, profileJwtService);

            var serviceProvider = new ServiceCollection()
                .AddLogging()
                .AddOptions()
                .Configure<Microsoft.AspNetCore.Http.Json.JsonOptions>(o => { })
                .BuildServiceProvider();

            return new EmergencyLinkTestEnvironment(
                connection, context, authService, tokenHasher,
                profileService, emergencyLinkService, publicProfileAccessService,
                serviceProvider
            );
        }

        public void SetCurrentUser(string userId)
        {
            UserContext.UserId = userId;
        }

        public async Task<AuthResponse> RegisterAsync(string email)
        {
            HttpContext.Response.Body = new MemoryStream();
            HttpContext.Response.StatusCode = StatusCodes.Status200OK;

            var result = await AuthEndpoints.RegisterAsync(
                new RegisterRequest(email, "Correct horse battery staple 42!", "Test", "Android"),
                HttpContext,
                AuthService,
                TokenHasher,
                CancellationToken.None
            );

            HttpContext.Response.Body = new MemoryStream();
            await result.ExecuteAsync(HttpContext);
            HttpContext.Response.Body.Position = 0;

            var body = await JsonSerializer.DeserializeAsync<AuthResponse>(
                HttpContext.Response.Body,
                new JsonSerializerOptions(JsonSerializerDefaults.Web)
            );
            return body ?? throw new InvalidOperationException("Register returned null.");
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
            await _connection.DisposeAsync();
            await _serviceProvider.DisposeAsync();
        }
    }

    private sealed class SimpleCurrentUserContext : ICurrentUserContext
    {
        public string? UserId { get; set; }
        public bool IsAuthenticated => UserId is not null;

        public string GetRequiredUserId()
        {
            return UserId ?? throw new InvalidOperationException("No user set in test context.");
        }
    }

    private sealed record ApiResult<T>(int StatusCode, T? Body);
}
