using HelpId.Api.Data;
using Microsoft.EntityFrameworkCore;

namespace HelpId.Api.Security;

public interface IOwnedResourceAuthorizationService
{
    Task<bool> CanAccessProfileAsync(
        string currentUserId,
        string profileUserId,
        CancellationToken cancellationToken = default
    );

    Task<bool> CanAccessPublicProfileLinkAsync(
        string currentUserId,
        string publicKey,
        CancellationToken cancellationToken = default
    );

    Task<bool> CanAccessRefreshTokenAsync(
        string currentUserId,
        string refreshTokenId,
        CancellationToken cancellationToken = default
    );
}

public sealed class OwnedResourceAuthorizationService(HelpIdDbContext dbContext)
    : IOwnedResourceAuthorizationService
{
    public Task<bool> CanAccessProfileAsync(
        string currentUserId,
        string profileUserId,
        CancellationToken cancellationToken = default
    )
    {
        if (!string.Equals(currentUserId, profileUserId, StringComparison.Ordinal))
        {
            return Task.FromResult(false);
        }

        return dbContext.UserProfiles
            .AsNoTracking()
            .AnyAsync(profile => profile.UserId == currentUserId, cancellationToken);
    }

    public Task<bool> CanAccessPublicProfileLinkAsync(
        string currentUserId,
        string publicKey,
        CancellationToken cancellationToken = default
    )
    {
        return dbContext.PublicProfileLinks
            .AsNoTracking()
            .AnyAsync(link =>
                link.PublicKey == publicKey &&
                link.UserId == currentUserId,
                cancellationToken
            );
    }

    public Task<bool> CanAccessRefreshTokenAsync(
        string currentUserId,
        string refreshTokenId,
        CancellationToken cancellationToken = default
    )
    {
        return dbContext.RefreshTokens
            .AsNoTracking()
            .AnyAsync(token =>
                token.Id == refreshTokenId &&
                token.UserId == currentUserId,
                cancellationToken
            );
    }
}
