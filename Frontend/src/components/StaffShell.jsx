import Brand from './Brand';
import { useAuth } from '../context/AuthContext';
import { Link } from 'react-router-dom';

export default function StaffShell({ title, children, tabs, active, onTab }) {
  const { user, logout } = useAuth();
  return (
    <div className="min-h-screen bg-cream">
      <header className="flex items-center justify-between px-4 py-3 bg-white border-b border-black/10">
        <div className="flex items-center gap-4">
          <Brand />
          <span className="text-sm font-semibold text-gray-400">/ {title}</span>
        </div>
        <div className="flex items-center gap-3 text-sm">
          <Link to="/" className="text-gray-500 hover:text-maroon">Public site ↗</Link>
          <span className="text-gray-700 font-semibold">{user?.fullName}</span>
          <span className="rounded-full bg-maroon-50 text-maroon px-2 py-0.5 text-xs font-bold">{user?.role}</span>
          <button onClick={logout} className="text-gray-400 hover:text-flag">Logout</button>
        </div>
      </header>
      {tabs && (
        <div className="flex gap-1 px-4 pt-3 bg-white border-b border-black/5">
          {tabs.map((t) => (
            <button key={t} onClick={() => onTab(t)}
              className={`px-4 py-2 text-sm font-semibold rounded-t-lg ${active === t ? 'bg-cream text-maroon border-x border-t border-black/10' : 'text-gray-500 hover:text-gray-800'}`}>
              {t}
            </button>
          ))}
        </div>
      )}
      <main className="mx-auto max-w-6xl px-4 py-6">{children}</main>
    </div>
  );
}
