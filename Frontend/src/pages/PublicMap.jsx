import { useEffect, useMemo, useState } from 'react';
import { MapContainer, TileLayer, Marker, GeoJSON, useMap } from 'react-leaflet';
import MarkerClusterGroup from 'react-leaflet-cluster';
import L from 'leaflet';
import { Link } from 'react-router-dom';
import client, { unwrap } from '../api/client';
import TopBar from '../components/TopBar';
import ComplaintDetailModal from '../components/ComplaintDetailModal';
import { SeverityBadge, StatusPill } from '../components/ui';
import wardBoundaries from '../data/mumbaiWards.json';

const MUMBAI_CENTER = [19.076, 72.8777];
const MUMBAI_BOUNDS = [[18.84, 72.72], [19.32, 73.03]];

const RESOLVED = ['RESOLVED_CLAIMED', 'CITIZEN_VERIFIED', 'CLOSED'];

// severity gradient: yellow (low) -> orange -> red (critical); resolved complaints are always green,
// regardless of priority, so "done" reads instantly on the map.
const PRIORITY_COLORS = { LOW: '#EAB308', MEDIUM: '#F59E0B', HIGH: '#F97316', CRITICAL: '#DC2626' };
const PRIORITY_RANK = { LOW: 0, MEDIUM: 1, HIGH: 2, CRITICAL: 3 };
const RESOLVED_COLOR = '#16A34A';

function sevColor(priority, status) {
  if (status && RESOLVED.includes(status)) return RESOLVED_COLOR;
  return PRIORITY_COLORS[priority] || PRIORITY_COLORS.LOW;
}

function rankOf(priority, status) {
  return status && RESOLVED.includes(status) ? -1 : (PRIORITY_RANK[priority] ?? 0);
}

function bubbleIcon(count, color) {
  const size = count >= 100 ? 64 : count >= 10 ? 54 : 44;
  const text = count >= 1000 ? `${(count / 1000).toFixed(0)}k` : count;
  return L.divIcon({
    className: 'pukar-cluster',
    html: `<div class="pukar-bubble" style="width:${size}px;height:${size}px;background:${color};font-size:${size > 50 ? 15 : 13}px">${text}</div>`,
    iconSize: [size, size],
    iconAnchor: [size / 2, size / 2],
  });
}

// individual (un-clustered) complaint marker — a small dot whose class encodes a severity
// "rank" (resolved = -1, LOW..CRITICAL = 0..3) so the cluster icon below can read it back
// off the child markers when grouped.
function pointIcon(priority, status) {
  return L.divIcon({
    className: `pukar-point pukar-point-rank-${rankOf(priority, status)}`,
    html: `<div class="pukar-dot" style="background:${sevColor(priority, status)}"></div>`,
    iconSize: [16, 16],
    iconAnchor: [8, 8],
  });
}

// cluster bubble: color reflects the worst (highest-rank) complaint it currently groups;
// a cluster of only-resolved complaints shows green, otherwise the usual yellow->red gradient.
function clusterIcon(cluster) {
  const children = cluster.getAllChildMarkers();
  let worstRank = -1;
  for (const m of children) {
    const match = (m.options.icon?.options?.className || '').match(/pukar-point-rank-(-?\d+)/);
    if (match) worstRank = Math.max(worstRank, parseInt(match[1], 10));
  }
  const color = worstRank === -1 ? RESOLVED_COLOR : Object.values(PRIORITY_COLORS)[worstRank];
  return bubbleIcon(cluster.getChildCount(), color);
}

const WARD_STYLE = { color: '#7B1E1E', weight: 1.2, opacity: 0.5, fillColor: '#7B1E1E', fillOpacity: 0.03 };
const WARD_HOVER_STYLE = { ...WARD_STYLE, opacity: 0.9, fillOpacity: 0.12 };

function WardBoundaries({ wardNames }) {
  return (
    <GeoJSON
      data={wardBoundaries}
      style={WARD_STYLE}
      onEachFeature={(feature, layer) => {
        const code = feature.properties.code;
        const name = wardNames[code];
        layer.bindTooltip(name ? `${name} (${code})` : code, { sticky: true, className: 'pukar-ward-tooltip' });
        layer.on('mouseover', () => layer.setStyle(WARD_HOVER_STYLE));
        layer.on('mouseout', () => layer.setStyle(WARD_STYLE));
      }}
    />
  );
}

function FitBounds({ points }) {
  const map = useMap();
  useEffect(() => {
    if (points.length) {
      const b = L.latLngBounds(points.map((p) => [p.lat, p.lng]));
      map.fitBounds(b.pad(0.3), { maxZoom: 13 });
    }
  }, [points, map]);
  return null;
}

