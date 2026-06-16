import { beforeEach, describe, expect, it, vi } from 'vitest';
import { AdminAuthError, assignRole, getStats, getUsers, revokeRole } from '../adminApi';

const mockFetch = vi.fn();
vi.stubGlobal('fetch', mockFetch);

const KEYS = {
  accessToken: 'helpid_admin_access_token',
  expiresAt: 'helpid_admin_expires_at',
};

function setValidToken() {
  sessionStorage.setItem(KEYS.accessToken, 'bearer-token');
  sessionStorage.setItem(KEYS.expiresAt, new Date(Date.now() + 900_000).toISOString());
}

function makeOkResponse(body: unknown, status = 200) {
  return {
    ok: status >= 200 && status < 300,
    status,
    json: () => Promise.resolve(body),
  };
}

function makeErrorResponse(status: number) {
  return { ok: false, status, json: () => Promise.resolve({ error: 'error' }) };
}

beforeEach(() => {
  sessionStorage.clear();
  mockFetch.mockReset();
});

describe('getStats()', () => {
  it('parses all 4 stat fields correctly', async () => {
    setValidToken();
    mockFetch.mockResolvedValueOnce(
      makeOkResponse({ totalUsers: 42, totalProfiles: 38, totalPublicLinks: 15, auditEventsLast7Days: 127 }),
    );

    const stats = await getStats();
    expect(stats.totalUsers).toBe(42);
    expect(stats.totalProfiles).toBe(38);
    expect(stats.totalPublicLinks).toBe(15);
    expect(stats.auditEventsLast7Days).toBe(127);
  });

  it('throws AdminAuthError when no token in sessionStorage', async () => {
    await expect(getStats()).rejects.toBeInstanceOf(AdminAuthError);
    expect(mockFetch).not.toHaveBeenCalled();
  });

  it('throws AdminAuthError on 401 response', async () => {
    setValidToken();
    mockFetch.mockResolvedValueOnce(makeErrorResponse(401));
    await expect(getStats()).rejects.toBeInstanceOf(AdminAuthError);
  });

  it('throws AdminAuthError on 403 response', async () => {
    setValidToken();
    mockFetch.mockResolvedValueOnce(makeErrorResponse(403));
    await expect(getStats()).rejects.toBeInstanceOf(AdminAuthError);
  });

  it('throws FETCH_ERROR on non-auth error response', async () => {
    setValidToken();
    mockFetch.mockResolvedValueOnce(makeErrorResponse(500));
    await expect(getStats()).rejects.toThrow('FETCH_ERROR');
  });
});

describe('getUsers()', () => {
  it('parses users array, page and totalCount correctly', async () => {
    setValidToken();
    const payload = {
      users: [
        { userId: 'u1', email: 'a@b.com', displayName: 'A', roles: ['User'], isLocked: false, createdAtUtc: '2026-01-01T00:00:00Z' },
      ],
      page: 2,
      pageSize: 20,
      totalCount: 45,
    };
    mockFetch.mockResolvedValueOnce(makeOkResponse(payload));

    const result = await getUsers(2, 20);
    expect(result.users).toHaveLength(1);
    expect(result.users[0].userId).toBe('u1');
    expect(result.page).toBe(2);
    expect(result.totalCount).toBe(45);
  });

  it('throws AdminAuthError on 401', async () => {
    setValidToken();
    mockFetch.mockResolvedValueOnce(makeErrorResponse(401));
    await expect(getUsers(1, 20)).rejects.toBeInstanceOf(AdminAuthError);
  });
});

describe('assignRole()', () => {
  it('resolves without throwing on 204', async () => {
    setValidToken();
    mockFetch.mockResolvedValueOnce({ ok: true, status: 204, json: () => Promise.resolve({}) });
    await expect(assignRole('user-1', 'role_admin')).resolves.toBeUndefined();
  });

  it('throws AdminAuthError on 403', async () => {
    setValidToken();
    mockFetch.mockResolvedValueOnce(makeErrorResponse(403));
    await expect(assignRole('user-1', 'role_admin')).rejects.toBeInstanceOf(AdminAuthError);
  });

  it('throws USER_NOT_FOUND on 404', async () => {
    setValidToken();
    mockFetch.mockResolvedValueOnce({ ok: false, status: 404, json: () => Promise.resolve({}) });
    await expect(assignRole('missing', 'role_admin')).rejects.toThrow('USER_NOT_FOUND');
  });
});

describe('revokeRole()', () => {
  it('resolves without throwing on 204', async () => {
    setValidToken();
    mockFetch.mockResolvedValueOnce({ ok: true, status: 204, json: () => Promise.resolve({}) });
    await expect(revokeRole('user-1', 'role_admin')).resolves.toBeUndefined();
  });

  it('throws USER_NOT_FOUND on 404', async () => {
    setValidToken();
    mockFetch.mockResolvedValueOnce({ ok: false, status: 404, json: () => Promise.resolve({}) });
    await expect(revokeRole('ghost', 'role_admin')).rejects.toThrow('USER_NOT_FOUND');
  });
});

describe('network error', () => {
  it('propagates TypeError (not AdminAuthError) on fetch failure', async () => {
    setValidToken();
    mockFetch.mockRejectedValueOnce(new TypeError('Failed to fetch'));

    const err = await getStats().catch((e) => e);
    expect(err).toBeInstanceOf(TypeError);
    expect(err).not.toBeInstanceOf(AdminAuthError);
  });
});
