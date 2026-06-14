using HelpId.Api.Data.Entities;
using HelpId.Api.Data.Schema;
using HelpId.Api.Security;
using Microsoft.EntityFrameworkCore;

namespace HelpId.Api.Data;

public sealed class HelpIdDbContext(DbContextOptions<HelpIdDbContext> options) : DbContext(options)
{
    public DbSet<User> Users => Set<User>();
    public DbSet<RefreshToken> RefreshTokens => Set<RefreshToken>();
    public DbSet<UserProfile> UserProfiles => Set<UserProfile>();
    public DbSet<ProfileAllergy> ProfileAllergies => Set<ProfileAllergy>();
    public DbSet<MedicalNote> MedicalNotes => Set<MedicalNote>();
    public DbSet<EmergencyContact> EmergencyContacts => Set<EmergencyContact>();
    public DbSet<PublicProfileLink> PublicProfileLinks => Set<PublicProfileLink>();
    public DbSet<AuditEvent> AuditEvents => Set<AuditEvent>();
    public DbSet<Role> Roles => Set<Role>();
    public DbSet<Permission> Permissions => Set<Permission>();
    public DbSet<UserRole> UserRoles => Set<UserRole>();
    public DbSet<RolePermission> RolePermissions => Set<RolePermission>();

    protected override void OnModelCreating(ModelBuilder modelBuilder)
    {
        base.OnModelCreating(modelBuilder);

        ConfigureUsers(modelBuilder);
        ConfigureRefreshTokens(modelBuilder);
        ConfigureUserProfiles(modelBuilder);
        ConfigureProfileAllergies(modelBuilder);
        ConfigureMedicalNotes(modelBuilder);
        ConfigureEmergencyContacts(modelBuilder);
        ConfigurePublicProfileLinks(modelBuilder);
        ConfigureAuditEvents(modelBuilder);
        ConfigureRoles(modelBuilder);
        ConfigurePermissions(modelBuilder);
        ConfigureUserRoles(modelBuilder);
        ConfigureRolePermissions(modelBuilder);
        SeedAuthorizationData(modelBuilder);
    }

    private static void ConfigureUsers(ModelBuilder modelBuilder)
    {
        modelBuilder.Entity<User>(entity =>
        {
            entity.ToTable("Users");
            entity.HasKey(user => user.Id);

            entity.Property(user => user.Id)
                .HasMaxLength(64)
                .ValueGeneratedNever();

            entity.Property(user => user.Email)
                .IsRequired()
                .HasMaxLength(SchemaConstants.EmailMaxLength);

            entity.Property(user => user.NormalizedEmail)
                .IsRequired()
                .HasMaxLength(SchemaConstants.EmailMaxLength);

            entity.HasIndex(user => user.NormalizedEmail)
                .IsUnique()
                .HasDatabaseName("UX_Users_NormalizedEmail");

            entity.Property(user => user.PasswordHash)
                .IsRequired()
                .HasMaxLength(SchemaConstants.PasswordHashMaxLength);

            entity.Property(user => user.DisplayName)
                .HasMaxLength(SchemaConstants.DisplayNameMaxLength);

            entity.Property(user => user.PhoneNumber)
                .HasMaxLength(SchemaConstants.PhoneMaxLength);

            entity.Property(user => user.IsEmailVerified)
                .IsRequired()
                .HasDefaultValue(false);

            entity.Property(user => user.FailedLoginCount)
                .IsRequired()
                .HasDefaultValue(0);

            entity.Property(user => user.SecurityStamp)
                .IsRequired()
                .HasMaxLength(SchemaConstants.SecurityStampMaxLength);

            entity.Property(user => user.CreatedAtUtc)
                .IsRequired();

            entity.Property(user => user.UpdatedAtUtc)
                .IsRequired();

            entity.HasIndex(user => user.DeletedAtUtc)
                .HasDatabaseName("IX_Users_DeletedAtUtc");
        });
    }

