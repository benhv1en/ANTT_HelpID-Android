namespace HelpId.Api.Profiles;

public sealed record UpdateProfileRequest(
    string? Name,
    string? BloodGroup,
    string? Address,
    string? Language,
    IReadOnlyList<string>? Allergies,
    IReadOnlyList<string>? MedicalNotes,
    IReadOnlyList<EmergencyContactRequest>? EmergencyContacts
);

public sealed record EmergencyContactRequest(
    string? Name,
    string? Phone,
    string? Relationship
);

public sealed record ProfileDto(
    string UserId,
    string Name,
    string BloodGroup,
    string Address,
    string Language,
    IReadOnlyList<string> Allergies,
    IReadOnlyList<string> MedicalNotes,
    IReadOnlyList<EmergencyContactDto> EmergencyContacts,
    long LastUpdated
);

public sealed record EmergencyContactDto(
    string Id,
    string Name,
    string Phone,
    string? Relationship
);
