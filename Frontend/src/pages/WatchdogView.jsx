import { useEffect, useState } from 'react';
import client, { unwrap } from '../api/client';
import StaffShell from '../components/StaffShell';
import { Spinner, StatCard, Btn } from '../components/ui';

export default function WatchdogView() {
  const [tab, setTab] = useState('Oversight');
  return (
    <StaffShell title="Watchdog" tabs={['Oversight', 'Audit Integrity', 'Corruption Graph']} active={tab} onTab={setTab}>
      {tab === 'Oversight' && <Scorecards />}
      {tab === 'Audit Integrity' && <Audit />}
      {tab === 'Corruption Graph' && <Graph />}
    </StaffShell>
  );
}

function Scorecards() {
  const [data, setData] = useState(null);
  useEffect(() => { unwrap(client.get('/analytics/department-scorecard/all')).then(setData).catch(() => setData([])); }, []);
  if (!data) return <Spinner />;
  return (
    <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-3">
      {data.map((d) => (
        <div key={d.departmentId} className="rounded-2xl border border-black/10 bg-white p-5">
          <div className="flex items-center justify-between">
            <h3 className="font-bold text-ink">{d.departmentName}</h3>
            <span className="font-mono text-xs text-gray-400">{d.code}</span>
          </div>
          <div className="mt-3 grid grid-cols-2 gap-2 text-sm">
            <Metric label="Total" value={d.totalComplaints} />
            <Metric label="Resolved" value={d.resolvedCount} />
            <Metric label="SLA %" value={`${Math.round(d.slaCompliancePct)}%`} />
            <Metric label="Avg hrs" value={Math.round(d.avgResolutionHours)} />
          </div>
          <div className="mt-3">
            <div className="flex justify-between text-xs text-gray-500"><span>Efficiency index</span><span className="font-mono">{d.efficiencyIndex?.toFixed(1)}</span></div>
            <div className="mt-1 h-2 rounded-full bg-gray-100"><div className="h-full rounded-full bg-maroon" style={{ width: `${Math.min(100, d.efficiencyIndex * 10)}%` }} /></div>
          </div>
        </div>
      ))}
      {!data.length && <p className="text-gray-400">No scorecard data yet.</p>}
    </div>
  );
}
const Metric = ({ label, value }) => (
  <div className="rounded-lg border border-black/10 px-2 py-1.5"><div className="font-mono font-bold text-maroon">{value}</div><div className="text-xs text-gray-400">{label}</div></div>
);

function Audit() {
  const [result, setResult] = useState(null);
  const [busy, setBusy] = useState(false);
  const verify = () => { setBusy(true); unwrap(client.get('/audit/verify')).then(setResult).catch((e) => setResult({ error: e.message })).finally(() => setBusy(false)); };
  useEffect(verify, []);
  return (
    <div className="max-w-xl">
      <div className="rounded-2xl border border-black/10 bg-white p-6">
        <h2 className="font-bold text-ink mb-1">Immutable Audit Chain</h2>
        <p className="text-sm text-gray-500 mb-4">Every action is hash-chained. Re-walk the chain to detect tampering.</p>
        {busy && <Spinner label="Verifying chain…" />}
        {result && !busy && !result.error && (
          <div className={`rounded-xl p-5 border ${result.intact ? 'bg-emerald-50 border-emerald-200' : 'bg-red-50 border-red-200'}`}>
            <div className="text-3xl">{result.intact ? '🔒' : '⚠️'}</div>
            <div className={`font-bold text-lg ${result.intact ? 'text-emerald-700' : 'text-flag'}`}>
              {result.intact ? 'Chain intact & verified' : 'Tampering detected!'}
            </div>
            <div className="mt-2 grid grid-cols-2 gap-3 text-sm">
              <StatCard value={result.totalEntries} label="Total entries" />
              <StatCard value={result.firstBrokenId ?? '—'} label="First broken ID" accent={result.intact ? 'text-verify' : 'text-flag'} />
            </div>
            <p className="text-xs text-gray-400 mt-3">Verified at {new Date(result.verifiedAt).toLocaleString()}</p>
          </div>
        )}
        {result?.error && <p className="text-flag">{result.error}</p>}
        <Btn variant="primary" className="mt-4" disabled={busy} onClick={verify}>Re-verify chain</Btn>
      </div>
    </div>
  );
}

function Graph() {
  const [g, setG] = useState(null);
  useEffect(() => { unwrap(client.get('/analytics/corruption-graph')).then(setG).catch(() => setG({ nodes: [], edges: [] })); }, []);
  if (!g) return <Spinner />;
  if (!g.nodes.length) return <p className="text-gray-400">No network signals detected yet — needs more linked complaints.</p>;

  const cx = 350, cy = 230, R = 170;
  const pos = {};
  g.nodes.forEach((n, i) => {
    const a = (2 * Math.PI * i) / g.nodes.length - Math.PI / 2;
    pos[n.id] = { x: cx + R * Math.cos(a), y: cy + R * Math.sin(a) };
  });

  return (
    <div className="rounded-2xl border border-black/10 bg-white p-4">
      <h2 className="font-bold text-ink mb-2">Corruption Network</h2>
      <p className="text-sm text-gray-500 mb-3">Links between wards, departments, and repeat patterns. Node size = risk.</p>
      <svg viewBox="0 0 700 470" className="w-full">
        {g.edges.map((e, i) => pos[e.source] && pos[e.target] && (
          <line key={i} x1={pos[e.source].x} y1={pos[e.source].y} x2={pos[e.target].x} y2={pos[e.target].y}
            stroke="#7B1E1E" strokeOpacity={Math.min(0.6, 0.15 + e.weight * 0.1)} strokeWidth={1 + e.weight} />
        ))}
        {g.nodes.map((n) => {
          const r = 14 + (n.riskScore || 0) * 2;
          const color = n.type === 'DEPARTMENT' ? '#1d4ed8' : n.type === 'WARD' ? '#7B1E1E' : '#ea580c';
          return (
            <g key={n.id}>
              <circle cx={pos[n.id].x} cy={pos[n.id].y} r={r} fill={color} fillOpacity={0.85} />
              <text x={pos[n.id].x} y={pos[n.id].y + r + 12} textAnchor="middle" className="fill-gray-700" fontSize="11" fontWeight="600">{n.label}</text>
            </g>
          );
        })}
      </svg>
    </div>
  );
}