    private static void ConfigureRefreshTokens(ModelBuilder modelBuilder)
    {
        modelBuilder.Entity<RefreshToken>(entity =>
        {
            entity.ToTable("RefreshTokens");
            entity.HasKey(token => token.Id);

            entity.Property(token => token.Id)
                .HasMaxLength(64)
                .ValueGeneratedNever();

            entity.Property(token => token.UserId)
                .IsRequired()
                .HasMaxLength(64);

            entity.Property(token => token.TokenHash)
                .IsRequired()
                .HasMaxLength(SchemaConstants.TokenHashMaxLength);

            entity.HasIndex(token => token.TokenHash)
                .IsUnique()
                .HasDatabaseName("UX_RefreshTokens_TokenHash");

            entity.Property(token => token.TokenFamilyId)
                .IsRequired()
                .HasMaxLength(SchemaConstants.TokenFamilyIdMaxLength);

            entity.HasIndex(token => token.UserId)
                .HasDatabaseName("IX_RefreshTokens_UserId");

            entity.HasIndex(token => token.TokenFamilyId)
                .HasDatabaseName("IX_RefreshTokens_TokenFamilyId");

            entity.HasIndex(token => token.ExpiresAtUtc)
                .HasDatabaseName("IX_RefreshTokens_ExpiresAtUtc");

            entity.Property(token => token.CreatedAtUtc)
                .IsRequired();

            entity.Property(token => token.ExpiresAtUtc)
                .IsRequired();

            entity.Property(token => token.ReplacedByTokenId)
                .HasMaxLength(64);

            entity.Property(token => token.DeviceName)
                .HasMaxLength(SchemaConstants.DeviceNameMaxLength);

            entity.Property(token => token.UserAgentHash)
                .HasMaxLength(SchemaConstants.HashMaxLength);

            entity.Property(token => token.CreatedByIpHash)
                .HasMaxLength(SchemaConstants.HashMaxLength);

            entity.HasOne(token => token.User)
                .WithMany(user => user.RefreshTokens)
                .HasForeignKey(token => token.UserId)
                .OnDelete(DeleteBehavior.Cascade);

            entity.HasOne(token => token.ReplacedByToken)
                .WithMany(token => token.ReplacedTokens)
                .HasForeignKey(token => token.ReplacedByTokenId)
                .OnDelete(DeleteBehavior.SetNull);
        });
    }

    private static void ConfigureUserProfiles(ModelBuilder modelBuilder)
    {
        modelBuilder.Entity<UserProfile>(entity =>
        {
            entity.ToTable("UserProfiles");
            entity.HasKey(profile => profile.UserId);

            entity.Property(profile => profile.UserId)
                .HasMaxLength(64)
                .ValueGeneratedNever();

            entity.Property(profile => profile.FullName)
                .IsRequired()
                .HasMaxLength(SchemaConstants.FullNameMaxLength)
                .HasDefaultValue(string.Empty);

            entity.Property(profile => profile.BloodGroup)
                .IsRequired()
                .HasMaxLength(SchemaConstants.BloodGroupMaxLength)
                .HasDefaultValue(string.Empty);

            entity.Property(profile => profile.Address)
                .IsRequired()
                .HasMaxLength(SchemaConstants.AddressMaxLength)
                .HasDefaultValue(string.Empty);

            entity.Property(profile => profile.Language)
                .IsRequired()
                .HasMaxLength(SchemaConstants.LanguageMaxLength)
                .HasDefaultValue("en");

            entity.Property(profile => profile.CreatedAtUtc)
                .IsRequired();

            entity.Property(profile => profile.UpdatedAtUtc)
                .IsRequired();

            entity.Property(profile => profile.LastUpdatedUtc)
                .IsRequired();

            entity.HasOne(profile => profile.User)
                .WithOne(user => user.Profile)
                .HasForeignKey<UserProfile>(profile => profile.UserId)
                .OnDelete(DeleteBehavior.Cascade);
        });
    }

