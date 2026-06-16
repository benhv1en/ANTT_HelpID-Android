const KEYS = {
  accessToken: 'helpid_admin_access_token',
  refreshToken: 'helpid_admin_refresh_token',
  expiresAt: 'helpid_admin_expires_at',
  userId: 'helpid_admin_user_id',
} as const;

function clearSession(): void {
  (Object.values(KEYS) as string[]).forEach((key) => sessionStorage.removeItem(key));
}

export async function login(email: string, password: string): Promise<void> {
  const res = await fetch('/api/admin-login', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ email, password }),
  });

  if (!res.ok) {
    if (res.status === 401 || res.status === 403) throw new Error('INVALID_CREDENTIALS');
    throw new Error('SERVER_ERROR');
  }

  const data = (await res.json()) as {
    accessToken: string;
    refreshToken: string;
    accessTokenExpiresAtUtc: string;
    refreshTokenExpiresAtUtc: string;
    user: { id: string };
  };

  sessionStorage.setItem(KEYS.accessToken, data.accessToken);
  sessionStorage.setItem(KEYS.refreshToken, data.refreshToken);
  sessionStorage.setItem(KEYS.expiresAt, data.accessTokenExpiresAtUtc);
  sessionStorage.setItem(KEYS.userId, data.user?.id ?? '');
}

export function logout(): void {
  const token = getAdminToken();
  const refreshToken = sessionStorage.getItem(KEYS.refreshToken);
  if (token) {
    // Best-effort server-side revoke — fire-and-forget, don't block UI
    fetch('/api/admin-logout', {
      method: 'POST',
      headers: { Authorization: `Bearer ${token}`, 'Content-Type': 'application/json' },
      body: JSON.stringify({ refreshToken }),
    }).catch(() => undefined);
  }
  clearSession();
}

export function getAdminToken(): string | null {
  const token = sessionStorage.getItem(KEYS.accessToken);
  const expiresAt = sessionStorage.getItem(KEYS.expiresAt);
  if (!token || !expiresAt) return null;
  if (new Date(expiresAt) <= new Date()) {
    clearSession();
    return null;
  }
  return token;
}

export function isAdminLoggedIn(): boolean {
  return getAdminToken() !== null;
}

export function getAdminUserId(): string | null {
  return sessionStorage.getItem(KEYS.userId);
}
