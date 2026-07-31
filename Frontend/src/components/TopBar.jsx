import { Link, useLocation } from "react-router-dom";
import Brand from "./Brand";
import { useAuth } from "../context/AuthContext";

const NAV = [
  { to: "/", label: "Map" },
  { to: "/dashboard", label: "Dashboard" },
  { to: "/track", label: "Track" },
];

export default function TopBar() {
  const { pathname } = useLocation();
  const { user, logout } = useAuth();
  return (
    <header className="absolute top-0 inset-x-0 z-[1000] flex items-center justify-between px-4 py-3 bg-cream/90 backdrop-blur border-b border-black/5">
      <div className="flex items-center gap-6">
        <Brand />
        <nav className="hidden sm:flex items-center gap-1">
          {NAV.map((n) => (
            <Link
              key={n.to}
              to={n.to}
              className={`px-3 py-1.5 rounded-md text-sm font-semibold ${pathname === n.to ? "bg-maroon text-white" : "text-gray-600 hover:bg-black/5"}`}
            >
              {n.label}
            </Link>
          ))}
        </nav>
      </div>
      <div className="flex items-center gap-2">
        <Link
          to="/report"
          className="rounded-md bg-maroon px-3 py-1.5 text-sm font-semibold text-white hover:bg-maroon-700"
        >
          Report Issue
        </Link>
        {user ? (
          <div className="flex items-center gap-2">
            <Link
              to={dashFor(user.role)}
              className="rounded-md border border-black/10 bg-white px-3 py-1.5 text-sm font-semibold text-gray-700 hover:bg-gray-50"
            >
              {user.role}
            </Link>
            <button
              onClick={logout}
              className="text-sm text-gray-500 hover:text-flag"
            >
              Logout
            </button>
          </div>
        ) : (
          <Link
            to="/login"
            className="rounded-md border border-black/10 bg-white px-3 py-1.5 text-sm font-semibold text-gray-700 hover:bg-gray-50"
          >
            Staff Login
          </Link>
        )}
      </div>
    </header>
  );
}

export function dashFor(role) {
  if (role === "ADMIN") return "/admin";
  if (role === "WATCHDOG") return "/watchdog";
  return "/staff";
}
