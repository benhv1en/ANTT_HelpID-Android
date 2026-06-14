using HelpId.Api.Data;
using HelpId.Api.Security;
using Microsoft.EntityFrameworkCore;

namespace HelpId.Api.Profiles;

public interface IPublicProfileAccessService
{
    Task<PublicProfileAccessResult> GetProfileAsync(
        string publicKey,
        string publicProfileJwt,
        CancellationToken cancellationToken = default
    );
}

public sealed class PublicProfileAccessService(
    HelpIdDbContext dbContext,
    IPublicProfileTokenValidator tokenValidator
) : IPublicProfileAccessService
{
    public async Task<PublicProfileAccessResult> GetProfileAsync(
        string publicKey,
        string publicProfileJwt,
        CancellationToken cancellationToken = default
    )
    {
        var tokenResult = await tokenValidator.ValidateAsync(
            publicKey,
            publicProfileJwt,
            cancellationToken
        );

        var now = DateTimeOffset.UtcNow;
        var tokenLifetime = tokenResult.ExpiresAtUtc - tokenResult.IssuedAtUtc;
        if (
            !tokenResult.IsValid ||
            tokenResult.ExpiresAtUtc <= now ||
            tokenLifetime > HelpIdAuthorizationDefaults.PublicProfileTokenMaxLifetime
        )
        {
            return PublicProfileAccessResult.Forbidden();
        }

        var link = await dbContext.PublicProfileLinks
            .AsNoTracking()
            .Where(candidate => candidate.PublicKey == publicKey && candidate.RevokedAtUtc == null)
            .Select(candidate => new { candidate.UserId })
            .SingleOrDefaultAsync(cancellationToken);

        if (link is null)
        {
            return PublicProfileAccessResult.NotFound();
        }

        var profile = await dbContext.UserProfiles
            .AsNoTracking()
            .Where(candidate => candidate.UserId == link.UserId)
            .Select(candidate => new
            {
                candidate.FullName,
                candidate.BloodGroup,
                candidate.Address
            })
            .SingleOrDefaultAsync(cancellationToken);

        if (profile is null)
        {
            return PublicProfileAccessResult.NotFound();
        }

        var allergies = await dbContext.ProfileAllergies
            .AsNoTracking()
            .Where(allergy => allergy.UserId == link.UserId)
            .OrderBy(allergy => allergy.SortOrder)
            .Select(allergy => allergy.Value)
            .ToListAsync(cancellationToken);

        var medicalNotes = await dbContext.MedicalNotes
            .AsNoTracking()
            .Where(note => note.UserId == link.UserId)
            .OrderBy(note => note.SortOrder)
            .Select(note => note.Value)
            .ToListAsync(cancellationToken);

        var emergencyContacts = await dbContext.EmergencyContacts
            .AsNoTracking()
            .Where(contact => contact.UserId == link.UserId)
            .OrderBy(contact => contact.SortOrder)
            .Select(contact => new PublicEmergencyContactResponse(
                contact.Name,
                contact.Phone,
                contact.Relationship
            ))
            .ToListAsync(cancellationToken);

        var response = new PublicEmergencyProfileResponse(
            profile.FullName,
            profile.BloodGroup,
            allergies,
            emergencyContacts,
            profile.Address,
            medicalNotes
        );

        return PublicProfileAccessResult.Ok(response);
    }
}

public enum PublicProfileAccessStatus
{
    Ok,
    Forbidden,
    NotFound
}

public sealed record PublicProfileAccessResult(
    PublicProfileAccessStatus Status,
    PublicEmergencyProfileResponse? Profile
)
{
    public static PublicProfileAccessResult Ok(PublicEmergencyProfileResponse profile) =>
        new(PublicProfileAccessStatus.Ok, profile);

    public static PublicProfileAccessResult Forbidden() =>
        new(PublicProfileAccessStatus.Forbidden, null);

    public static PublicProfileAccessResult NotFound() =>
        new(PublicProfileAccessStatus.NotFound, null);
}

public sealed record PublicEmergencyProfileResponse(
    string Name,
    string BloodGroup,
    IReadOnlyList<string> Allergies,
    IReadOnlyList<PublicEmergencyContactResponse> EmergencyContacts,
    string Address,
    IReadOnlyList<string> MedicalNotes
);

public sealed record PublicEmergencyContactResponse(
    string Name,
    string Phone,
    string? Relationship
);