    private static void ConfigureProfileAllergies(ModelBuilder modelBuilder)
    {
        modelBuilder.Entity<ProfileAllergy>(entity =>
        {
            entity.ToTable("ProfileAllergies");
            entity.HasKey(allergy => allergy.Id);

            entity.Property(allergy => allergy.Id)
                .HasMaxLength(64)
                .ValueGeneratedNever();

            entity.Property(allergy => allergy.UserId)
                .IsRequired()
                .HasMaxLength(64);

            entity.Property(allergy => allergy.Value)
                .IsRequired()
                .HasMaxLength(SchemaConstants.AllergyValueMaxLength);

            entity.Property(allergy => allergy.SortOrder)
                .IsRequired()
                .HasDefaultValue(0);

            entity.Property(allergy => allergy.CreatedAtUtc)
                .IsRequired();

            entity.Property(allergy => allergy.UpdatedAtUtc)
                .IsRequired();

            entity.HasIndex(allergy => new { allergy.UserId, allergy.SortOrder })
                .HasDatabaseName("IX_ProfileAllergies_UserId_SortOrder");

            entity.HasOne(allergy => allergy.User)
                .WithMany(user => user.Allergies)
                .HasForeignKey(allergy => allergy.UserId)
                .OnDelete(DeleteBehavior.Cascade);
        });
    }

    private static void ConfigureMedicalNotes(ModelBuilder modelBuilder)
    {
        modelBuilder.Entity<MedicalNote>(entity =>
        {
            entity.ToTable("MedicalNotes");
            entity.HasKey(note => note.Id);

            entity.Property(note => note.Id)
                .HasMaxLength(64)
                .ValueGeneratedNever();

            entity.Property(note => note.UserId)
                .IsRequired()
                .HasMaxLength(64);

            entity.Property(note => note.Value)
                .IsRequired()
                .HasMaxLength(SchemaConstants.MedicalNoteValueMaxLength);

            entity.Property(note => note.SortOrder)
                .IsRequired()
                .HasDefaultValue(0);

            entity.Property(note => note.CreatedAtUtc)
                .IsRequired();

            entity.Property(note => note.UpdatedAtUtc)
                .IsRequired();

            entity.HasIndex(note => new { note.UserId, note.SortOrder })
                .HasDatabaseName("IX_MedicalNotes_UserId_SortOrder");

            entity.HasOne(note => note.User)
                .WithMany(user => user.MedicalNotes)
                .HasForeignKey(note => note.UserId)
                .OnDelete(DeleteBehavior.Cascade);
        });
    }

    private static void ConfigureEmergencyContacts(ModelBuilder modelBuilder)
    {
        modelBuilder.Entity<EmergencyContact>(entity =>
        {
            entity.ToTable("EmergencyContacts");
            entity.HasKey(contact => contact.Id);

            entity.Property(contact => contact.Id)
                .HasMaxLength(64)
                .ValueGeneratedNever();

            entity.Property(contact => contact.UserId)
                .IsRequired()
                .HasMaxLength(64);

            entity.Property(contact => contact.Name)
                .IsRequired()
                .HasMaxLength(SchemaConstants.FullNameMaxLength);

            entity.Property(contact => contact.Phone)
                .IsRequired()
                .HasMaxLength(SchemaConstants.PhoneMaxLength);

            entity.Property(contact => contact.Relationship)
                .HasMaxLength(SchemaConstants.RelationshipMaxLength);

            entity.Property(contact => contact.SortOrder)
                .IsRequired()
                .HasDefaultValue(0);

            entity.Property(contact => contact.CreatedAtUtc)
                .IsRequired();

            entity.Property(contact => contact.UpdatedAtUtc)
                .IsRequired();

            entity.HasIndex(contact => new { contact.UserId, contact.SortOrder })
                .HasDatabaseName("IX_EmergencyContacts_UserId_SortOrder");

            entity.HasOne(contact => contact.User)
                .WithMany(user => user.EmergencyContacts)
                .HasForeignKey(contact => contact.UserId)
                .OnDelete(DeleteBehavior.Cascade);
        });
    }

