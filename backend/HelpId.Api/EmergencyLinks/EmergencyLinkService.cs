using System.Security.Cryptography;
using System.Text.RegularExpressions;
using HelpId.Api.Data;
using HelpId.Api.Data.Entities;
using HelpId.Api.Profiles;
using Microsoft.EntityFrameworkCore;
using Microsoft.Extensions.Options;

namespace HelpId.Api.EmergencyLinks;

public enum MintStatus { Ok, ValidationFailed, Conflict, KeyCollision }

public sealed record MintResult(MintStatus Status, MintResponse? Response);

public interface IEmergencyLinkService
{
    Task<MintResult> MintAsync(
        string userId,
        string? requestedPublicKey,
        CancellationToken cancellationToken = default
    );
}

public sealed class EmergencyLinkService(
    HelpIdDbContext dbContext,
    IPublicProfileJwtService profileJwtService,
    IOptions<EmergencyLinkOptions> options
) : IEmergencyLinkService
{
    internal static readonly Regex PublicKeyRegex =
        new(@"^HID-[A-Z0-9_-]{8,64}$", RegexOptions.Compiled, TimeSpan.FromMilliseconds(100));

    private readonly EmergencyLinkOptions _options = options.Value;

    public async Task<MintResult> MintAsync(
        string userId,
        string? requestedPublicKey,
        CancellationToken cancellationToken = default
    )
    {
        var now = DateTimeOffset.UtcNow;
        string publicKey;

        if (!string.IsNullOrWhiteSpace(requestedPublicKey))
        {
            if (!PublicKeyRegex.IsMatch(requestedPublicKey))
            {
                return new MintResult(MintStatus.ValidationFailed, null);
            }

            var existing = await dbContext.PublicProfileLinks
                .SingleOrDefaultAsync(
                    link => link.PublicKey == requestedPublicKey,
                    cancellationToken
                );

            if (existing is not null && !string.Equals(existing.UserId, userId, StringComparison.Ordinal))
            {
                return new MintResult(MintStatus.Conflict, null);
            }

            if (existing is not null)
            {
                existing.UpdatedAtUtc = now;
                existing.LastMintedAtUtc = now;
                existing.RevokedAtUtc = null;
            }
            else
            {
                dbContext.PublicProfileLinks.Add(new PublicProfileLink
                {
                    PublicKey = requestedPublicKey,
                    UserId = userId,
                    CreatedAtUtc = now,
                    UpdatedAtUtc = now,
                    LastMintedAtUtc = now
                });
            }

            publicKey = requestedPublicKey;
        }
        else
        {
            var existingKey = await dbContext.PublicProfileLinks
                .AsNoTracking()
                .Where(link => link.UserId == userId && link.RevokedAtUtc == null)
                .Select(link => link.PublicKey)
                .FirstOrDefaultAsync(cancellationToken);

            if (existingKey is not null)
            {
                var record = await dbContext.PublicProfileLinks
                    .SingleAsync(link => link.PublicKey == existingKey, cancellationToken);
                record.UpdatedAtUtc = now;
                record.LastMintedAtUtc = now;
                publicKey = existingKey;
            }
            else
            {
                var generated = await GenerateUniqueKeyAsync(userId, now, cancellationToken);
                if (generated is null)
                {
                    return new MintResult(MintStatus.KeyCollision, null);
                }

                publicKey = generated;
            }
        }

        await dbContext.SaveChangesAsync(cancellationToken);

        var tokenResult = profileJwtService.IssueToken(publicKey);
        var baseUrl = _options.BaseUrl.TrimEnd('/');
        var url = $"{baseUrl}/e/{publicKey}?t={tokenResult.Token}";

        return new MintResult(MintStatus.Ok, new MintResponse(
            publicKey,
            tokenResult.ExpiresInSeconds,
            tokenResult.Token,
            url
        ));
    }

    private async Task<string?> GenerateUniqueKeyAsync(
        string userId,
        DateTimeOffset now,
        CancellationToken cancellationToken
    )
    {
        for (var attempt = 0; attempt < 3; attempt++)
        {
            var randomPart = Convert.ToHexString(RandomNumberGenerator.GetBytes(8));
            var candidateKey = $"HID-{randomPart}";

            var exists = await dbContext.PublicProfileLinks
                .AnyAsync(link => link.PublicKey == candidateKey, cancellationToken);

            if (!exists)
            {
                dbContext.PublicProfileLinks.Add(new PublicProfileLink
                {
                    PublicKey = candidateKey,
                    UserId = userId,
                    CreatedAtUtc = now,
                    UpdatedAtUtc = now,
                    LastMintedAtUtc = now
                });

                return candidateKey;
            }
        }

        return null;
    }
}
