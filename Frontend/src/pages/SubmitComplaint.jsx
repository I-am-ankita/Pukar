import { useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import { MapContainer, TileLayer, Marker, useMapEvents } from 'react-leaflet';
import L from 'leaflet';
import client, { unwrap } from '../api/client';
import TopBar from '../components/TopBar';
import { Btn, Field, inputCls, SeverityBadge } from '../components/ui';

const MUMBAI_CENTER = [19.076, 72.8777];
const MUMBAI_BOUNDS = [[18.84, 72.72], [19.32, 73.03]];
const MIN_LAT = 18.89, MAX_LAT = 19.27, MIN_LNG = 72.77, MAX_LNG = 72.98;

const withinMumbai = (lat, lng) => lat >= MIN_LAT && lat <= MAX_LAT && lng >= MIN_LNG && lng <= MAX_LNG;

const pinIcon = L.divIcon({
  className: 'pukar-pin',
  html: '<div class="pukar-pin-dot"></div>',
  iconSize: [20, 20],
  iconAnchor: [10, 10],
});

function LocationPicker({ position, onPick }) {
  useMapEvents({
    click(e) {
      const { lat, lng } = e.latlng;
      if (!withinMumbai(lat, lng)) return;
      onPick(lat, lng);
    },
  });
  return position ? (
    <Marker position={position} icon={pinIcon} draggable
      eventHandlers={{ dragend: (e) => { const { lat, lng } = e.target.getLatLng(); onPick(lat, lng); } }} />
  ) : null;
}

export default function SubmitComplaint() {
  const [cats, setCats] = useState([]);
  const [wards, setWards] = useState([]);
  const [form, setForm] = useState({
    category: '', subCategory: '', description: '', locationText: '', ward: '', wardId: '',
    latitude: '', longitude: '', reporterPhone: '',
  });
  const [file, setFile] = useState(null);
  const [result, setResult] = useState(null);
  const [err, setErr] = useState('');
  const [busy, setBusy] = useState(false);

  useEffect(() => {
    unwrap(client.get('/meta/categories')).then(setCats).catch(() => setCats([]));
    unwrap(client.get('/wards/public/all')).then(setWards).catch(() => setWards([]));
  }, []);

  const set = (k) => (e) => setForm({ ...form, [k]: e.target.value });

  const setWard = (e) => {
    const wardId = e.target.value;
    const w = wards.find((x) => x.id === wardId);
    setForm((f) => ({ ...f, wardId, ward: w ? w.name : '' }));
  };

  const setLatLng = (lat, lng) => setForm((f) => ({ ...f, latitude: lat.toFixed(6), longitude: lng.toFixed(6) }));

  const useLocation = () => {
    if (!navigator.geolocation) return;
    setErr('');
    navigator.geolocation.getCurrentPosition((pos) => {
      if (!withinMumbai(pos.coords.latitude, pos.coords.longitude)) {
        setErr('Your current location is outside Mumbai — PUKAR only accepts complaints within Mumbai city limits.');
        return;
      }
      setLatLng(pos.coords.latitude, pos.coords.longitude);
    });
  };

  const pinPosition = form.latitude && form.longitude ? [Number(form.latitude), Number(form.longitude)] : null;

  const submit = async () => {
    setErr('');
    if (pinPosition && !withinMumbai(pinPosition[0], pinPosition[1])) {
      setErr('Location must be within Mumbai city limits.');
      return;
    }
    setBusy(true);
    try {
      const payload = {
        ...form,
        wardId: form.wardId || null,
        latitude: form.latitude ? Number(form.latitude) : null,
        longitude: form.longitude ? Number(form.longitude) : null,
        reporterPhone: form.reporterPhone || null,
      };
      const res = await unwrap(client.post('/complaints', payload));
      if (file) {
        const fd = new FormData();
        fd.append('file', file);
        await client.post(`/complaints/track/${res.trackingCode}/evidence`, fd);
      }
      setResult(res);
    } catch (e) {
      setErr(e.message);
    } finally {
      setBusy(false);
    }
  };

  if (result) {
    return (
      <div className="min-h-screen bg-cream">
        <TopBar />
        <div className="mx-auto max-w-lg px-4 pt-28 pb-16">
          <div className="rounded-2xl border border-emerald-200 bg-white p-6 text-center">
            <div className="text-4xl">✅</div>
            <h1 className="mt-2 text-xl font-bold text-ink">Complaint Registered</h1>
            <p className="text-gray-500 mt-1">{result.message}</p>
            <div className="mt-4 rounded-xl bg-cream border border-black/10 p-4">
              <div className="text-xs text-gray-500">Your tracking code</div>
              <div className="font-mono text-2xl font-bold text-maroon">{result.trackingCode}</div>
            </div>
            {result.reporterToken && (
              <div className="mt-3 rounded-xl bg-amber-50 border border-amber-200 p-3 text-left text-sm">
                <span className="font-semibold">Anonymous token (save this!): </span>
                <span className="font-mono break-all">{result.reporterToken}</span>
              </div>
            )}
            <div className="mt-4 flex items-center justify-center gap-2 text-sm">
              <span className="text-gray-500">Assigned priority:</span> <SeverityBadge value={result.priority} />
            </div>
            <p className="text-xs text-gray-400 mt-2">Estimated first SLA: {result.estimatedSlaHours} hours</p>
            <div className="mt-5 flex gap-3 justify-center">
              <Btn as="link" to="/track" variant="ghost">Track it</Btn>
              <Btn as="link" to="/" variant="primary">Back to map</Btn>
            </div>
          </div>
        </div>
      </div>
    );
  }

  return (
    <div className="min-h-screen bg-cream">
      <TopBar />
      <div className="mx-auto max-w-lg px-4 pt-24 pb-16">
        <h1 className="text-2xl font-bold text-ink">Report an Issue</h1>
        <p className="text-gray-500 mb-6">Anonymous by default. No login required.</p>

        <div className="space-y-4 rounded-2xl border border-black/10 bg-white p-5">
          <Field label="Category">
            <select className={inputCls} value={form.category} onChange={set('category')}>
              <option value="">Select a category…</option>
              {cats.map((c) => <option key={c.code} value={c.code}>{c.label}</option>)}
            </select>
          </Field>
          <Field label="Sub-category (optional)">
            <input className={inputCls} value={form.subCategory} onChange={set('subCategory')} placeholder="e.g. Pothole, Bribe demand" />
          </Field>
          <Field label="Description">
            <textarea className={inputCls} rows={4} value={form.description} onChange={set('description')}
              placeholder="Describe what happened (min 5 characters)…" />
          </Field>
          <div className="grid grid-cols-2 gap-3">
            <Field label="Ward">
              <select className={inputCls} value={form.wardId} onChange={setWard}>
                <option value="">Select your ward…</option>
                {wards.map((w) => <option key={w.id} value={w.id}>{w.name} ({w.code})</option>)}
              </select>
            </Field>
            <Field label="Landmark"><input className={inputCls} value={form.locationText} onChange={set('locationText')} placeholder="Near…" /></Field>
          </div>

          <Field label="Location (tap or drag the pin — Mumbai only)">
            <div className="h-56 w-full overflow-hidden rounded-xl border border-black/10">
              <MapContainer center={pinPosition || MUMBAI_CENTER} zoom={pinPosition ? 15 : 11}
                maxBounds={MUMBAI_BOUNDS} maxBoundsViscosity={1.0} minZoom={10}
                className="h-full w-full" zoomControl={false}>
                <TileLayer attribution='&copy; OpenStreetMap'
                  url="https://{s}.basemaps.cartocdn.com/light_all/{z}/{x}/{y}{r}.png" />
                <LocationPicker position={pinPosition} onPick={setLatLng} />
              </MapContainer>
            </div>
          </Field>
          <button onClick={useLocation} className="text-sm font-semibold text-maroon">📍 Use my current location</button>

          <Field label="Photo evidence (optional)">
            <input type="file" accept="image/*" onChange={(e) => setFile(e.target.files?.[0] || null)} className="text-sm" />
          </Field>
          <Field label="Phone (optional)" hint="Stored only as an irreversible hash — never in plain text. Provide it to confirm the resolution later via a one-time OTP. Leave blank to stay fully anonymous and use your reporter token instead.">
            <input className={inputCls} value={form.reporterPhone} onChange={set('reporterPhone')} placeholder="Optional — enables OTP verification" />
          </Field>

          {err && <p className="text-flag text-sm">{err}</p>}
          <Btn variant="primary" className="w-full" disabled={busy || !form.category || form.description.length < 5} onClick={submit}>
            {busy ? 'Submitting…' : 'Submit Anonymously'}
          </Btn>
          <p className="text-center text-xs text-emerald-600">✓ Your identity is never stored</p>
        </div>

        <p className="text-center text-sm text-gray-400 mt-4">
          Already reported? <Link to="/track" className="text-maroon font-semibold">Track a complaint →</Link>
        </p>
      </div>
    </div>
  );
}