    private static void ConfigurePublicProfileLinks(ModelBuilder modelBuilder)
    {
        modelBuilder.Entity<PublicProfileLink>(entity =>
        {
            entity.ToTable("PublicProfileLinks");
            entity.HasKey(link => link.PublicKey);

            entity.Property(link => link.PublicKey)
                .HasMaxLength(SchemaConstants.PublicKeyMaxLength)
                .ValueGeneratedNever();

            entity.Property(link => link.UserId)
                .IsRequired()
                .HasMaxLength(64);

            entity.Property(link => link.CreatedAtUtc)
                .IsRequired();

            entity.Property(link => link.UpdatedAtUtc)
                .IsRequired();

            entity.HasIndex(link => link.UserId)
                .HasDatabaseName("IX_PublicProfileLinks_UserId");

            entity.HasOne(link => link.User)
                .WithMany(user => user.PublicProfileLinks)
                .HasForeignKey(link => link.UserId)
                .OnDelete(DeleteBehavior.Cascade);
        });
    }

    private static void ConfigureAuditEvents(ModelBuilder modelBuilder)
    {
        modelBuilder.Entity<AuditEvent>(entity =>
        {
            entity.ToTable("AuditEvents");
            entity.HasKey(audit => audit.Id);

            entity.Property(audit => audit.Id)
                .HasMaxLength(64)
                .ValueGeneratedNever();

            entity.Property(audit => audit.UserId)
                .HasMaxLength(64);

            entity.Property(audit => audit.EventType)
                .IsRequired()
                .HasMaxLength(SchemaConstants.AuditEventTypeMaxLength);

            entity.Property(audit => audit.ReasonCode)
                .HasMaxLength(SchemaConstants.AuditReasonCodeMaxLength);

            entity.Property(audit => audit.Success)
                .IsRequired();

            entity.Property(audit => audit.CreatedAtUtc)
                .IsRequired();

            entity.Property(audit => audit.IpHash)
                .HasMaxLength(SchemaConstants.HashMaxLength);

            entity.Property(audit => audit.UserAgentHash)
                .HasMaxLength(SchemaConstants.HashMaxLength);

            entity.HasIndex(audit => new { audit.UserId, audit.CreatedAtUtc })
                .HasDatabaseName("IX_AuditEvents_UserId_CreatedAtUtc");

            entity.HasIndex(audit => new { audit.EventType, audit.CreatedAtUtc })
                .HasDatabaseName("IX_AuditEvents_EventType_CreatedAtUtc");

            entity.HasOne(audit => audit.User)
                .WithMany(user => user.AuditEvents)
                .HasForeignKey(audit => audit.UserId)
                .OnDelete(DeleteBehavior.SetNull);
        });
    }

    private static void ConfigureRoles(ModelBuilder modelBuilder)
    {
        modelBuilder.Entity<Role>(entity =>
        {
            entity.ToTable("Roles");
            entity.HasKey(role => role.Id);

            entity.Property(role => role.Id)
                .HasMaxLength(64)
                .ValueGeneratedNever();

            entity.Property(role => role.Name)
                .IsRequired()
                .HasMaxLength(SchemaConstants.RoleNameMaxLength);

            entity.Property(role => role.NormalizedName)
                .IsRequired()
                .HasMaxLength(SchemaConstants.RoleNameMaxLength);

            entity.HasIndex(role => role.NormalizedName)
                .IsUnique()
                .HasDatabaseName("UX_Roles_NormalizedName");

            entity.Property(role => role.CreatedAtUtc)
                .IsRequired();
        });
    }

