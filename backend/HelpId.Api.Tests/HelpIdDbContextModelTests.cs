using HelpId.Api.Data;
using HelpId.Api.Data.Entities;
using HelpId.Api.Data.Schema;
using Microsoft.Data.Sqlite;
using Microsoft.EntityFrameworkCore;
using Microsoft.EntityFrameworkCore.Metadata;
using Xunit;

namespace HelpId.Api.Tests;

public sealed class HelpIdDbContextModelTests
{
    [Fact]
    public void Model_contains_expected_auth_profile_and_audit_entities()
    {
        using var context = CreateContext();
        var model = context.Model;

        AssertTable<User>(model, "Users");
        AssertTable<RefreshToken>(model, "RefreshTokens");
        AssertTable<UserProfile>(model, "UserProfiles");
        AssertTable<ProfileAllergy>(model, "ProfileAllergies");
        AssertTable<MedicalNote>(model, "MedicalNotes");
        AssertTable<EmergencyContact>(model, "EmergencyContacts");
        AssertTable<PublicProfileLink>(model, "PublicProfileLinks");
        AssertTable<AuditEvent>(model, "AuditEvents");
    }

    [Fact]
    public void User_constraints_match_auth_contract()
    {
        using var context = CreateContext();
        var user = Entity<User>(context.Model);

        AssertMaxLength(user, nameof(User.Email), SchemaConstants.EmailMaxLength, required: true);
        AssertMaxLength(user, nameof(User.NormalizedEmail), SchemaConstants.EmailMaxLength, required: true);
        AssertMaxLength(user, nameof(User.PasswordHash), SchemaConstants.PasswordHashMaxLength, required: true);
        AssertMaxLength(user, nameof(User.DisplayName), SchemaConstants.DisplayNameMaxLength, required: false);
        AssertMaxLength(user, nameof(User.PhoneNumber), SchemaConstants.PhoneMaxLength, required: false);
        AssertMaxLength(user, nameof(User.SecurityStamp), SchemaConstants.SecurityStampMaxLength, required: true);

        var normalizedEmailIndex = FindIndex(user, nameof(User.NormalizedEmail));
        Assert.True(normalizedEmailIndex.IsUnique);
        Assert.Equal("UX_Users_NormalizedEmail", normalizedEmailIndex.GetDatabaseName());
    }

    [Fact]
    public void Refresh_token_constraints_match_token_contract()
    {
        using var context = CreateContext();
        var token = Entity<RefreshToken>(context.Model);

        AssertMaxLength(token, nameof(RefreshToken.TokenHash), SchemaConstants.TokenHashMaxLength, required: true);
        AssertMaxLength(token, nameof(RefreshToken.TokenFamilyId), SchemaConstants.TokenFamilyIdMaxLength, required: true);
        AssertMaxLength(token, nameof(RefreshToken.DeviceName), SchemaConstants.DeviceNameMaxLength, required: false);
        AssertMaxLength(token, nameof(RefreshToken.UserAgentHash), SchemaConstants.HashMaxLength, required: false);
        AssertMaxLength(token, nameof(RefreshToken.CreatedByIpHash), SchemaConstants.HashMaxLength, required: false);

        var tokenHashIndex = FindIndex(token, nameof(RefreshToken.TokenHash));
        Assert.True(tokenHashIndex.IsUnique);
        Assert.Equal("UX_RefreshTokens_TokenHash", tokenHashIndex.GetDatabaseName());

        AssertForeignKey<RefreshToken, User>(
            context.Model,
            nameof(RefreshToken.UserId),
            required: true,
            DeleteBehavior.Cascade
        );
        AssertForeignKey<RefreshToken, RefreshToken>(
            context.Model,
            nameof(RefreshToken.ReplacedByTokenId),
            required: false,
            DeleteBehavior.SetNull
        );
    }

