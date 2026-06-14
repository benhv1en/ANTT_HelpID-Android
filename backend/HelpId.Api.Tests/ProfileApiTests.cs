using System.Text.Json;
using HelpId.Api.Auth;
using HelpId.Api.Data;
using HelpId.Api.Profiles;
using HelpId.Api.Security;
using Microsoft.AspNetCore.Http;
using Microsoft.Data.Sqlite;
using Microsoft.EntityFrameworkCore;
using Microsoft.Extensions.DependencyInjection;
using Microsoft.Extensions.Options;
using Xunit;

namespace HelpId.Api.Tests;

public sealed class ProfileApiTests
{
    [Fact]
    public async Task Get_profile_returns_empty_profile_for_new_user()
    {
        await using var env = await ProfileTestEnvironment.CreateAsync();
        var register = await env.RegisterAsync("user@example.test");

        env.SetCurrentUser(register.User.Id);
        var result = await env.ExecuteAsync<JsonElement>(
            await ProfileEndpoints.GetProfileAsync(
                env.UserContext,
                env.ProfileService,
                env.HttpContext,
                CancellationToken.None
            )
        );

        Assert.Equal(StatusCodes.Status200OK, result.StatusCode);
        Assert.NotEqual(JsonValueKind.Undefined, result.Body.ValueKind);
        var profile = result.Body.GetProperty("profile");
        Assert.Equal(register.User.Id, profile.GetProperty("userId").GetString());
        Assert.Equal("", profile.GetProperty("name").GetString());
        Assert.Equal("", profile.GetProperty("bloodGroup").GetString());
        Assert.Equal(0, profile.GetProperty("allergies").GetArrayLength());
        Assert.Equal(0, profile.GetProperty("emergencyContacts").GetArrayLength());
    }

    [Fact]
    public async Task Update_profile_persists_all_fields_and_replaces_lists()
    {
        await using var env = await ProfileTestEnvironment.CreateAsync();
        var register = await env.RegisterAsync("user@example.test");
        env.SetCurrentUser(register.User.Id);

        var updateRequest = new UpdateProfileRequest(
            Name: "Nguyen Van A",
            BloodGroup: "O+",
            Address: "District 1",
            Language: "vi",
            Allergies: ["Penicillin", "Latex"],
            MedicalNotes: ["Diabetes"],
            EmergencyContacts: [new EmergencyContactRequest("Father", "+84901234567", "Father")]
        );

        var update = await env.ExecuteAsync<JsonElement>(
            await ProfileEndpoints.UpdateProfileAsync(
                updateRequest,
                env.UserContext,
                env.ProfileService,
                env.HttpContext,
                CancellationToken.None
            )
        );

        Assert.Equal(StatusCodes.Status200OK, update.StatusCode);
        var profile = update.Body.GetProperty("profile");
        Assert.Equal("Nguyen Van A", profile.GetProperty("name").GetString());
        Assert.Equal("O+", profile.GetProperty("bloodGroup").GetString());
        Assert.Equal("District 1", profile.GetProperty("address").GetString());
        Assert.Equal("vi", profile.GetProperty("language").GetString());
        Assert.Equal(2, profile.GetProperty("allergies").GetArrayLength());
        Assert.Equal("Penicillin", profile.GetProperty("allergies")[0].GetString());
        Assert.Equal(1, profile.GetProperty("medicalNotes").GetArrayLength());
        Assert.Equal(1, profile.GetProperty("emergencyContacts").GetArrayLength());
        var contact = profile.GetProperty("emergencyContacts")[0];
        Assert.Equal("Father", contact.GetProperty("name").GetString());
        Assert.Equal("+84901234567", contact.GetProperty("phone").GetString());
        Assert.Equal("Father", contact.GetProperty("relationship").GetString());

        // Replacing lists: update again with only one allergy
        var updateAgain = await env.ExecuteAsync<JsonElement>(
            await ProfileEndpoints.UpdateProfileAsync(
                new UpdateProfileRequest(null, null, null, null,
                    Allergies: ["Aspirin"],
                    MedicalNotes: null,
                    EmergencyContacts: null
                ),
                env.UserContext,
                env.ProfileService,
                env.HttpContext,
                CancellationToken.None
            )
        );

        Assert.Equal(StatusCodes.Status200OK, updateAgain.StatusCode);
        var updated = updateAgain.Body.GetProperty("profile");
        Assert.Equal(1, updated.GetProperty("allergies").GetArrayLength());
        Assert.Equal("Aspirin", updated.GetProperty("allergies")[0].GetString());
        // MedicalNotes null means unchanged: still 1
        Assert.Equal(1, updated.GetProperty("medicalNotes").GetArrayLength());
    }

