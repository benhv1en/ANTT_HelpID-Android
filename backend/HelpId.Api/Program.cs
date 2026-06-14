using HelpId.Api.Auth;
using HelpId.Api.Data;
using HelpId.Api.EmergencyLinks;
using HelpId.Api.Profiles;
using HelpId.Api.Security;
using Microsoft.EntityFrameworkCore;

var builder = WebApplication.CreateBuilder(args);

var connectionString = builder.Configuration.GetConnectionString("HelpIdDb")
    ?? throw new InvalidOperationException("Missing ConnectionStrings:HelpIdDb configuration.");

builder.Services.AddDbContext<HelpIdDbContext>(options =>
{
    options.UseSqlite(connectionString);
});

builder.Services.AddHelpIdAuthApi(builder.Configuration);
builder.Services.AddHelpIdProfileApi(builder.Configuration);
builder.Services.AddHelpIdEmergencyLinkApi(builder.Configuration);
builder.Services.AddHelpIdAuthorization();

var app = builder.Build();

app.UseExceptionHandler(exceptionApp =>
{
    exceptionApp.Run(async context =>
    {
        context.Response.StatusCode = StatusCodes.Status500InternalServerError;
        await Results.Problem(
            title: "Request failed.",
            statusCode: StatusCodes.Status500InternalServerError
        ).ExecuteAsync(context);
    });
});

app.Use(async (context, next) =>
{
    context.Response.Headers.XContentTypeOptions = "nosniff";
    await next();
});

app.UseAuthentication();
app.UseAuthorization();

app.MapGet("/", () => Results.Redirect("/health"));

app.MapGet("/health", () =>
{
    var response = new HealthResponse(
        Status: "ok",
        Service: "HelpId.Api",
        CheckedAtUtc: DateTimeOffset.UtcNow
    );

    return Results.Ok(response);
});

app.MapAuthEndpoints();
app.MapProfileEndpoints();
app.MapEmergencyLinkEndpoints();

app.Run();

public partial class Program
{
}

internal sealed record HealthResponse(
    string Status,
    string Service,
    DateTimeOffset CheckedAtUtc
);
