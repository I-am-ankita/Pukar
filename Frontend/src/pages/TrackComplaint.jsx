import { useState } from 'react';
import client, { unwrap } from '../api/client';
import TopBar from '../components/TopBar';
import { Btn, inputCls, SeverityBadge, StatusPill } from '../components/ui';

export default function TrackComplaint() {
  const [code, setCode] = useState('');
  const [data, setData] = useState(null);
  const [err, setErr] = useState('');
  const [busy, setBusy] = useState(false);

  const search = async () => {
    if (!code.trim()) return;
    setBusy(true); setErr(''); setData(null);
    try {
      setData(await unwrap(client.get(`/complaints/track/${code.trim()}`)));
    } catch (e) {
      setErr(e.message);
    } finally {
      setBusy(false);
    }
  };

  return (
    <div className="min-h-screen bg-cream">
      <TopBar />
      <div className="mx-auto max-w-2xl px-4 pt-24 pb-16">
        <h1 className="text-2xl font-bold text-ink">Track a Complaint</h1>
        <p className="text-gray-500 mb-6">Enter the tracking code you received when reporting.</p>

        <div className="flex gap-2">
          <input className={inputCls} value={code} onChange={(e) => setCode(e.target.value.toUpperCase())}
            placeholder="PUK-2026-123456" onKeyDown={(e) => e.key === 'Enter' && search()} />
          <Btn variant="primary" disabled={busy} onClick={search}>{busy ? '…' : 'Track'}</Btn>
        </div>
        {err && <p className="text-flag text-sm mt-3">{err}</p>}

        {data && (
          <div className="mt-6 rounded-2xl border border-black/10 bg-white p-6 animate-fadeUp">
            <div className="flex items-center justify-between">
              <SeverityBadge value={data.priority} />
              <StatusPill status={data.status} />
            </div>
            <h2 className="mt-2 text-xl font-bold text-ink">{data.ward || data.category}</h2>
            <p className="text-sm text-gray-500">{data.locationText}</p>
            <div className="mt-4 grid grid-cols-2 gap-3 text-sm">
              <Info label="Department" value={data.departmentName} />
              <Info label="Category" value={data.category} />
              <Info label="Supervisor" value={data.supervisorName || 'Unassigned'} />
              <Info label="Watchdog" value={data.watchdogName || 'Unassigned'} />
              <Info label="Field officer" value={data.assignedOfficerName || 'Awaiting assignment'} />
              <Info label="Submitted" value={fmt(data.submittedAt)} />
              <Info label="Last update" value={fmt(data.lastUpdatedAt)} />
              <Info label="SLA deadline" value={fmt(data.slaDeadline)} />
              <Info label="Escalations" value={data.escalationCount} />
            </div>

            {data.resolutionNote && (
              <div className="mt-4 rounded-xl bg-emerald-50 border border-emerald-200 p-3 text-sm text-emerald-800">
                <span className="font-semibold">Resolution: </span>{data.resolutionNote}
              </div>
            )}

            <div className="mt-6">
              <h3 className="font-bold text-ink mb-2">Status timeline</h3>
              <ol className="relative border-l-2 border-maroon/20 ml-2 space-y-4">
                {(data.statusHistory || data.history || []).map((h, i) => (
                  <li key={i} className="ml-4">
                    <span className="absolute -left-[7px] h-3 w-3 rounded-full bg-maroon" />
                    <StatusPill status={h.status} />
                    <span className="ml-2 text-xs text-gray-400">{fmt(h.at)}</span>
                    {h.reason && <p className="text-sm text-gray-600">{h.reason}</p>}
                  </li>
                ))}
              </ol>
            </div>
          </div>
        )}
      </div>
    </div>
  );
}

const fmt = (iso) => (iso ? new Date(iso).toLocaleString() : '—');
function Info({ label, value }) {
  return (
    <div className="rounded-lg border border-black/10 px-3 py-2">
      <div className="text-xs text-gray-400">{label}</div>
      <div className="font-semibold text-gray-800">{value || '—'}</div>
    </div>
  );
}
