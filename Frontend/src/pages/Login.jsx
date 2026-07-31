import { useState } from "react";
import { useNavigate } from "react-router-dom";
import { useAuth } from "../context/AuthContext";
import { dashFor } from "../components/TopBar";
import Brand from "../components/Brand";
import { Btn, Field, inputCls } from "../components/ui";

const DEMO = [
  ["admin", "Admin"],
  ["watchdog01", "Watchdog"],
  ["supervisor01", "Supervisor"],
  ["officer01", "Officer"],
];

export default function Login() {
  const { login, loading } = useAuth();
  const nav = useNavigate();
  const [username, setUsername] = useState("");
  const [password, setPassword] = useState("");
  const [err, setErr] = useState("");

  const submit = async () => {
    setErr("");
    try {
      const u = await login(username, password);
      nav(dashFor(u.role));
    } catch (e) {
      setErr(e.message);
    }
  };

  return (
    <div className="min-h-screen bg-cream flex items-center justify-center px-4">
      <div className="w-full max-w-sm">
        <div className="flex justify-center mb-6">
          <Brand />
        </div>
        <div className="rounded-2xl border border-black/10 bg-white p-6">
          <h1 className="text-xl font-bold text-ink mb-1">Staff Login</h1>
          <p className="text-sm text-gray-500 mb-5">
            Officers, supervisors, watchdog & admin.
          </p>
          <div className="space-y-4">
            <Field label="Username">
              <input
                className={inputCls}
                value={username}
                onChange={(e) => setUsername(e.target.value)}
                onKeyDown={(e) => e.key === "Enter" && submit()}
              />
            </Field>
            <Field label="Password">
              <input
                type="password"
                className={inputCls}
                value={password}
                onChange={(e) => setPassword(e.target.value)}
                onKeyDown={(e) => e.key === "Enter" && submit()}
              />
            </Field>
            {err && <p className="text-flag text-sm">{err}</p>}
            <Btn
              variant="primary"
              className="w-full"
              disabled={loading}
              onClick={submit}
            >
              {loading ? "Signing in…" : "Sign in"}
            </Btn>
          </div>

          <div className="mt-5 border-t border-black/5 pt-4">
            <p className="text-xs text-gray-400 mb-2">
              Demo accounts (password:{" "}
              <span className="font-mono">demo1234</span>)
            </p>
            <div className="grid grid-cols-2 gap-2">
              {DEMO.map(([u, label]) => (
                <button
                  key={u}
                  onClick={() => {
                    setUsername(u);
                    setPassword("demo1234");
                  }}
                  className="rounded-lg border border-black/10 px-2 py-1.5 text-xs font-semibold text-gray-600 hover:bg-cream"
                >
                  {label}
                </button>
              ))}
            </div>
          </div>
        </div>
      </div>
    </div>
  );
}