    [Fact]
    public async Task Update_profile_rejects_invalid_blood_group_and_too_many_allergies()
    {
        await using var env = await ProfileTestEnvironment.CreateAsync();
        var register = await env.RegisterAsync("user@example.test");
        env.SetCurrentUser(register.User.Id);

        var badBloodGroup = await env.ExecuteAsync<JsonElement>(
            await ProfileEndpoints.UpdateProfileAsync(
                new UpdateProfileRequest(null, "XX", null, null, null, null, null),
                env.UserContext,
                env.ProfileService,
                env.HttpContext,
                CancellationToken.None
            )
        );
        Assert.Equal(StatusCodes.Status422UnprocessableEntity, badBloodGroup.StatusCode);

        var tooManyAllergies = await env.ExecuteAsync<JsonElement>(
            await ProfileEndpoints.UpdateProfileAsync(
                new UpdateProfileRequest(
                    null, null, null, null,
                    Allergies: Enumerable.Range(0, 21).Select(i => $"Allergy {i}").ToList(),
                    null, null
                ),
                env.UserContext,
                env.ProfileService,
                env.HttpContext,
                CancellationToken.None
            )
        );
        Assert.Equal(StatusCodes.Status422UnprocessableEntity, tooManyAllergies.StatusCode);

        var badLanguage = await env.ExecuteAsync<JsonElement>(
            await ProfileEndpoints.UpdateProfileAsync(
                new UpdateProfileRequest(null, null, null, "zz", null, null, null),
                env.UserContext,
                env.ProfileService,
                env.HttpContext,
                CancellationToken.None
            )
        );
        Assert.Equal(StatusCodes.Status422UnprocessableEntity, badLanguage.StatusCode);
    }

    [Fact]
    public async Task Update_profile_rejects_invalid_emergency_contact_phone()
    {
        await using var env = await ProfileTestEnvironment.CreateAsync();
        var register = await env.RegisterAsync("user@example.test");
        env.SetCurrentUser(register.User.Id);

        var badPhone = await env.ExecuteAsync<JsonElement>(
            await ProfileEndpoints.UpdateProfileAsync(
                new UpdateProfileRequest(
                    null, null, null, null, null, null,
                    EmergencyContacts:
                    [
                        new EmergencyContactRequest("Mother", "not-a-phone", null)
                    ]
                ),
                env.UserContext,
                env.ProfileService,
                env.HttpContext,
                CancellationToken.None
            )
        );
        Assert.Equal(StatusCodes.Status422UnprocessableEntity, badPhone.StatusCode);

        var nameButNoPhone = await env.ExecuteAsync<JsonElement>(
            await ProfileEndpoints.UpdateProfileAsync(
                new UpdateProfileRequest(
                    null, null, null, null, null, null,
                    EmergencyContacts:
                    [
                        new EmergencyContactRequest("Mother", null, null)
                    ]
                ),
                env.UserContext,
                env.ProfileService,
                env.HttpContext,
                CancellationToken.None
            )
        );
        Assert.Equal(StatusCodes.Status422UnprocessableEntity, nameButNoPhone.StatusCode);
    }