    [Fact]
    public void Profile_owned_data_uses_user_foreign_keys_and_sort_indexes()
    {
        using var context = CreateContext();
        var model = context.Model;

        AssertForeignKey<UserProfile, User>(model, nameof(UserProfile.UserId), required: true, DeleteBehavior.Cascade);
        AssertForeignKey<ProfileAllergy, User>(model, nameof(ProfileAllergy.UserId), required: true, DeleteBehavior.Cascade);
        AssertForeignKey<MedicalNote, User>(model, nameof(MedicalNote.UserId), required: true, DeleteBehavior.Cascade);
        AssertForeignKey<EmergencyContact, User>(model, nameof(EmergencyContact.UserId), required: true, DeleteBehavior.Cascade);
        AssertForeignKey<PublicProfileLink, User>(model, nameof(PublicProfileLink.UserId), required: true, DeleteBehavior.Cascade);

        AssertHasIndex<ProfileAllergy>(model, nameof(ProfileAllergy.UserId), nameof(ProfileAllergy.SortOrder));
        AssertHasIndex<MedicalNote>(model, nameof(MedicalNote.UserId), nameof(MedicalNote.SortOrder));
        AssertHasIndex<EmergencyContact>(model, nameof(EmergencyContact.UserId), nameof(EmergencyContact.SortOrder));

        AssertMaxLength(Entity<UserProfile>(model), nameof(UserProfile.FullName), SchemaConstants.FullNameMaxLength, required: true);
        AssertMaxLength(Entity<UserProfile>(model), nameof(UserProfile.BloodGroup), SchemaConstants.BloodGroupMaxLength, required: true);
        AssertMaxLength(Entity<UserProfile>(model), nameof(UserProfile.Address), SchemaConstants.AddressMaxLength, required: true);
        AssertMaxLength(Entity<UserProfile>(model), nameof(UserProfile.Language), SchemaConstants.LanguageMaxLength, required: true);
        AssertMaxLength(Entity<ProfileAllergy>(model), nameof(ProfileAllergy.Value), SchemaConstants.AllergyValueMaxLength, required: true);
        AssertMaxLength(Entity<MedicalNote>(model), nameof(MedicalNote.Value), SchemaConstants.MedicalNoteValueMaxLength, required: true);
        AssertMaxLength(Entity<EmergencyContact>(model), nameof(EmergencyContact.Phone), SchemaConstants.PhoneMaxLength, required: true);
        AssertMaxLength(Entity<PublicProfileLink>(model), nameof(PublicProfileLink.PublicKey), SchemaConstants.PublicKeyMaxLength, required: true);
    }

    [Fact]
    public void Audit_events_keep_history_when_user_is_deleted()
    {
        using var context = CreateContext();
        var model = context.Model;

        AssertForeignKey<AuditEvent, User>(
            model,
            nameof(AuditEvent.UserId),
            required: false,
            DeleteBehavior.SetNull
        );
        AssertHasIndex<AuditEvent>(model, nameof(AuditEvent.UserId), nameof(AuditEvent.CreatedAtUtc));
        AssertHasIndex<AuditEvent>(model, nameof(AuditEvent.EventType), nameof(AuditEvent.CreatedAtUtc));
    }

    [Fact]
    public void Model_can_create_sqlite_schema()
    {
        using var connection = new SqliteConnection("Data Source=:memory:");
        connection.Open();

        var options = new DbContextOptionsBuilder<HelpIdDbContext>()
            .UseSqlite(connection)
            .Options;

        using var context = new HelpIdDbContext(options);

        Assert.True(context.Database.EnsureCreated());
    }

    private static HelpIdDbContext CreateContext()
    {
        var options = new DbContextOptionsBuilder<HelpIdDbContext>()
            .UseSqlite("Data Source=:memory:")
            .Options;

        return new HelpIdDbContext(options);
    }

    private static void AssertTable<TEntity>(IModel model, string expectedTableName)
    {
        var entity = Entity<TEntity>(model);
        Assert.Equal(expectedTableName, entity.GetTableName());
    }

    private static IEntityType Entity<TEntity>(IModel model)
    {
        return model.FindEntityType(typeof(TEntity))
            ?? throw new InvalidOperationException($"Entity {typeof(TEntity).Name} was not configured.");
    }

    private static void AssertMaxLength(
        IEntityType entity,
        string propertyName,
        int expectedLength,
        bool required
    )
    {
        var property = entity.FindProperty(propertyName)
            ?? throw new InvalidOperationException($"Property {propertyName} was not configured.");

        Assert.Equal(expectedLength, property.GetMaxLength());
        Assert.Equal(!required, property.IsNullable);
    }

    private static IIndex FindIndex(IEntityType entity, params string[] propertyNames)
    {
        return entity.GetIndexes().Single(index =>
            index.Properties.Select(property => property.Name).SequenceEqual(propertyNames)
        );
    }

    private static void AssertHasIndex<TEntity>(IModel model, params string[] propertyNames)
    {
        var entity = Entity<TEntity>(model);
        _ = FindIndex(entity, propertyNames);
    }

    private static void AssertForeignKey<TDependent, TPrincipal>(
        IModel model,
        string propertyName,
        bool required,
        DeleteBehavior deleteBehavior
    )
    {
        var dependent = Entity<TDependent>(model);
        var foreignKey = dependent.GetForeignKeys().Single(fk =>
            fk.PrincipalEntityType.ClrType == typeof(TPrincipal) &&
            fk.Properties.Select(property => property.Name).SequenceEqual(new[] { propertyName })
        );

        Assert.Equal(required, foreignKey.IsRequired);
        Assert.Equal(deleteBehavior, foreignKey.DeleteBehavior);
    }
}
