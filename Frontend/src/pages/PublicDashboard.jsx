import { useEffect, useState } from 'react';
import client, { unwrap } from '../api/client';
import TopBar from '../components/TopBar';
import { Spinner } from '../components/ui';

export default function PublicDashboard() {
  const [stats, setStats] = useState(null);
  const [err, setErr] = useState('');

  useEffect(() => {
    unwrap(client.get('/analytics/public/stats')).then(setStats).catch((e) => setErr(e.message));
  }, []);

  return (
    <div className="min-h-screen bg-cream">
      <TopBar />
      <div className="mx-auto max-w-5xl px-4 pt-24 pb-16">
        <h1 className="text-2xl font-bold text-ink">Public Transparency Dashboard</h1>
        <p className="text-gray-500 mb-6">Live accountability snapshot across all wards.</p>

        {!stats && !err && <Spinner />}
        {err && <p className="text-flag">{err}</p>}

        {stats && (
          <>
            <div className="grid grid-cols-1 sm:grid-cols-3 gap-4">
              <BigStat value={stats.unresolved} label="Unresolved" accent="text-flag" />
              <BigStat value={stats.resolved} label="Resolved" accent="text-verify" />
              <BigStat value={`${Math.round(stats.resolutionRate)}%`} label="Resolution Rate" accent="text-maroon" />
            </div>

            <div className="mt-8 grid grid-cols-1 lg:grid-cols-3 gap-6">
              <div className="lg:col-span-2 rounded-2xl border border-black/10 bg-white p-5">
                <h2 className="font-bold text-ink mb-4">Worst wards by unresolved reports</h2>
                <div className="space-y-3">
                  {(stats.worstWards || []).map((w) => (
                    <div key={w.rank} className="flex items-center gap-3">
                      <span className="font-mono text-sm font-bold text-gray-400 w-6">#{w.rank}</span>
                      <div className="flex-1">
                        <div className="flex items-center justify-between text-sm">
                          <span className="font-semibold text-ink">{w.ward}</span>
                          <span className="font-mono text-flag">{w.reports - w.resolved} open</span>
                        </div>
                        <div className="mt-1 h-2 rounded-full bg-gray-100 overflow-hidden">
                          <div className="h-full bg-flag/80 rounded-full"
                            style={{ width: `${Math.min(100, 100 - w.resolvedPct)}%` }} />
                        </div>
                        <div className="text-[11px] text-gray-400 mt-0.5">{w.zone} · {w.reports} reports · {w.resolvedPct}% resolved</div>
                      </div>
                    </div>
                  ))}
                  {!stats.worstWards?.length && <p className="text-gray-400 text-sm">No ward data yet.</p>}
                </div>
              </div>

              <div className="rounded-2xl border border-black/10 bg-white p-5">
                <h2 className="font-bold text-ink mb-4">By category</h2>
                <div className="space-y-2">
                  {(stats.byCategory || []).map((c) => (
                    <div key={c.category} className="flex items-center justify-between text-sm">
                      <span className="text-gray-600">{prettyCat(c.category)}</span>
                      <span className="font-mono font-bold text-maroon">{c.count}</span>
                    </div>
                  ))}
                  {!stats.byCategory?.length && <p className="text-gray-400 text-sm">No data yet.</p>}
                </div>
              </div>
            </div>
          </>
        )}
      </div>
    </div>
  );
}

const prettyCat = (c) => (c || '').replace('_', ' ').toLowerCase().replace(/\b\w/g, (x) => x.toUpperCase());

function BigStat({ value, label, accent }) {
  return (
    <div className="rounded-2xl border border-black/10 bg-white p-6">
      <div className={`font-mono text-4xl font-bold ${accent}`}>{value}</div>
      <div className="text-gray-600 font-semibold mt-1">{label}</div>
    </div>
  );
}