    [Fact]
    public async Task Update_profile_stores_sql_injection_input_safely()
    {
        await using var env = await ProfileTestEnvironment.CreateAsync();
        var register = await env.RegisterAsync("user@example.test");
        env.SetCurrentUser(register.User.Id);

        var injection = "'; DROP TABLE UserProfiles; --";

        var update = await env.ExecuteAsync<JsonElement>(
            await ProfileEndpoints.UpdateProfileAsync(
                new UpdateProfileRequest(injection, null, null, null, null, null, null),
                env.UserContext,
                env.ProfileService,
                env.HttpContext,
                CancellationToken.None
            )
        );

        Assert.Equal(StatusCodes.Status200OK, update.StatusCode);

        // Verify the table still exists and the exact string was stored
        var get = await env.ExecuteAsync<JsonElement>(
            await ProfileEndpoints.GetProfileAsync(
                env.UserContext,
                env.ProfileService,
                env.HttpContext,
                CancellationToken.None
            )
        );
        Assert.Equal(StatusCodes.Status200OK, get.StatusCode);
        Assert.Equal(injection, get.Body.GetProperty("profile").GetProperty("name").GetString());

        // UserProfiles table still exists and can be queried
        var profileCount = await env.Context.UserProfiles.CountAsync();
        Assert.Equal(1, profileCount);
    }

    [Fact]
    public async Task Profile_response_does_not_include_sensitive_fields()
    {
        await using var env = await ProfileTestEnvironment.CreateAsync();
        var register = await env.RegisterAsync("user@example.test");
        env.SetCurrentUser(register.User.Id);

        var result = await env.ExecuteAsync<JsonElement>(
            await ProfileEndpoints.GetProfileAsync(
                env.UserContext,
                env.ProfileService,
                env.HttpContext,
                CancellationToken.None
            )
        );

        Assert.Equal(StatusCodes.Status200OK, result.StatusCode);
        var profile = result.Body.GetProperty("profile");

        // Private fields that should not appear
        Assert.False(profile.TryGetProperty("passwordHash", out _));
        Assert.False(profile.TryGetProperty("securityStamp", out _));
        Assert.False(profile.TryGetProperty("email", out _));
    }

    private sealed class ProfileTestEnvironment : IAsyncDisposable
    {
        private readonly SqliteConnection _connection;
        private readonly ServiceProvider _serviceProvider;

        public HelpIdDbContext Context { get; }
        public IProfileService ProfileService { get; }
        public IAuthService AuthService { get; }
        public ITokenHasher TokenHasher { get; }
        public DefaultHttpContext HttpContext { get; }
        public SimpleCurrentUserContext UserContext { get; }

        private ProfileTestEnvironment(
            SqliteConnection connection,
            HelpIdDbContext context,
            IProfileService profileService,
            IAuthService authService,
            ITokenHasher tokenHasher,
            ServiceProvider serviceProvider
        )
        {
            _connection = connection;
            _serviceProvider = serviceProvider;
            Context = context;
            ProfileService = profileService;
            AuthService = authService;
            TokenHasher = tokenHasher;
            UserContext = new SimpleCurrentUserContext();
            HttpContext = new DefaultHttpContext
            {
                RequestServices = serviceProvider,
                Response = { Body = new MemoryStream() }
            };
        }

        public static async Task<ProfileTestEnvironment> CreateAsync()
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

            var passwordHasher = new Pbkdf2PasswordHasher();
            var tokenHasher = new Sha256TokenHasher();
            var jwtService = new JwtAccessTokenService(authOptions);
            var validator = new AuthRequestValidator(authOptions);
            var authService = new AuthService(
                context, validator, passwordHasher, tokenHasher, jwtService, authOptions
            );

            var profileValidator = new ProfileRequestValidator();
            var profileService = new ProfileService(context, profileValidator);

            var serviceProvider = new ServiceCollection()
                .AddLogging()
                .AddOptions()
                .Configure<Microsoft.AspNetCore.Http.Json.JsonOptions>(o => { })
                .BuildServiceProvider();

            return new ProfileTestEnvironment(
                connection, context, profileService, authService, tokenHasher, serviceProvider
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
                new RegisterRequest(email, "Correct horse battery staple 42!", "Test User", "Android"),
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
