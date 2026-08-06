import { Link } from 'react-router-dom';

// Severity gradient: yellow (low) -> orange (medium/high) -> red (critical).
// Green is reserved for "resolved" status (see statusMeta) so it never doubles as a priority color.
export const SEVERITY = {
  CRITICAL: { dot: 'bg-flag', text: 'text-flag', label: 'CRITICAL' },
  HIGH:     { dot: 'bg-orange-500', text: 'text-orange-600', label: 'HIGH' },
  MEDIUM:   { dot: 'bg-amber-500', text: 'text-amber-600', label: 'MEDIUM' },
  LOW:      { dot: 'bg-yellow-400', text: 'text-yellow-600', label: 'LOW' },
};

const RESOLVED_STATUSES = ['RESOLVED_CLAIMED', 'CITIZEN_VERIFIED', 'CLOSED'];

export function statusMeta(status) {
  if (RESOLVED_STATUSES.includes(status))
    return { label: status === 'CLOSED' ? 'Resolved' : 'Resolution Claimed', cls: 'bg-emerald-50 text-emerald-700 border-emerald-200' };
  if (status === 'ESCALATED' || status === 'WATCHDOG_REVIEW' || status === 'DISPUTED' || status === 'CITIZEN_REJECTED')
    return { label: status.replace('_', ' ').toLowerCase().replace(/\b\w/g, (c) => c.toUpperCase()), cls: 'bg-red-50 text-flag border-red-200' };
  return { label: 'Unresolved', cls: 'bg-red-50 text-flag border-red-200' };
}

export function SeverityBadge({ value }) {
  const s = SEVERITY[value] || SEVERITY.LOW;
  return (
    <span className="inline-flex items-center gap-2 font-bold tracking-wide text-sm">
      <span className={`h-2.5 w-2.5 rounded-full ${s.dot}`} />
      <span className={s.text}>{s.label}</span>
    </span>
  );
}

export function StatusPill({ status }) {
  const m = statusMeta(status);
  return <span className={`text-xs font-semibold px-2.5 py-1 rounded-full border ${m.cls}`}>{m.label}</span>;
}

export function StatCard({ value, label, sub, accent = 'text-maroon' }) {
  return (
    <div className="rounded-xl border border-black/10 bg-white px-4 py-3">
      <div className={`font-mono text-2xl font-bold ${accent}`}>{value}</div>
      <div className="text-sm font-semibold text-gray-800">{label}</div>
      {sub && <div className="text-xs text-gray-500">{sub}</div>}
    </div>
  );
}

export function Spinner({ label = 'Loading…' }) {
  return (
    <div className="flex items-center justify-center gap-3 py-10 text-gray-500">
      <div className="h-5 w-5 animate-spin rounded-full border-2 border-maroon border-t-transparent" />
      <span className="text-sm">{label}</span>
    </div>
  );
}

export function Btn({ as = 'button', to, variant = 'primary', className = '', children, ...rest }) {
  const styles = {
    primary: 'bg-maroon text-white hover:bg-maroon-700',
    verify: 'bg-verify text-white hover:bg-green-700',
    flag: 'bg-flag text-white hover:bg-red-700',
    ghost: 'bg-white text-gray-800 border border-black/10 hover:bg-gray-50',
    dark: 'bg-ink text-white hover:bg-slate-800',
  };
  const cls = `inline-flex items-center justify-center gap-2 rounded-lg px-4 py-2.5 text-sm font-semibold transition ${styles[variant]} ${className}`;
  if (as === 'link') return <Link to={to} className={cls} {...rest}>{children}</Link>;
  return <button className={cls} {...rest}>{children}</button>;
}

export function Field({ label, children, hint }) {
  return (
    <label className="block">
      <span className="mb-1 block text-sm font-semibold text-gray-700">{label}</span>
      {children}
      {hint && <span className="mt-1 block text-xs text-gray-500">{hint}</span>}
    </label>
  );
}

export const inputCls =
  'w-full rounded-lg border border-black/15 bg-white px-3 py-2.5 text-sm outline-none focus:border-maroon focus:ring-2 focus:ring-maroon/20';
