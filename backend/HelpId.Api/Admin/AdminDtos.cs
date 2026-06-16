namespace HelpId.Api.Admin;

public sealed record AdminStatsDto(
    int TotalUsers,
    int TotalProfiles,
    int TotalPublicLinks,
    int AuditEventsLast7Days
);

public sealed record AdminUserDto(
    string UserId,
    string Email,
    string? DisplayName,
    IReadOnlyList<string> Roles,
    bool IsLocked,
    DateTimeOffset CreatedAtUtc
);

public sealed record AdminUsersPageDto(
    IReadOnlyList<AdminUserDto> Users,
    int Page,
    int PageSize,
    int TotalCount
);

public enum AdminOperationStatus
{
    Ok,
    NotFound,
    BadRequest,
    Forbidden
}

public sealed record AdminOperationResult(
    AdminOperationStatus Status,
    string? ErrorMessage = null
);