    private static void ConfigurePermissions(ModelBuilder modelBuilder)
    {
        modelBuilder.Entity<Permission>(entity =>
        {
            entity.ToTable("Permissions");
            entity.HasKey(permission => permission.Id);

            entity.Property(permission => permission.Id)
                .HasMaxLength(64)
                .ValueGeneratedNever();

            entity.Property(permission => permission.Code)
                .IsRequired()
                .HasMaxLength(SchemaConstants.PermissionCodeMaxLength);

            entity.HasIndex(permission => permission.Code)
                .IsUnique()
                .HasDatabaseName("UX_Permissions_Code");

            entity.Property(permission => permission.Description)
                .HasMaxLength(SchemaConstants.PermissionDescriptionMaxLength);

            entity.Property(permission => permission.CreatedAtUtc)
                .IsRequired();
        });
    }

    private static void ConfigureUserRoles(ModelBuilder modelBuilder)
    {
        modelBuilder.Entity<UserRole>(entity =>
        {
            entity.ToTable("UserRoles");
            entity.HasKey(userRole => new { userRole.UserId, userRole.RoleId });

            entity.Property(userRole => userRole.UserId)
                .HasMaxLength(64)
                .ValueGeneratedNever();

            entity.Property(userRole => userRole.RoleId)
                .HasMaxLength(64)
                .ValueGeneratedNever();

            entity.Property(userRole => userRole.AssignedAtUtc)
                .IsRequired();

            entity.HasIndex(userRole => userRole.RoleId)
                .HasDatabaseName("IX_UserRoles_RoleId");

            entity.HasOne(userRole => userRole.User)
                .WithMany(user => user.UserRoles)
                .HasForeignKey(userRole => userRole.UserId)
                .OnDelete(DeleteBehavior.Cascade);

            entity.HasOne(userRole => userRole.Role)
                .WithMany(role => role.UserRoles)
                .HasForeignKey(userRole => userRole.RoleId)
                .OnDelete(DeleteBehavior.Cascade);
        });
    }

    private static void ConfigureRolePermissions(ModelBuilder modelBuilder)
    {
        modelBuilder.Entity<RolePermission>(entity =>
        {
            entity.ToTable("RolePermissions");
            entity.HasKey(rolePermission => new { rolePermission.RoleId, rolePermission.PermissionId });

            entity.Property(rolePermission => rolePermission.RoleId)
                .HasMaxLength(64)
                .ValueGeneratedNever();

            entity.Property(rolePermission => rolePermission.PermissionId)
                .HasMaxLength(64)
                .ValueGeneratedNever();

            entity.Property(rolePermission => rolePermission.GrantedAtUtc)
                .IsRequired();

            entity.HasIndex(rolePermission => rolePermission.PermissionId)
                .HasDatabaseName("IX_RolePermissions_PermissionId");

            entity.HasOne(rolePermission => rolePermission.Role)
                .WithMany(role => role.RolePermissions)
                .HasForeignKey(rolePermission => rolePermission.RoleId)
                .OnDelete(DeleteBehavior.Cascade);

            entity.HasOne(rolePermission => rolePermission.Permission)
                .WithMany(permission => permission.RolePermissions)
                .HasForeignKey(rolePermission => rolePermission.PermissionId)
                .OnDelete(DeleteBehavior.Cascade);
        });
    }

