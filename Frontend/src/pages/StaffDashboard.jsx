import { useEffect, useState, useCallback } from 'react';
import client, { unwrap } from '../api/client';
import { useAuth } from '../context/AuthContext';
import StaffShell from '../components/StaffShell';
import { SeverityBadge, StatusPill, Btn, inputCls, Spinner, StatCard } from '../components/ui';

const STATUSES = ['ASSIGNED', 'IN_PROGRESS', 'RESOLVED_CLAIMED'];

export default function StaffDashboard() {
  const { user } = useAuth();
  const isSupervisor = user.role === 'SUPERVISOR';
  const [rows, setRows] = useState([]);
  const [loading, setLoading] = useState(true);
  const [sel, setSel] = useState(null);
  const [officers, setOfficers] = useState([]);

  const load = useCallback(() => {
    setLoading(true);
    const url = isSupervisor ? `/complaints?departmentId=${user.departmentId}&size=100` : '/complaints?size=100';
    unwrap(client.get(url))
      .then((p) => setRows(p?.content || []))
      .catch(() => setRows([]))
      .finally(() => setLoading(false));
  }, [isSupervisor, user.departmentId]);

  useEffect(() => { load(); }, [load]);
  useEffect(() => {
    if (isSupervisor) unwrap(client.get('/departments/my/officers')).then(setOfficers).catch(() => {});
  }, [isSupervisor]);

  const open = (count) => rows.filter((r) => !['CLOSED', 'CITIZEN_VERIFIED'].includes(r.status)).length;
  const breached = rows.filter((r) => r.slaBreached).length;

  return (
    <StaffShell title={isSupervisor ? 'Supervisor Console' : 'Officer Console'}>
      <div className="grid grid-cols-2 sm:grid-cols-4 gap-3 mb-6">
        <StatCard value={rows.length} label="Total" />
        <StatCard value={open()} label="Open" accent="text-flag" />
        <StatCard value={breached} label="SLA Breached" accent="text-flag" />
        <StatCard value={rows.filter((r) => ['RESOLVED_CLAIMED', 'CLOSED', 'CITIZEN_VERIFIED'].includes(r.status)).length} label="Resolved" accent="text-verify" />
      </div>

      {loading ? <Spinner /> : (
        <div className="rounded-2xl border border-black/10 bg-white overflow-hidden">
          <table className="w-full text-sm">
            <thead className="bg-cream text-gray-500 text-left">
              <tr>
                <th className="px-4 py-3">Code</th><th className="px-4 py-3">Ward</th>
                <th className="px-4 py-3">Category</th><th className="px-4 py-3">Priority</th>
                <th className="px-4 py-3">Status</th><th className="px-4 py-3">SLA</th><th></th>
              </tr>
            </thead>
            <tbody>
              {rows.map((r) => (
                <tr key={r.id} className="border-t border-black/5 hover:bg-cream/50">
                  <td className="px-4 py-3 font-mono text-xs">{r.trackingCode}</td>
                  <td className="px-4 py-3 font-semibold">{r.ward || '—'}</td>
                  <td className="px-4 py-3">{r.category}</td>
                  <td className="px-4 py-3"><SeverityBadge value={r.priority} /></td>
                  <td className="px-4 py-3"><StatusPill status={r.status} /></td>
                  <td className="px-4 py-3">{r.slaBreached ? <span className="text-flag font-semibold">Breached</span> : <span className="text-gray-400">OK</span>}</td>
                  <td className="px-4 py-3"><button className="text-maroon font-semibold" onClick={() => setSel(r)}>Manage →</button></td>
                </tr>
              ))}
              {!rows.length && <tr><td colSpan={7} className="px-4 py-10 text-center text-gray-400">No complaints assigned.</td></tr>}
            </tbody>
          </table>
        </div>
      )}

      {sel && (
        <ManagePanel row={sel} isSupervisor={isSupervisor} officers={officers}
          onClose={() => setSel(null)} onDone={() => { setSel(null); load(); }} />
      )}
    </StaffShell>
  );
}

