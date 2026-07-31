import { useEffect, useState } from 'react';
import client, { unwrap } from '../api/client';
import { SeverityBadge, StatusPill, Btn, Spinner, Field, inputCls } from './ui';

const daysSince = (iso) => (iso ? Math.max(0, Math.floor((Date.now() - new Date(iso).getTime()) / 86400000)) : 0);
const prettyCat = (c) => (c || '').replace('_', ' ').toLowerCase().replace(/\b\w/g, (x) => x.toUpperCase());

export default function ComplaintDetailModal({ trackingCode, onClose }) {
  const [data, setData] = useState(null);
  const [err, setErr] = useState('');
  const [msg, setMsg] = useState('');
  const [busy, setBusy] = useState(false);

  // identity / OTP state
  const [auth, setAuth] = useState(null);          // null | 'choose' | 'otp' | 'token'
  const [pending, setPending] = useState(null);    // boolean: verify(true)/flag(false) awaiting identity
  const [citizenToken, setCitizenToken] = useState(null);
  const [phone, setPhone] = useState('');
  const [otpSent, setOtpSent] = useState(false);
  const [devCode, setDevCode] = useState('');
  const [code, setCode] = useState('');
  const [reporterToken, setReporterToken] = useState('');

  const load = () => {
    setData(null); setErr('');
    unwrap(client.get(`/complaints/track/${trackingCode}`)).then(setData).catch((e) => setErr(e.message));
  };
  useEffect(load, [trackingCode]);

  const canGiveFeedback = data?.status === 'RESOLVED_CLAIMED';

  const startAction = (isResolved) => {
    setMsg('');
    setPending(isResolved);
    if (citizenToken) return postFeedback(isResolved, { citizenToken });
    setAuth('choose');
  };

  const postFeedback = async (isResolved, { citizenToken: ct, reporterToken: rt } = {}) => {
    setBusy(true); setMsg('');
    try {
      await unwrap(client.post(
        `/complaints/track/${trackingCode}/feedback`,
        { isResolved, remarks: isResolved ? 'Citizen verified the resolution' : 'Citizen flagged: not resolved', reporterToken: rt || null },
        { headers: ct ? { 'X-Citizen-Token': ct } : {} },
      ));
      setMsg(isResolved ? 'Thanks — confirmed as resolved.' : 'Flagged. This complaint will be re-escalated for review.');
      setAuth(null); setPending(null);
      load();
    } catch (e) {
      setMsg(e.message);
    } finally {
      setBusy(false);
    }
  };

  const sendOtp = async () => {
    setBusy(true); setMsg('');
    try {
      const r = await unwrap(client.post('/auth/otp/request', { phone }));
      setOtpSent(true);
      setDevCode(r.devCode || '');
    } catch (e) {
      setMsg(e.message);
    } finally {
      setBusy(false);
    }
  };

  const verifyOtp = async () => {
    setBusy(true); setMsg('');
    try {
      const r = await unwrap(client.post('/auth/otp/verify', { phone, code }));
      setCitizenToken(r.citizenToken);
      await postFeedback(pending, { citizenToken: r.citizenToken });
    } catch (e) {
      setMsg(e.message);
      setBusy(false);
    }
  };

  const photo = data?.evidence?.find((e) => (e.fileType || '').startsWith('image'))?.fileUrl || data?.evidence?.[0]?.fileUrl;
  const reportCount = data?.evidence?.length ? data.evidence.length : 1;

  return (
    <div className="fixed inset-0 z-[2000] flex items-end sm:items-center justify-center bg-black/40 p-0 sm:p-4" onClick={onClose}>
      <div className="w-full sm:max-w-lg max-h-[92vh] overflow-y-auto rounded-t-2xl sm:rounded-2xl bg-white shadow-2xl animate-fadeUp"
        onClick={(e) => e.stopPropagation()}>
        {!data && !err && <Spinner label="Loading complaint…" />}
        {err && <div className="p-6 text-flag">{err}</div>}
        {data && (
          <>
            <div className="sticky top-0 bg-white px-5 pt-5 pb-3 border-b border-black/5">
              <div className="flex items-start justify-between">
                <div className="flex items-center gap-3">
                  <SeverityBadge value={data.priority} />
                  <StatusPill status={data.status} />
                </div>
                <button onClick={onClose} className="text-gray-400 hover:text-gray-700 text-xl leading-none">×</button>
              </div>
              <h2 className="mt-2 text-xl font-bold text-ink">{data.ward || prettyCat(data.category)}</h2>
              <p className="mt-1 flex items-center gap-1 text-sm text-gray-500"><span>📍</span>{data.locationText || 'Location not specified'}</p>
              <p className="font-mono text-xs text-gray-400 mt-1">{data.trackingCode}</p>
            </div>

            <div className="p-5 space-y-5">
              {photo && (
                <img src={photo} alt="evidence" className="w-full h-52 object-cover rounded-xl border border-black/10"
                  onError={(e) => { e.currentTarget.style.display = 'none'; }} />
              )}

              <div className="grid grid-cols-3 gap-3">
                <Stat value={reportCount} label="Reports" accent="text-maroon" />
                <Stat value={daysSince(data.submittedAt)} label="Days" accent="text-flag" />
                <Stat value={prettyCat(data.category)} label="Type" accent="text-maroon" small />
              </div>

              {/* Accountability hierarchy: Ward -> Department -> Supervisor -> Officer, with the ward Watchdog overseeing */}
              <div>
                <p className="text-xs font-bold tracking-wider text-gray-400 mb-2">ACCOUNTABILITY HIERARCHY</p>
                <div className="rounded-xl border border-black/10 p-4">
                  <Node tone="maroon" caption="Your Ward" title={data.ward || '—'} />
                  <Connector />
                  <Node tone="blue" caption="Responsible Department" title={data.departmentName || 'Grievance Cell'} />
                  <Connector />
                  <Node tone="violet" caption="Supervisor" title={data.supervisorName || 'Unassigned'} muted={!data.supervisorName} />
                  <Connector />
                  <Node tone="green" caption="Field Officer" title={data.assignedOfficerName || 'Awaiting assignment'} muted={!data.assignedOfficerName} />
                  <Connector />
                  <Node tone="maroon" caption="Ward Watchdog" title={data.watchdogName || 'Unassigned'} muted={!data.watchdogName} />
                </div>
              </div>

              {data.resolutionNote && (
                <div className="rounded-xl bg-emerald-50 border border-emerald-200 p-3 text-sm text-emerald-800">
                  <span className="font-semibold">Resolution note: </span>{data.resolutionNote}
                </div>
              )}

              <p className="text-center text-xs text-emerald-600">✓ All reports are anonymous</p>
              {msg && <p className="text-center text-sm font-medium text-maroon">{msg}</p>}

              {/* Identity / OTP panel */}
              {auth && canGiveFeedback && (
                <div className="rounded-xl border border-maroon-200 bg-maroon-50/40 p-4 animate-fadeUp">
                  <div className="flex items-center justify-between">
                    <p className="text-sm font-bold text-ink">Verify it's you</p>
                    <button onClick={() => { setAuth(null); setPending(null); }} className="text-gray-400 text-sm">cancel</button>
                  </div>
                  <p className="text-xs text-gray-500 mt-1">
                    Only the citizen who filed this complaint can confirm it. Prove ownership of the phone you reported with,
                    or use the anonymous token you were given.
                  </p>

                  {auth === 'choose' && (
                    <div className="mt-3 grid grid-cols-2 gap-2">
                      <Btn variant="primary" onClick={() => setAuth('otp')}>📱 I used my phone</Btn>
                      <Btn variant="ghost" onClick={() => setAuth('token')}>🔑 I have a token</Btn>
                    </div>
                  )}

                  {auth === 'otp' && (
                    <div className="mt-3 space-y-3">
                      <Field label="Phone number used when reporting">
                        <input className={inputCls} value={phone} onChange={(e) => setPhone(e.target.value)} placeholder="e.g. 98XXXXXX21" />
                      </Field>
                      {!otpSent ? (
                        <Btn variant="primary" className="w-full" disabled={busy || !phone} onClick={sendOtp}>Send OTP</Btn>
                      ) : (
                        <>
                          {devCode && (
                            <p className="text-xs rounded bg-amber-50 border border-amber-200 px-2 py-1 text-amber-700">
                              Dev mode — your code is <span className="font-mono font-bold">{devCode}</span>
                            </p>
                          )}
                          <Field label="Enter the 6-digit code">
                            <input className={`${inputCls} font-mono tracking-widest`} value={code}
                              onChange={(e) => setCode(e.target.value)} placeholder="******" />
                          </Field>
                          <Btn variant="primary" className="w-full" disabled={busy || !code}
                            onClick={verifyOtp}>Verify &amp; {pending ? 'confirm resolved' : 'flag'}</Btn>
                        </>
                      )}
                    </div>
                  )}

                  {auth === 'token' && (
                    <div className="mt-3 space-y-3">
                      <Field label="Anonymous reporter token" hint="Shown once, right after you submitted the complaint.">
                        <input className={`${inputCls} font-mono text-xs`} value={reporterToken}
                          onChange={(e) => setReporterToken(e.target.value)} placeholder="xxxxxxxx-xxxx-..." />
                      </Field>
                      <Btn variant="primary" className="w-full" disabled={busy || !reporterToken}
                        onClick={() => postFeedback(pending, { reporterToken })}>
                        Submit {pending ? 'verification' : 'flag'}
                      </Btn>
                    </div>
                  )}
                </div>
              )}
            </div>

            {/* Footer actions */}
            {canGiveFeedback ? (
              <div className="sticky bottom-0 grid grid-cols-2 gap-3 bg-white p-4 border-t border-black/5">
                <Btn variant="verify" disabled={busy} onClick={() => startAction(true)}>✓ Verify Cleanup</Btn>
                <Btn variant="flag" disabled={busy} onClick={() => startAction(false)}>⚑ Flag as Incorrect</Btn>
              </div>
            ) : (
              <div className="sticky bottom-0 bg-white p-4 border-t border-black/5 text-center text-sm text-gray-500">
                Verify / Flag becomes available once the department claims a resolution.
              </div>
            )}
          </>
        )}
      </div>
    </div>
  );
}

const TONES = {
  maroon: 'bg-maroon-50 border-maroon-200 text-maroon',
  blue: 'bg-blue-50 border-blue-100 text-blue-700',
  violet: 'bg-violet-50 border-violet-100 text-violet-700',
  green: 'bg-emerald-50 border-emerald-100 text-emerald-700',
};

function Node({ tone, caption, title, muted }) {
  return (
    <div className="flex flex-col items-center">
      <div className={`rounded-lg border px-4 py-2 text-center min-w-[180px] ${TONES[tone]} ${muted ? 'opacity-60' : ''}`}>
        <div className="text-[10px] uppercase tracking-wide opacity-70">{caption}</div>
        <div className="font-bold text-sm">{title}</div>
      </div>
    </div>
  );
}
const Connector = () => <div className="h-4 w-px bg-gray-300 mx-auto" />;

function Stat({ value, label, accent, small }) {
  return (
    <div className="rounded-xl border border-black/10 px-3 py-3">
      <div className={`font-mono font-bold ${small ? 'text-sm leading-tight' : 'text-2xl'} ${accent}`}>{value}</div>
      <div className="text-xs text-gray-500 mt-0.5">{label}</div>
    </div>
  );
}
