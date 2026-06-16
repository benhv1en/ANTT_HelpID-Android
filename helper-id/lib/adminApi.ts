import { getAdminToken } from './adminAuth';

export interface AdminStats {
  totalUsers: number;
  totalProfiles: number;
  totalPublicLinks: number;
  auditEventsLast7Days: number;
}

export interface AdminUser {
  userId: string;
  email: string;
  displayName: string;
  roles: string[];
  isLocked: boolean;
  createdAtUtc: string;
}

export interface AdminUsersPage {
  users: AdminUser[];
  page: number;
  pageSize: number;
  totalCount: number;
}

export class AdminAuthError extends Error {
  constructor() {
    super('AUTH_ERROR');
    this.name = 'AdminAuthError';
  }
}

async function authedFetch(path: string, init: RequestInit = {}): Promise<Response> {
  const token = getAdminToken();
  if (!token) throw new AdminAuthError();

  const res = await fetch(path, {
    ...init,
    headers: {
      ...(init.headers ?? {}),
      Authorization: `Bearer ${token}`,
      'Cache-Control': 'no-store',
    },
  });

  if (res.status === 401 || res.status === 403) throw new AdminAuthError();
  return res;
}

export async function getStats(): Promise<AdminStats> {
  const res = await authedFetch('/api/admin-stats');
  if (!res.ok) throw new Error('FETCH_ERROR');
  return res.json() as Promise<AdminStats>;
}

export async function getUsers(page: number, size: number): Promise<AdminUsersPage> {
  const res = await authedFetch(`/api/admin-users?page=${page}&size=${size}`);
  if (!res.ok) throw new Error('FETCH_ERROR');
  return res.json() as Promise<AdminUsersPage>;
}

export async function assignRole(userId: string, roleId: string): Promise<void> {
  const res = await authedFetch(
    `/api/admin-role?userId=${encodeURIComponent(userId)}&roleId=${encodeURIComponent(roleId)}`,
    { method: 'POST' },
  );
  if (res.status === 404) throw new Error('USER_NOT_FOUND');
  if (!res.ok) throw new Error('FETCH_ERROR');
}

export async function revokeRole(userId: string, roleId: string): Promise<void> {
  const res = await authedFetch(
    `/api/admin-role?userId=${encodeURIComponent(userId)}&roleId=${encodeURIComponent(roleId)}`,
    { method: 'DELETE' },
  );
  if (res.status === 404) throw new Error('USER_NOT_FOUND');
  if (!res.ok) throw new Error('FETCH_ERROR');
}
