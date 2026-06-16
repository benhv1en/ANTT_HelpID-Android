import { beforeEach, describe, expect, it, vi } from 'vitest';
import { getAdminToken, getAdminUserId, isAdminLoggedIn, login, logout } from '../adminAuth';

const mockFetch = vi.fn();
vi.stubGlobal('fetch', mockFetch);

const KEYS = {
  accessToken: 'helpid_admin_access_token',
  refreshToken: 'helpid_admin_refresh_token',
  expiresAt: 'helpid_admin_expires_at',
  userId: 'helpid_admin_user_id',
};

const futureExpiry = () => new Date(Date.now() + 900_000).toISOString();
const pastExpiry = () => new Date(Date.now() - 1_000).toISOString();

function setValidSession() {
  sessionStorage.setItem(KEYS.accessToken, 'test-access-token');
  sessionStorage.setItem(KEYS.refreshToken, 'test-refresh-token');
  sessionStorage.setItem(KEYS.expiresAt, futureExpiry());
  sessionStorage.setItem(KEYS.userId, 'user-abc');
}

beforeEach(() => {
  sessionStorage.clear();
  mockFetch.mockReset();
});

describe('login()', () => {
  it('stores correct keys in sessionStorage on success', async () => {
    mockFetch.mockResolvedValueOnce({
      ok: true,
      status: 200,
      json: () =>
        Promise.resolve({
          accessToken: 'acc-tok',
          refreshToken: 'ref-tok',
          accessTokenExpiresAtUtc: futureExpiry(),
          refreshTokenExpiresAtUtc: new Date(Date.now() + 2_592_000_000).toISOString(),
          user: { id: 'user-xyz' },
        }),
    });

    await login('admin@example.com', 'password');

    expect(sessionStorage.getItem(KEYS.accessToken)).toBe('acc-tok');
    expect(sessionStorage.getItem(KEYS.refreshToken)).toBe('ref-tok');
    expect(sessionStorage.getItem(KEYS.userId)).toBe('user-xyz');
    expect(sessionStorage.getItem(KEYS.expiresAt)).toBeTruthy();
  });

  it('throws INVALID_CREDENTIALS on 401', async () => {
    mockFetch.mockResolvedValueOnce({ ok: false, status: 401, json: () => Promise.resolve({}) });
    await expect(login('admin@example.com', 'wrong')).rejects.toThrow('INVALID_CREDENTIALS');
  });

  it('throws INVALID_CREDENTIALS on 403', async () => {
    mockFetch.mockResolvedValueOnce({ ok: false, status: 403, json: () => Promise.resolve({}) });
    await expect(login('admin@example.com', 'wrong')).rejects.toThrow('INVALID_CREDENTIALS');
  });

  it('throws SERVER_ERROR on 500', async () => {
    mockFetch.mockResolvedValueOnce({ ok: false, status: 500, json: () => Promise.resolve({}) });
    await expect(login('admin@example.com', 'pw')).rejects.toThrow('SERVER_ERROR');
  });
});

describe('getAdminToken()', () => {
  it('returns null when sessionStorage is empty', () => {
    expect(getAdminToken()).toBeNull();
  });

  it('returns null when token exists but no expiresAt', () => {
    sessionStorage.setItem(KEYS.accessToken, 'some-token');
    expect(getAdminToken()).toBeNull();
  });

  it('returns null and clears session when token is expired', () => {
    sessionStorage.setItem(KEYS.accessToken, 'expired-tok');
    sessionStorage.setItem(KEYS.expiresAt, pastExpiry());

    expect(getAdminToken()).toBeNull();
    expect(sessionStorage.getItem(KEYS.accessToken)).toBeNull();
    expect(sessionStorage.getItem(KEYS.expiresAt)).toBeNull();
  });

  it('returns the token when valid and not expired', () => {
    sessionStorage.setItem(KEYS.accessToken, 'valid-tok');
    sessionStorage.setItem(KEYS.expiresAt, futureExpiry());

    expect(getAdminToken()).toBe('valid-tok');
  });
});

describe('isAdminLoggedIn()', () => {
  it('returns false when not logged in', () => {
    expect(isAdminLoggedIn()).toBe(false);
  });

  it('returns true when valid session exists', () => {
    setValidSession();
    expect(isAdminLoggedIn()).toBe(true);
  });
});

describe('logout()', () => {
  it('clears all sessionStorage keys immediately', () => {
    setValidSession();
    mockFetch.mockResolvedValueOnce({ ok: true });

    logout();

    expect(sessionStorage.getItem(KEYS.accessToken)).toBeNull();
    expect(sessionStorage.getItem(KEYS.refreshToken)).toBeNull();
    expect(sessionStorage.getItem(KEYS.expiresAt)).toBeNull();
    expect(sessionStorage.getItem(KEYS.userId)).toBeNull();
  });

  it('clears session even when not logged in (no token)', () => {
    logout();
    expect(sessionStorage.getItem(KEYS.accessToken)).toBeNull();
  });
});

describe('getAdminUserId()', () => {
  it('returns null when not logged in', () => {
    expect(getAdminUserId()).toBeNull();
  });

  it('returns userId from sessionStorage', () => {
    setValidSession();
    expect(getAdminUserId()).toBe('user-abc');
  });
});
