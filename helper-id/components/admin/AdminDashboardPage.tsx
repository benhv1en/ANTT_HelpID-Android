import React, { useCallback, useEffect, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { AdminAuthError, AdminStats, getStats } from '../../lib/adminApi';
import { logout } from '../../lib/adminAuth';

const STAT_LABELS: { key: keyof AdminStats; label: string }[] = [
  { key: 'totalUsers', label: 'Total users' },
  { key: 'totalProfiles', label: 'Profiles created' },
  { key: 'totalPublicLinks', label: 'Public links minted' },
  { key: 'auditEventsLast7Days', label: 'Audit events (7 days)' },
];

function StatSkeleton() {
  return (
    <div className="bg-white rounded-2xl border border-gray-100 p-6 shadow-sm animate-pulse">
      <div className="h-3 bg-gray-200 rounded w-1/2 mb-4" />
      <div className="h-8 bg-gray-200 rounded w-1/3" />
    </div>
  );
}

export const AdminDashboardPage: React.FC = () => {
  const navigate = useNavigate();
  const [stats, setStats] = useState<AdminStats | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  const load = useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      setStats(await getStats());
    } catch (err) {
      if (err instanceof AdminAuthError) {
        logout();
        navigate('/admin/login', { replace: true });
        return;
      }
      setError('Failed to load stats. Please try again.');
    } finally {
      setLoading(false);
    }
  }, [navigate]);

  useEffect(() => {
    load();
  }, [load]);

  return (
    <div className="max-w-4xl mx-auto px-6 py-8">
      <div className="flex items-center justify-between mb-6">
        <h2 className="text-xl font-semibold text-gray-900">Dashboard</h2>
        <a
          href="/admin/users"
          className="text-sm text-blue-600 hover:underline"
          onClick={(e) => { e.preventDefault(); navigate('/admin/users'); }}
        >
          Manage users →
        </a>
      </div>

      {loading && (
        <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
          {STAT_LABELS.map((s) => <StatSkeleton key={s.key} />)}
        </div>
      )}

      {!loading && error && (
        <div className="bg-white rounded-2xl border border-red-100 p-6 shadow-sm">
          <p className="text-red-600 mb-4">{error}</p>
          <button
            onClick={load}
            className="text-sm bg-gray-900 text-white rounded-lg px-4 py-2 hover:bg-gray-700 transition-colors"
          >
            Retry
          </button>
        </div>
      )}

      {!loading && !error && stats && (
        <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
          {STAT_LABELS.map(({ key, label }) => (
            <div key={key} className="bg-white rounded-2xl border border-gray-100 p-6 shadow-sm">
              <div className="text-xs text-gray-400 uppercase tracking-wider font-semibold mb-2">
                {label}
              </div>
              <div className="text-4xl font-bold text-gray-900">{stats[key]}</div>
            </div>
          ))}
        </div>
      )}
    </div>
  );
};
