import React, { useEffect } from 'react';
import { Outlet, useNavigate } from 'react-router-dom';
import { logout } from '../../lib/adminAuth';

function setNoIndexMeta() {
  const name = 'robots';
  const content = 'noindex,nofollow,noarchive';
  let el = document.querySelector(`meta[name="${name}"]`);
  if (!el) {
    el = document.createElement('meta');
    el.setAttribute('name', name);
    document.head.appendChild(el);
  }
  el.setAttribute('content', content);
}

export const AdminLayout: React.FC = () => {
  const navigate = useNavigate();

  useEffect(() => {
    setNoIndexMeta();
    document.title = 'HelpID Admin';
  }, []);

  const handleLogout = () => {
    logout();
    navigate('/admin/login', { replace: true });
  };

  return (
    <div className="min-h-screen bg-gray-50 flex flex-col">
      <header className="bg-white border-b border-gray-200 px-6 py-4 flex items-center justify-between">
        <span className="text-lg font-bold text-gray-900">HelpID Admin</span>
        <button
          onClick={handleLogout}
          className="text-sm text-gray-600 hover:text-gray-900 border border-gray-300 rounded-lg px-4 py-1.5 hover:bg-gray-50 transition-colors"
        >
          Logout
        </button>
      </header>
      <main className="flex-grow">
        <Outlet />
      </main>
    </div>
  );
};
