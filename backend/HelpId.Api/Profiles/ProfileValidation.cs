using System.Text.RegularExpressions;
using HelpId.Api.Data.Schema;

namespace HelpId.Api.Profiles;

public interface IProfileRequestValidator
{
    IReadOnlyDictionary<string, string[]> ValidateUpdate(UpdateProfileRequest request);
}

public sealed class ProfileRequestValidator : IProfileRequestValidator
{
    private static readonly HashSet<string> ValidBloodGroups = new(StringComparer.Ordinal)
        { "A+", "A-", "B+", "B-", "AB+", "AB-", "O+", "O-" };

    private static readonly HashSet<string> SupportedLanguages = new(StringComparer.Ordinal)
        { "en", "es", "hi", "fr", "de", "vi" };

    private static readonly Regex E164Regex =
        new(@"^\+[1-9]\d{1,14}$", RegexOptions.Compiled, TimeSpan.FromMilliseconds(100));

    public IReadOnlyDictionary<string, string[]> ValidateUpdate(UpdateProfileRequest request)
    {
        var errors = new Dictionary<string, string[]>(StringComparer.Ordinal);

        if (request.Name is { Length: > SchemaConstants.FullNameMaxLength })
        {
            errors["name"] = ["name.too_long"];
        }

        if (!string.IsNullOrEmpty(request.BloodGroup) && !ValidBloodGroups.Contains(request.BloodGroup))
        {
            errors["bloodGroup"] = ["blood_group.invalid"];
        }

        if (request.Address is { Length: > SchemaConstants.AddressMaxLength })
        {
            errors["address"] = ["address.too_long"];
        }

        if (!string.IsNullOrEmpty(request.Language) && !SupportedLanguages.Contains(request.Language))
        {
            errors["language"] = ["language.unsupported"];
        }

        ValidateStringList(errors, request.Allergies, "allergies", "allergies.too_many", 20,
            SchemaConstants.AllergyValueMaxLength, "allergy.too_long");

        ValidateStringList(errors, request.MedicalNotes, "medicalNotes", "medical_notes.too_many", 50,
            SchemaConstants.MedicalNoteValueMaxLength, "medical_note.too_long");

        ValidateContacts(errors, request.EmergencyContacts);

        return errors;
    }

    private static void ValidateStringList(
        Dictionary<string, string[]> errors,
        IReadOnlyList<string>? list,
        string fieldName,
        string tooManyCode,
        int maxCount,
        int maxItemLength,
        string tooLongCode
    )
    {
        if (list is null) return;

        if (list.Count > maxCount)
        {
            errors[fieldName] = [tooManyCode];
            return;
        }

        for (var i = 0; i < list.Count; i++)
        {
            if (list[i].Length > maxItemLength)
            {
                errors[$"{fieldName}[{i}]"] = [tooLongCode];
            }
        }
    }

    private static void ValidateContacts(
        Dictionary<string, string[]> errors,
        IReadOnlyList<EmergencyContactRequest>? contacts
    )
    {
        if (contacts is null) return;

        if (contacts.Count > 10)
        {
            errors["emergencyContacts"] = ["contacts.too_many"];
            return;
        }

        for (var i = 0; i < contacts.Count; i++)
        {
            var contact = contacts[i];
            var hasName = !string.IsNullOrWhiteSpace(contact.Name);
            var hasPhone = !string.IsNullOrWhiteSpace(contact.Phone);

            if (hasPhone && !hasName)
            {
                errors[$"emergencyContacts[{i}].name"] = ["contact_name.required"];
                continue;
            }

            if (hasName && !hasPhone)
            {
                errors[$"emergencyContacts[{i}].phone"] = ["contact_phone.required"];
                continue;
            }

            if (!hasName) continue;

            if (contact.Name!.Length > SchemaConstants.FullNameMaxLength)
            {
                errors[$"emergencyContacts[{i}].name"] = ["contact_name.too_long"];
            }

            if (!E164Regex.IsMatch(contact.Phone!))
            {
                errors[$"emergencyContacts[{i}].phone"] = ["contact_phone.invalid"];
            }
            else if (contact.Phone!.Length > SchemaConstants.PhoneMaxLength)
            {
                errors[$"emergencyContacts[{i}].phone"] = ["contact_phone.invalid"];
            }
        }
    }
}