function ManagePanel({ row, isSupervisor, officers, onClose, onDone }) {
  const [status, setStatus] = useState(row.status);
  const [note, setNote] = useState('');
  const [officerId, setOfficerId] = useState(row.assignedOfficerId || '');
  const [reason, setReason] = useState('');
  const [err, setErr] = useState('');
  const [busy, setBusy] = useState(false);

  const run = async (fn) => {
    setBusy(true); setErr('');
    try { await fn(); onDone(); } catch (e) { setErr(e.message); } finally { setBusy(false); }
  };

  return (
    <div className="fixed inset-0 z-[2000] flex items-center justify-center bg-black/40 p-4" onClick={onClose}>
      <div className="w-full max-w-md rounded-2xl bg-white p-6 max-h-[90vh] overflow-y-auto" onClick={(e) => e.stopPropagation()}>
        <div className="flex items-center justify-between mb-1">
          <SeverityBadge value={row.priority} />
          <button onClick={onClose} className="text-gray-400 text-xl">×</button>
        </div>
        <h2 className="text-lg font-bold text-ink">{row.ward} · {row.category}</h2>
        <p className="font-mono text-xs text-gray-400">{row.trackingCode}</p>
        <p className="text-sm text-gray-600 mt-2">{row.description}</p>
        <div className="mt-2 flex flex-wrap gap-x-4 gap-y-1 text-xs text-gray-500">
          <span>Dept: <span className="font-semibold text-gray-700">{row.departmentName || '—'}</span></span>
          <span>Supervisor: <span className="font-semibold text-gray-700">{row.supervisorName || 'Unassigned'}</span></span>
          <span>Officer: <span className="font-semibold text-gray-700">{row.assignedOfficerName || 'Unassigned'}</span></span>
          <span>Watchdog: <span className="font-semibold text-gray-700">{row.watchdogName || 'Unassigned'}</span></span>
        </div>
        {row.escalationCount > 0 && (
          <p className="mt-1 text-xs text-flag">
            Escalated to <span className="font-semibold">{row.escalatedToName || 'nobody yet — unstaffed'}</span>
            {row.escalatedToRole && <> ({row.escalatedToRole})</>}
          </p>
        )}

        <div className="mt-5 space-y-4">
          <div>
            <p className="text-sm font-semibold text-gray-700 mb-1">Update status</p>
            <select className={inputCls} value={status} onChange={(e) => setStatus(e.target.value)}>
              {STATUSES.map((s) => <option key={s} value={s}>{s.replace('_', ' ')}</option>)}
            </select>
            {status === 'RESOLVED_CLAIMED' && (
              <textarea className={`${inputCls} mt-2`} rows={2} placeholder="Resolution note (required)"
                value={note} onChange={(e) => setNote(e.target.value)} />
            )}
            <Btn variant="primary" className="w-full mt-2" disabled={busy}
              onClick={() => run(() => unwrap(client.patch(`/complaints/${row.id}/status`, {
                status, resolutionNote: note || null,
                resolutionEvidenceUrl: status === 'RESOLVED_CLAIMED' ? '/files/sample-resolution.jpg' : null,
              })))}>
              Save status
            </Btn>
          </div>

          {isSupervisor && (
            <>
              <div className="border-t border-black/5 pt-4">
                <p className="text-sm font-semibold text-gray-700 mb-1">Assign officer</p>
                <select className={inputCls} value={officerId} onChange={(e) => setOfficerId(e.target.value)}>
                  <option value="">Select officer…</option>
                  {officers.map((o) => <option key={o.id} value={o.id}>{o.fullName} ({o.username})</option>)}
                </select>
                <Btn variant="ghost" className="w-full mt-2" disabled={busy || !officerId}
                  onClick={() => run(() => unwrap(client.patch(`/complaints/${row.id}/assign`, { officerId, reason: 'Assigned by supervisor' })))}>
                  Assign
                </Btn>
              </div>
              <div className="grid grid-cols-2 gap-2">
                <Btn variant="verify" disabled={busy}
                  onClick={() => run(() => unwrap(client.patch(`/complaints/${row.id}/approve`, {})))}>Approve</Btn>
                <Btn variant="flag" disabled={busy}
                  onClick={() => run(() => unwrap(client.post(`/complaints/${row.id}/escalate`, { reason: reason || 'Manual escalation', triggeredBy: 'SUPERVISOR' })))}>Escalate</Btn>
              </div>
            </>
          )}
          {err && <p className="text-flag text-sm">{err}</p>}
        </div>
      </div>
    </div>
  );
}
