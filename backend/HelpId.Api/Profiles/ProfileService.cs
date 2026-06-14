using HelpId.Api.Data;
using HelpId.Api.Data.Entities;
using Microsoft.EntityFrameworkCore;

namespace HelpId.Api.Profiles;

public enum ProfileOperationStatus { Ok, NotFound, ValidationFailed }

public sealed record ProfileOperationResult<T>(
    ProfileOperationStatus Status,
    T? Value = default,
    IReadOnlyDictionary<string, string[]>? ValidationErrors = null
)
{
    public static ProfileOperationResult<T> Ok(T value) =>
        new(ProfileOperationStatus.Ok, value);

    public static ProfileOperationResult<T> NotFound() =>
        new(ProfileOperationStatus.NotFound);

    public static ProfileOperationResult<T> ValidationFailed(
        IReadOnlyDictionary<string, string[]> errors
    ) => new(ProfileOperationStatus.ValidationFailed, ValidationErrors: errors);
}

public interface IProfileService
{
    Task<ProfileOperationResult<ProfileDto>> GetProfileAsync(
        string userId,
        CancellationToken cancellationToken = default
    );

    Task<ProfileOperationResult<ProfileDto>> UpdateProfileAsync(
        string userId,
        UpdateProfileRequest request,
        CancellationToken cancellationToken = default
    );
}

public sealed class ProfileService(
    HelpIdDbContext dbContext,
    IProfileRequestValidator validator
) : IProfileService
{
    public async Task<ProfileOperationResult<ProfileDto>> GetProfileAsync(
        string userId,
        CancellationToken cancellationToken = default
    )
    {
        var dto = await LoadProfileAsync(userId, cancellationToken);
        return dto is not null
            ? ProfileOperationResult<ProfileDto>.Ok(dto)
            : ProfileOperationResult<ProfileDto>.NotFound();
    }

    public async Task<ProfileOperationResult<ProfileDto>> UpdateProfileAsync(
        string userId,
        UpdateProfileRequest request,
        CancellationToken cancellationToken = default
    )
    {
        var validationErrors = validator.ValidateUpdate(request);
        if (validationErrors.Count > 0)
        {
            return ProfileOperationResult<ProfileDto>.ValidationFailed(validationErrors);
        }

        var profile = await dbContext.UserProfiles
            .SingleOrDefaultAsync(p => p.UserId == userId, cancellationToken);

        if (profile is null)
        {
            return ProfileOperationResult<ProfileDto>.NotFound();
        }

        var now = DateTimeOffset.UtcNow;

        if (request.Name is not null) profile.FullName = request.Name.Trim();
        if (request.BloodGroup is not null) profile.BloodGroup = request.BloodGroup.Trim();
        if (request.Address is not null) profile.Address = request.Address.Trim();
        if (request.Language is not null) profile.Language = request.Language;
        profile.UpdatedAtUtc = now;
        profile.LastUpdatedUtc = now;

        if (request.Allergies is not null)
        {
            var existing = await dbContext.ProfileAllergies
                .Where(a => a.UserId == userId)
                .ToListAsync(cancellationToken);
            dbContext.ProfileAllergies.RemoveRange(existing);

            for (var i = 0; i < request.Allergies.Count; i++)
            {
                dbContext.ProfileAllergies.Add(new ProfileAllergy
                {
                    Id = Guid.NewGuid().ToString("N"),
                    UserId = userId,
                    Value = request.Allergies[i].Trim(),
                    SortOrder = i,
                    CreatedAtUtc = now,
                    UpdatedAtUtc = now
                });
            }
        }

        if (request.MedicalNotes is not null)
        {
            var existing = await dbContext.MedicalNotes
                .Where(n => n.UserId == userId)
                .ToListAsync(cancellationToken);
            dbContext.MedicalNotes.RemoveRange(existing);

            for (var i = 0; i < request.MedicalNotes.Count; i++)
            {
                dbContext.MedicalNotes.Add(new MedicalNote
                {
                    Id = Guid.NewGuid().ToString("N"),
                    UserId = userId,
                    Value = request.MedicalNotes[i].Trim(),
                    SortOrder = i,
                    CreatedAtUtc = now,
                    UpdatedAtUtc = now
                });
            }
        }

        if (request.EmergencyContacts is not null)
        {
            var existing = await dbContext.EmergencyContacts
                .Where(c => c.UserId == userId)
                .ToListAsync(cancellationToken);
            dbContext.EmergencyContacts.RemoveRange(existing);

            var sortOrder = 0;
            foreach (var contact in request.EmergencyContacts)
            {
                if (string.IsNullOrWhiteSpace(contact.Name) || string.IsNullOrWhiteSpace(contact.Phone))
                    continue;

                dbContext.EmergencyContacts.Add(new EmergencyContact
                {
                    Id = Guid.NewGuid().ToString("N"),
                    UserId = userId,
                    Name = contact.Name.Trim(),
                    Phone = contact.Phone.Trim(),
                    Relationship = string.IsNullOrWhiteSpace(contact.Relationship)
                        ? null
                        : contact.Relationship.Trim(),
                    SortOrder = sortOrder++,
                    CreatedAtUtc = now,
                    UpdatedAtUtc = now
                });
            }
        }

        await dbContext.SaveChangesAsync(cancellationToken);

        var updated = await LoadProfileAsync(userId, cancellationToken);
        return updated is not null
            ? ProfileOperationResult<ProfileDto>.Ok(updated)
            : ProfileOperationResult<ProfileDto>.NotFound();
    }

    private async Task<ProfileDto?> LoadProfileAsync(string userId, CancellationToken cancellationToken)
    {
        var profile = await dbContext.UserProfiles
            .AsNoTracking()
            .Where(p => p.UserId == userId)
            .Select(p => new
            {
                p.FullName, p.BloodGroup, p.Address, p.Language, p.LastUpdatedUtc
            })
            .SingleOrDefaultAsync(cancellationToken);

        if (profile is null) return null;

        var allergies = await dbContext.ProfileAllergies
            .AsNoTracking()
            .Where(a => a.UserId == userId)
            .OrderBy(a => a.SortOrder)
            .Select(a => a.Value)
            .ToListAsync(cancellationToken);

        var medicalNotes = await dbContext.MedicalNotes
            .AsNoTracking()
            .Where(n => n.UserId == userId)
            .OrderBy(n => n.SortOrder)
            .Select(n => n.Value)
            .ToListAsync(cancellationToken);

        var emergencyContacts = await dbContext.EmergencyContacts
            .AsNoTracking()
            .Where(c => c.UserId == userId)
            .OrderBy(c => c.SortOrder)
            .Select(c => new EmergencyContactDto(c.Id, c.Name, c.Phone, c.Relationship))
            .ToListAsync(cancellationToken);

        return new ProfileDto(
            userId,
            profile.FullName,
            profile.BloodGroup,
            profile.Address,
            profile.Language,
            allergies,
            medicalNotes,
            emergencyContacts,
            profile.LastUpdatedUtc.ToUnixTimeMilliseconds()
        );
    }
}