export default function PublicMap() {
  const [points, setPoints] = useState([]);
  const [loading, setLoading] = useState(true);
  const [severity, setSeverity] = useState('ALL');
  const [status, setStatus] = useState('ALL');
  const [view, setView] = useState('map');
  const [active, setActive] = useState(null);
  const [wardNames, setWardNames] = useState({});

  useEffect(() => {
    setLoading(true);
    unwrap(client.get('/analytics/heatmap'))
      .then((d) => setPoints(d?.points || []))
      .catch(() => setPoints([]))
      .finally(() => setLoading(false));
    unwrap(client.get('/wards/public/all'))
      .then((ws) => setWardNames(Object.fromEntries((ws || []).map((w) => [w.code, w.name]))))
      .catch(() => setWardNames({}));
  }, []);

  const filtered = useMemo(
    () =>
      points.filter((p) => {
        if (severity !== 'ALL' && p.priority !== severity) return false;
        if (status === 'UNRESOLVED' && RESOLVED.includes(p.status)) return false;
        if (status === 'RESOLVED' && !RESOLVED.includes(p.status)) return false;
        return true;
      }),
    [points, severity, status]
  );

  const reports = filtered.length;
  const activeCount = filtered.filter((p) => !RESOLVED.includes(p.status)).length;

  return (
    <div className="relative h-screen w-screen overflow-hidden">
      <TopBar />

      {/* Filter pills */}
      <div className="absolute top-[64px] left-4 z-[1000] flex gap-2">
        <Pill value={severity} onChange={setSeverity}
          options={[['ALL', 'All Severity'], ['CRITICAL', 'Critical'], ['HIGH', 'High'], ['MEDIUM', 'Medium'], ['LOW', 'Low']]} />
        <Pill value={status} onChange={setStatus}
          options={[['ALL', 'All Status'], ['UNRESOLVED', 'Unresolved'], ['RESOLVED', 'Resolved']]} />
      </div>

      {/* Map / List toggle */}
      <div className="absolute top-[64px] right-4 z-[1000] flex rounded-lg border border-black/10 bg-white overflow-hidden text-sm font-semibold">
        {['map', 'list'].map((v) => (
          <button key={v} onClick={() => setView(v)}
            className={`px-4 py-1.5 capitalize ${view === v ? 'bg-maroon text-white' : 'text-gray-600'}`}>{v}</button>
        ))}
      </div>

      {/* Counter card */}
      <div className="absolute top-[110px] left-4 z-[1000] rounded-xl border border-black/10 bg-white px-4 py-2 shadow-sm">
        <div className="flex items-center gap-3">
          <div><span className="font-mono text-lg font-bold text-maroon">{activeCount}</span> <span className="text-xs text-gray-500">Active</span></div>
          <div className="h-6 w-px bg-gray-200" />
          <div><span className="font-mono text-lg font-bold text-flag">{reports}</span> <span className="text-xs text-gray-500">Reports</span></div>
        </div>
      </div>

      {view === 'map' ? (
        <MapContainer center={MUMBAI_CENTER} zoom={12} minZoom={11}
          maxBounds={MUMBAI_BOUNDS} maxBoundsViscosity={1.0}
          className="h-full w-full" zoomControl={false}>
          <TileLayer
            attribution='&copy; OpenStreetMap'
            url="https://{s}.basemaps.cartocdn.com/light_all/{z}/{x}/{y}{r}.png"
          />
          <FitBounds points={filtered} />
          <WardBoundaries wardNames={wardNames} />
          <MarkerClusterGroup iconCreateFunction={clusterIcon} maxClusterRadius={60} spiderfyOnMaxZoom>
            {filtered.map((p) => (
              <Marker key={p.trackingCode} position={[p.lat, p.lng]} icon={pointIcon(p.priority, p.status)}
                eventHandlers={{ click: () => setActive(p.trackingCode) }} />
            ))}
          </MarkerClusterGroup>
        </MapContainer>
      ) : (
        <ListView loading={loading} points={filtered} onOpen={setActive} />
      )}

      {/* Bottom report bar */}
      <Link to="/report"
        className="absolute bottom-0 inset-x-0 z-[1000] flex items-center justify-center gap-2 bg-ink py-4 text-white font-semibold hover:bg-slate-800">
        <span>▣</span> Report an Issue Anonymously
      </Link>

      {active && <ComplaintDetailModal trackingCode={active} onClose={() => setActive(null)} />}
    </div>
  );
}

function Pill({ value, onChange, options }) {
  return (
    <select value={value} onChange={(e) => onChange(e.target.value)}
      className="rounded-lg border border-black/10 bg-white px-3 py-1.5 text-sm font-semibold text-gray-700 shadow-sm outline-none">
      {options.map(([v, l]) => <option key={v} value={v}>{l}</option>)}
    </select>
  );
}

function ListView({ loading, points, onOpen }) {
  return (
    <div className="h-full w-full overflow-y-auto bg-cream pt-[150px] pb-24 px-4">
      <div className="mx-auto max-w-3xl space-y-3">
        {loading && <p className="text-center text-gray-400 py-10">Loading…</p>}
        {!loading && !points.length && <p className="text-center text-gray-400 py-10">No reports match these filters.</p>}
        {points.map((p) => (
          <button key={p.trackingCode} onClick={() => onOpen(p.trackingCode)}
            className="w-full text-left rounded-xl border border-black/10 bg-white p-4 hover:border-maroon/40 transition">
            <div className="flex items-center justify-between">
              <SeverityBadge value={p.priority} />
              <StatusPill status={p.status} />
            </div>
            <div className="mt-2 font-bold text-ink">{p.ward || p.category}</div>
            <div className="text-sm text-gray-500">{p.locationText}</div>
            <div className="font-mono text-xs text-gray-400 mt-1">{p.trackingCode}</div>
          </button>
        ))}
      </div>
    </div>
  );
}
