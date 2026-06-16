import React, { useCallback, useEffect, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { AdminAuthError, AdminUser, AdminUsersPage as UsersPage, assignRole, getUsers, revokeRole } from '../../lib/adminApi';
import { getAdminUserId, logout } from '../../lib/adminAuth';

const PAGE_SIZE = 20;
const ROLE_ADMIN = 'role_admin';

type RowStatus = 'idle' | 'loading' | 'success' | 'error';
interface RowState { status: RowStatus; message: string }

function formatDate(iso: string): string {
  try {
    return new Date(iso).toLocaleDateString(undefined, { year: 'numeric', month: 'short', day: 'numeric' });
  } catch {
    return '—';
  }
}

export const AdminUsersPage: React.FC = () => {
  const navigate = useNavigate();
  const currentUserId = getAdminUserId();

  const [data, setData] = useState<UsersPage | null>(null);
  const [page, setPage] = useState(1);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [rowStates, setRowStates] = useState<Record<string, RowState>>({});

  const handleAuthError = useCallback(() => {
    logout();
    navigate('/admin/login', { replace: true });
  }, [navigate]);

  const load = useCallback(async (p: number) => {
    setLoading(true);
    setError(null);
    setRowStates({});
    try {
      setData(await getUsers(p, PAGE_SIZE));
    } catch (err) {
      if (err instanceof AdminAuthError) { handleAuthError(); return; }
      setError('Failed to load users. Please try again.');
    } finally {
      setLoading(false);
    }
  }, [handleAuthError]);

  useEffect(() => { load(page); }, [load, page]);

  const setRow = (userId: string, state: RowState) =>
    setRowStates((prev) => ({ ...prev, [userId]: state }));

  const handleGrant = async (user: AdminUser) => {
    setRow(user.userId, { status: 'loading', message: '' });
    try {
      await assignRole(user.userId, ROLE_ADMIN);
      setRow(user.userId, { status: 'success', message: 'Done' });
      await load(page);
    } catch (err) {
      if (err instanceof AdminAuthError) { handleAuthError(); return; }
      setRow(user.userId, { status: 'error', message: 'Failed' });
    }
  };

  const handleRevoke = async (user: AdminUser) => {
    setRow(user.userId, { status: 'loading', message: '' });
    try {
      await revokeRole(user.userId, ROLE_ADMIN);
      setRow(user.userId, { status: 'success', message: 'Done' });
      await load(page);
    } catch (err) {
      if (err instanceof AdminAuthError) { handleAuthError(); return; }
      setRow(user.userId, { status: 'error', message: 'Failed' });
    }
  };

  const totalPages = data ? Math.max(1, Math.ceil(data.totalCount / PAGE_SIZE)) : 1;

  return (
    <div className="max-w-5xl mx-auto px-6 py-8">
      <div className="flex items-center justify-between mb-6">
        <h2 className="text-xl font-semibold text-gray-900">Users</h2>
        <button
          onClick={() => navigate('/admin')}
          className="text-sm text-blue-600 hover:underline"
        >
          ← Dashboard
        </button>
      </div>

      {loading && (
        <div className="bg-white rounded-2xl border border-gray-100 p-8 shadow-sm animate-pulse">
          <div className="space-y-3">
            {Array.from({ length: 5 }).map((_, i) => (
              <div key={i} className="h-10 bg-gray-100 rounded" />
            ))}
          </div>
        </div>
      )}

      {!loading && error && (
        <div className="bg-white rounded-2xl border border-red-100 p-6 shadow-sm">
          <p className="text-red-600 mb-4">{error}</p>
          <button
            onClick={() => load(page)}
            className="text-sm bg-gray-900 text-white rounded-lg px-4 py-2 hover:bg-gray-700 transition-colors"
          >
            Retry
          </button>
        </div>
      )}

      {!loading && !error && data && (
        <>
          <div className="bg-white rounded-2xl border border-gray-100 shadow-sm overflow-hidden">
            <table className="w-full text-sm">
              <thead>
                <tr className="border-b border-gray-100 bg-gray-50">
                  <th className="text-left px-5 py-3 font-semibold text-gray-500 text-xs uppercase tracking-wider">Email</th>
                  <th className="text-left px-5 py-3 font-semibold text-gray-500 text-xs uppercase tracking-wider">Roles</th>
                  <th className="text-left px-5 py-3 font-semibold text-gray-500 text-xs uppercase tracking-wider">Status</th>
                  <th className="text-left px-5 py-3 font-semibold text-gray-500 text-xs uppercase tracking-wider">Created</th>
                  <th className="text-left px-5 py-3 font-semibold text-gray-500 text-xs uppercase tracking-wider">Actions</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-gray-50">
                {data.users.map((user) => {
                  const rs = rowStates[user.userId];
                  const busy = rs?.status === 'loading';
                  const isAdmin = user.roles.includes('Admin') || user.roles.includes('role_admin');
                  const isSelf = user.userId === currentUserId;

                  return (
                    <tr key={user.userId} className="hover:bg-gray-50 transition-colors">
                      <td className="px-5 py-3 text-gray-900 font-medium">{user.email}</td>
                      <td className="px-5 py-3 text-gray-600">
                        <div className="flex flex-wrap gap-1">
                          {user.roles.map((r) => (
                            <span
                              key={r}
                              className={`px-2 py-0.5 rounded-full text-xs font-semibold ${
                                r === 'Admin' || r === 'role_admin'
                                  ? 'bg-purple-100 text-purple-700'
                                  : 'bg-gray-100 text-gray-600'
                              }`}
                            >
                              {r}
                            </span>
                          ))}
                        </div>
                      </td>
                      <td className="px-5 py-3">
                        <span
                          className={`px-2 py-0.5 rounded-full text-xs font-semibold ${
                            user.isLocked
                              ? 'bg-red-100 text-red-700'
                              : 'bg-green-100 text-green-700'
                          }`}
                        >
                          {user.isLocked ? 'Locked' : 'Active'}
                        </span>
                      </td>
                      <td className="px-5 py-3 text-gray-500">{formatDate(user.createdAtUtc)}</td>
                      <td className="px-5 py-3">
                        <div className="flex items-center gap-2">
                          {!isAdmin ? (
                            <button
                              onClick={() => handleGrant(user)}
                              disabled={busy}
                              className="text-xs px-3 py-1.5 rounded-lg bg-purple-600 text-white hover:bg-purple-700 disabled:opacity-50 disabled:cursor-not-allowed transition-colors"
                            >
                              {busy ? '…' : 'Grant Admin'}
                            </button>
                          ) : (
                            <button
                              onClick={() => handleRevoke(user)}
                              disabled={busy || isSelf}
                              title={isSelf ? 'You cannot revoke your own admin role' : undefined}
                              className="text-xs px-3 py-1.5 rounded-lg bg-red-100 text-red-700 hover:bg-red-200 disabled:opacity-40 disabled:cursor-not-allowed transition-colors"
                            >
                              {busy ? '…' : 'Revoke Admin'}
                            </button>
                          )}
                          {rs?.status === 'success' && (
                            <span className="text-xs text-green-600 font-medium">{rs.message}</span>
                          )}
                          {rs?.status === 'error' && (
                            <span className="text-xs text-red-600 font-medium">{rs.message}</span>
                          )}
                        </div>
                      </td>
                    </tr>
                  );
                })}
                {data.users.length === 0 && (
                  <tr>
                    <td colSpan={5} className="px-5 py-8 text-center text-gray-400">
                      No users found.
                    </td>
                  </tr>
                )}
              </tbody>
            </table>
          </div>

          <div className="flex items-center justify-between mt-4">
            <span className="text-sm text-gray-500">
              Page {page} / {totalPages} &nbsp;·&nbsp; {data.totalCount} total
            </span>
            <div className="flex gap-2">
              <button
                onClick={() => setPage((p) => p - 1)}
                disabled={page <= 1}
                className="text-sm px-4 py-1.5 rounded-lg border border-gray-300 hover:bg-gray-50 disabled:opacity-40 disabled:cursor-not-allowed transition-colors"
              >
                Previous
              </button>
              <button
                onClick={() => setPage((p) => p + 1)}
                disabled={page >= totalPages}
                className="text-sm px-4 py-1.5 rounded-lg border border-gray-300 hover:bg-gray-50 disabled:opacity-40 disabled:cursor-not-allowed transition-colors"
              >
                Next
              </button>
            </div>
          </div>
        </>
      )}
    </div>
  );
};