    private static void SeedAuthorizationData(ModelBuilder modelBuilder)
    {
        var seedCreatedAt = HelpIdAuthorizationDefaults.SeedCreatedAtUtc;

        modelBuilder.Entity<Role>().HasData(
            new Role
            {
                Id = HelpIdAuthorizationDefaults.Roles.UserId,
                Name = HelpIdAuthorizationDefaults.Roles.UserName,
                NormalizedName = HelpIdAuthorizationDefaults.Roles.UserNormalizedName,
                CreatedAtUtc = seedCreatedAt
            },
            new Role
            {
                Id = HelpIdAuthorizationDefaults.Roles.AdminId,
                Name = HelpIdAuthorizationDefaults.Roles.AdminName,
                NormalizedName = HelpIdAuthorizationDefaults.Roles.AdminNormalizedName,
                CreatedAtUtc = seedCreatedAt
            }
        );

        modelBuilder.Entity<Permission>().HasData(
            new Permission
            {
                Id = HelpIdAuthorizationDefaults.Permissions.ProfileReadSelfId,
                Code = HelpIdAuthorizationDefaults.Permissions.ProfileReadSelf,
                Description = "Read own emergency profile",
                CreatedAtUtc = seedCreatedAt
            },
            new Permission
            {
                Id = HelpIdAuthorizationDefaults.Permissions.ProfileWriteSelfId,
                Code = HelpIdAuthorizationDefaults.Permissions.ProfileWriteSelf,
                Description = "Update own emergency profile",
                CreatedAtUtc = seedCreatedAt
            },
            new Permission
            {
                Id = HelpIdAuthorizationDefaults.Permissions.EmergencyLinkMintSelfId,
                Code = HelpIdAuthorizationDefaults.Permissions.EmergencyLinkMintSelf,
                Description = "Mint own public emergency profile link",
                CreatedAtUtc = seedCreatedAt
            },
            new Permission
            {
                Id = HelpIdAuthorizationDefaults.Permissions.AuthSessionSelfId,
                Code = HelpIdAuthorizationDefaults.Permissions.AuthSessionSelf,
                Description = "Manage own auth session",
                CreatedAtUtc = seedCreatedAt
            },
            new Permission
            {
                Id = HelpIdAuthorizationDefaults.Permissions.AdminMetadataReadId,
                Code = HelpIdAuthorizationDefaults.Permissions.AdminMetadataRead,
                Description = "Read admin metadata",
                CreatedAtUtc = seedCreatedAt
            }
        );

        modelBuilder.Entity<RolePermission>().HasData(
            new RolePermission
            {
                RoleId = HelpIdAuthorizationDefaults.Roles.UserId,
                PermissionId = HelpIdAuthorizationDefaults.Permissions.ProfileReadSelfId,
                GrantedAtUtc = seedCreatedAt
            },
            new RolePermission
            {
                RoleId = HelpIdAuthorizationDefaults.Roles.UserId,
                PermissionId = HelpIdAuthorizationDefaults.Permissions.ProfileWriteSelfId,
                GrantedAtUtc = seedCreatedAt
            },
            new RolePermission
            {
                RoleId = HelpIdAuthorizationDefaults.Roles.UserId,
                PermissionId = HelpIdAuthorizationDefaults.Permissions.EmergencyLinkMintSelfId,
                GrantedAtUtc = seedCreatedAt
            },
            new RolePermission
            {
                RoleId = HelpIdAuthorizationDefaults.Roles.UserId,
                PermissionId = HelpIdAuthorizationDefaults.Permissions.AuthSessionSelfId,
                GrantedAtUtc = seedCreatedAt
            },
            new RolePermission
            {
                RoleId = HelpIdAuthorizationDefaults.Roles.AdminId,
                PermissionId = HelpIdAuthorizationDefaults.Permissions.ProfileReadSelfId,
                GrantedAtUtc = seedCreatedAt
            },
            new RolePermission
            {
                RoleId = HelpIdAuthorizationDefaults.Roles.AdminId,
                PermissionId = HelpIdAuthorizationDefaults.Permissions.ProfileWriteSelfId,
                GrantedAtUtc = seedCreatedAt
            },
            new RolePermission
            {
                RoleId = HelpIdAuthorizationDefaults.Roles.AdminId,
                PermissionId = HelpIdAuthorizationDefaults.Permissions.EmergencyLinkMintSelfId,
                GrantedAtUtc = seedCreatedAt
            },
            new RolePermission
            {
                RoleId = HelpIdAuthorizationDefaults.Roles.AdminId,
                PermissionId = HelpIdAuthorizationDefaults.Permissions.AuthSessionSelfId,
                GrantedAtUtc = seedCreatedAt
            },
            new RolePermission
            {
                RoleId = HelpIdAuthorizationDefaults.Roles.AdminId,
                PermissionId = HelpIdAuthorizationDefaults.Permissions.AdminMetadataReadId,
                GrantedAtUtc = seedCreatedAt
            }
        );
    }

}
