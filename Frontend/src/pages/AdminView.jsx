import { useEffect, useState } from 'react';
import client, { unwrap } from '../api/client';
import StaffShell from '../components/StaffShell';
import { Spinner, Btn, Field, inputCls } from '../components/ui';

export default function AdminView() {
  const [tab, setTab] = useState('Users');
  return (
    <StaffShell title="Administration" tabs={['Users', 'SLA Rules', 'Departments', 'Wards']} active={tab} onTab={setTab}>
      {tab === 'Users' && <Users />}
      {tab === 'SLA Rules' && <SlaRules />}
      {tab === 'Departments' && <Departments />}
      {tab === 'Wards' && <Wards />}
    </StaffShell>
  );
}

const ROLES = ['OFFICER', 'SUPERVISOR', 'WATCHDOG', 'ADMIN'];

function Users() {
  const [users, setUsers] = useState(null);
  const [depts, setDepts] = useState([]);
  const [wards, setWards] = useState([]);
  const [form, setForm] = useState({ username: '', email: '', password: '', fullName: '', role: 'OFFICER', departmentId: '', wardId: '' });
  const [editingId, setEditingId] = useState(null);
  const [editForm, setEditForm] = useState(null);
  const [err, setErr] = useState('');
  const [busy, setBusy] = useState(false);

  const load = () => unwrap(client.get('/admin/users')).then(setUsers).catch(() => setUsers([]));
  useEffect(() => {
    load();
    unwrap(client.get('/departments/public/all')).then(setDepts).catch(() => {});
    unwrap(client.get('/wards/public/all')).then(setWards).catch(() => {});
  }, []);

  const set = (k) => (e) => setForm({ ...form, [k]: e.target.value });
  const create = async () => {
    setBusy(true); setErr('');
    try {
      await unwrap(client.post('/admin/users', { ...form, departmentId: form.departmentId || null, wardId: form.wardId || null }));
      setForm({ username: '', email: '', password: '', fullName: '', role: 'OFFICER', departmentId: '', wardId: '' });
      load();
    } catch (e) { setErr(e.message); } finally { setBusy(false); }
  };

  const changeRole = async (id, role) => {
    try { await unwrap(client.patch(`/admin/users/${id}/role`, { role, departmentId: null, wardId: null })); load(); }
    catch (e) { alert(e.message); }
  };

  const startEdit = (u) => {
    setEditingId(u.id);
    setEditForm({ fullName: u.fullName || '', email: u.email || '', departmentId: u.departmentId || '', wardId: u.wardId || '' });
  };
  const cancelEdit = () => { setEditingId(null); setEditForm(null); };
  const saveEdit = async (id) => {
    try {
      await unwrap(client.put(`/admin/users/${id}`, editForm));
      cancelEdit();
      load();
    } catch (e) { alert(e.message); }
  };

  const deactivate = async (u) => {
    if (!confirm(`Deactivate ${u.fullName || u.username}? They will no longer be able to log in.`)) return;
    try { await unwrap(client.delete(`/admin/users/${u.id}`)); load(); }
    catch (e) { alert(e.message); }
  };

  if (!users) return <Spinner />;
  return (
    <div className="grid gap-6 lg:grid-cols-3">
      <div className="lg:col-span-2 rounded-2xl border border-black/10 bg-white overflow-hidden">
        <div className="overflow-x-auto">
        <table className="w-full text-sm">
          <thead className="bg-cream text-gray-500 text-left">
            <tr>
              <th className="px-4 py-3 whitespace-nowrap">User</th>
              <th className="px-4 py-3 whitespace-nowrap">Email</th>
              <th className="px-4 py-3 whitespace-nowrap">Dept</th>
              <th className="px-4 py-3 whitespace-nowrap">Ward</th>
              <th className="px-4 py-3 whitespace-nowrap">Role</th>
              <th className="px-4 py-3 whitespace-nowrap">Status</th>
              <th className="px-4 py-3 whitespace-nowrap"></th>
            </tr>
          </thead>
          <tbody>
            {users.map((u) => editingId === u.id ? (
              <tr key={u.id} className="border-t border-black/5 bg-cream/40">
                <td className="px-4 py-3">
                  <input className={inputCls} placeholder="Full name" value={editForm.fullName}
                    onChange={(e) => setEditForm({ ...editForm, fullName: e.target.value })} />
                  <div className="font-mono text-xs text-gray-400 mt-1">{u.username}</div>
                </td>
                <td className="px-4 py-3">
                  <input className={inputCls} value={editForm.email}
                    onChange={(e) => setEditForm({ ...editForm, email: e.target.value })} />
                </td>
                <td className="px-4 py-3">
                  <select className={inputCls} value={editForm.departmentId}
                    onChange={(e) => setEditForm({ ...editForm, departmentId: e.target.value })}>
                    <option value="">None</option>
                    {depts.map((d) => <option key={d.id} value={d.id}>{d.name}</option>)}
                  </select>
                </td>
                <td className="px-4 py-3">
                  <select className={inputCls} value={editForm.wardId}
                    onChange={(e) => setEditForm({ ...editForm, wardId: e.target.value })}>
                    <option value="">None</option>
                    {wards.map((w) => <option key={w.id} value={w.id}>{w.name} ({w.code})</option>)}
                  </select>
                </td>
                <td className="px-4 py-3 text-xs text-gray-400">{u.role}</td>
                <td className="px-4 py-3 text-xs text-gray-400">{u.active ? 'Active' : 'Inactive'}</td>
                <td className="px-4 py-3 whitespace-nowrap">
                  <button className="text-maroon font-semibold text-xs mr-3" onClick={() => saveEdit(u.id)}>Save</button>
                  <button className="text-gray-400 text-xs" onClick={cancelEdit}>Cancel</button>
                </td>
              </tr>
            ) : (
              <tr key={u.id} className={`border-t border-black/5 ${!u.active ? 'opacity-50' : ''}`}>
                <td className="px-4 py-3 whitespace-nowrap"><div className="font-semibold">{u.fullName}</div><div className="font-mono text-xs text-gray-400">{u.username}</div></td>
                <td className="px-4 py-3 text-gray-600 whitespace-nowrap">{u.email}</td>
                <td className="px-4 py-3 text-gray-600 whitespace-nowrap">{u.departmentName || '—'}</td>
                <td className="px-4 py-3 text-gray-600 whitespace-nowrap">{u.wardName || '—'}</td>
                <td className="px-4 py-3">
                  <select className="min-w-[120px] rounded-lg border border-black/10 py-1 pl-2 pr-7 text-xs font-semibold" value={u.role}
                    onChange={(e) => changeRole(u.id, e.target.value)} disabled={!u.active}>
                    {ROLES.map((r) => <option key={r} value={r}>{r}</option>)}
                  </select>
                </td>
                <td className="px-4 py-3 whitespace-nowrap">
                  {u.active
                    ? <span className="text-xs font-semibold text-verify">Active</span>
                    : <span className="text-xs font-semibold text-gray-400">Inactive</span>}
                </td>
                <td className="px-4 py-3 whitespace-nowrap">
                  <button className="text-maroon font-semibold text-xs mr-3" onClick={() => startEdit(u)}>Edit</button>
                  {u.active && <button className="text-flag font-semibold text-xs" onClick={() => deactivate(u)}>Deactivate</button>}
                </td>
              </tr>
            ))}
          </tbody>
        </table>
        </div>
      </div>

      <div className="rounded-2xl border border-black/10 bg-white p-5 space-y-3 h-fit">
        <h3 className="font-bold text-ink">Add staff user</h3>
        <Field label="Username"><input className={inputCls} value={form.username} onChange={set('username')} /></Field>
        <Field label="Full name"><input className={inputCls} value={form.fullName} onChange={set('fullName')} /></Field>
        <Field label="Email"><input className={inputCls} value={form.email} onChange={set('email')} /></Field>
        <Field label="Password"><input className={inputCls} value={form.password} onChange={set('password')} /></Field>
        <Field label="Role">
          <select className={inputCls} value={form.role} onChange={set('role')}>{ROLES.map((r) => <option key={r}>{r}</option>)}</select>
        </Field>
        <Field label="Department">
          <select className={inputCls} value={form.departmentId} onChange={set('departmentId')}>
            <option value="">None</option>
            {depts.map((d) => <option key={d.id} value={d.id}>{d.name}</option>)}
          </select>
        </Field>
        <Field label="Ward" hint="Officers/supervisors serve one ward+department; watchdogs oversee a whole ward.">
          <select className={inputCls} value={form.wardId} onChange={set('wardId')}>
            <option value="">None</option>
            {wards.map((w) => <option key={w.id} value={w.id}>{w.name} ({w.code})</option>)}
          </select>
        </Field>
        {err && <p className="text-flag text-sm">{err}</p>}
        <Btn variant="primary" className="w-full" disabled={busy || !form.username || !form.password} onClick={create}>Create user</Btn>
      </div>
    </div>
  );
}

function SlaRules() {
  const [rules, setRules] = useState(null);
  const load = () => unwrap(client.get('/admin/sla-rules')).then(setRules).catch(() => setRules([]));
  useEffect(() => { load(); }, []);

  const save = async (r) => {
    try {
      await unwrap(client.put(`/admin/sla-rules/${r.id}`, {
        category: r.category, level1Hours: Number(r.level1Hours), level2Hours: Number(r.level2Hours), level3Hours: Number(r.level3Hours),
      }));
      load();
    } catch (e) { alert(e.message); }
  };

  if (!rules) return <Spinner />;
  return (
    <div className="rounded-2xl border border-black/10 bg-white overflow-hidden max-w-3xl">
      <table className="w-full text-sm">
        <thead className="bg-cream text-gray-500 text-left">
          <tr><th className="px-4 py-3">Category</th><th className="px-4 py-3">L1 (hrs)</th><th className="px-4 py-3">L2</th><th className="px-4 py-3">L3</th><th></th></tr>
        </thead>
        <tbody>
          {rules.map((r, i) => (
            <Row key={r.id} rule={r} onSave={save} />
          ))}
        </tbody>
      </table>
    </div>
  );
}

function Row({ rule, onSave }) {
  const [r, setR] = useState(rule);
  const set = (k) => (e) => setR({ ...r, [k]: e.target.value });
  const dirty = r.level1Hours != rule.level1Hours || r.level2Hours != rule.level2Hours || r.level3Hours != rule.level3Hours;
  return (
    <tr className="border-t border-black/5">
      <td className="px-4 py-3 font-semibold">{r.category}</td>
      {['level1Hours', 'level2Hours', 'level3Hours'].map((k) => (
        <td key={k} className="px-4 py-3"><input className="w-20 rounded-lg border border-black/10 px-2 py-1 font-mono" value={r[k]} onChange={set(k)} /></td>
      ))}
      <td className="px-4 py-3">{dirty && <button className="text-maroon font-semibold" onClick={() => onSave(r)}>Save</button>}</td>
    </tr>
  );
}

function Departments() {
  const [depts, setDepts] = useState(null);
  const [newDept, setNewDept] = useState({ name: '', code: '', district: '', state: '' });
  const [editingId, setEditingId] = useState(null);
  const [editForm, setEditForm] = useState(null);
  const [err, setErr] = useState('');
  const [busy, setBusy] = useState(false);

  const load = () => unwrap(client.get('/departments/public/all')).then(setDepts).catch(() => setDepts([]));
  useEffect(() => { load(); }, []);

  const createDept = async () => {
    setBusy(true); setErr('');
    try {
      await unwrap(client.post('/admin/departments', newDept));
      setNewDept({ name: '', code: '', district: '', state: '' });
      load();
    } catch (e) { setErr(e.message); } finally { setBusy(false); }
  };

  const startEdit = (d) => { setEditingId(d.id); setEditForm({ name: d.name, district: d.district || '', state: d.state || '' }); };
  const cancelEdit = () => { setEditingId(null); setEditForm(null); };
  const saveEdit = async (id) => {
    try { await unwrap(client.put(`/admin/departments/${id}`, editForm)); cancelEdit(); load(); }
    catch (e) { alert(e.message); }
  };
  const deleteDept = async (d) => {
    if (!confirm(`Delete department ${d.name}? This cannot be undone.`)) return;
    try { await unwrap(client.delete(`/admin/departments/${d.id}`)); load(); }
    catch (e) { alert(e.message); }
  };

  if (!depts) return <Spinner />;
  return (
    <div className="grid gap-6 lg:grid-cols-3">
      <div className="lg:col-span-2 grid gap-3 sm:grid-cols-2">
        {depts.map((d) => editingId === d.id ? (
          <div key={d.id} className="rounded-2xl border border-black/10 bg-white p-5 space-y-2">
            <input className={inputCls} value={editForm.name} placeholder="Name"
              onChange={(e) => setEditForm({ ...editForm, name: e.target.value })} />
            <input className={inputCls} value={editForm.district} placeholder="District"
              onChange={(e) => setEditForm({ ...editForm, district: e.target.value })} />
            <input className={inputCls} value={editForm.state} placeholder="State"
              onChange={(e) => setEditForm({ ...editForm, state: e.target.value })} />
            <div className="flex gap-3 pt-1">
              <button className="text-maroon font-semibold text-xs" onClick={() => saveEdit(d.id)}>Save</button>
              <button className="text-gray-400 text-xs" onClick={cancelEdit}>Cancel</button>
            </div>
          </div>
        ) : (
          <div key={d.id} className="rounded-2xl border border-black/10 bg-white p-5">
            <div className="flex items-center justify-between">
              <h3 className="font-bold text-ink">{d.name}</h3>
              <span className="font-mono text-xs rounded bg-maroon-50 text-maroon px-2 py-0.5">{d.code}</span>
            </div>
            <p className="text-sm text-gray-500 mt-1">{d.district}, {d.state}</p>
            <div className="mt-3 flex gap-3">
              <button className="text-maroon font-semibold text-xs" onClick={() => startEdit(d)}>Edit</button>
              <button className="text-flag font-semibold text-xs" onClick={() => deleteDept(d)}>Delete</button>
            </div>
          </div>
        ))}
      </div>

      <div className="rounded-2xl border border-black/10 bg-white p-5 space-y-3 h-fit">
        <h3 className="font-bold text-ink">Add department</h3>
        <Field label="Name"><input className={inputCls} value={newDept.name} onChange={(e) => setNewDept({ ...newDept, name: e.target.value })} /></Field>
        <Field label="Code" hint="e.g. WATER, SWM"><input className={inputCls} value={newDept.code} onChange={(e) => setNewDept({ ...newDept, code: e.target.value })} /></Field>
        <Field label="District (optional)"><input className={inputCls} value={newDept.district} onChange={(e) => setNewDept({ ...newDept, district: e.target.value })} /></Field>
        <Field label="State (optional)"><input className={inputCls} value={newDept.state} onChange={(e) => setNewDept({ ...newDept, state: e.target.value })} /></Field>
        {err && <p className="text-flag text-sm">{err}</p>}
        <Btn variant="primary" className="w-full" disabled={busy || !newDept.name || !newDept.code} onClick={createDept}>Create department</Btn>
      </div>
    </div>
  );
}

function Wards() {
  const [wards, setWards] = useState(null);
  const [allDepts, setAllDepts] = useState([]);
  const [picker, setPicker] = useState({});
  const [newWard, setNewWard] = useState({ code: '', name: '', zone: '' });
  const [editingId, setEditingId] = useState(null);
  const [editForm, setEditForm] = useState(null);
  const [err, setErr] = useState('');
  const [busy, setBusy] = useState(false);

  const load = () => unwrap(client.get('/admin/wards')).then(setWards).catch(() => setWards([]));
  useEffect(() => { load(); unwrap(client.get('/departments/public/all')).then(setAllDepts).catch(() => {}); }, []);

  const createWard = async () => {
    setBusy(true); setErr('');
    try {
      await unwrap(client.post('/admin/wards', newWard));
      setNewWard({ code: '', name: '', zone: '' });
      load();
    } catch (e) { setErr(e.message); } finally { setBusy(false); }
  };

  const linkDept = async (wardId) => {
    const departmentId = picker[wardId];
    if (!departmentId) return;
    try { await unwrap(client.post(`/admin/wards/${wardId}/departments`, { departmentId })); load(); }
    catch (e) { alert(e.message); }
  };

  const unlinkDept = async (wardId, deptId) => {
    try { await unwrap(client.delete(`/admin/wards/${wardId}/departments/${deptId}`)); load(); }
    catch (e) { alert(e.message); }
  };

  const startEdit = (w) => { setEditingId(w.id); setEditForm({ name: w.name, zone: w.zone || '' }); };
  const cancelEdit = () => { setEditingId(null); setEditForm(null); };
  const saveEdit = async (id) => {
    try { await unwrap(client.put(`/admin/wards/${id}`, editForm)); cancelEdit(); load(); }
    catch (e) { alert(e.message); }
  };
  const deleteWard = async (w) => {
    if (!confirm(`Delete ward ${w.name} (${w.code})? This cannot be undone.`)) return;
    try { await unwrap(client.delete(`/admin/wards/${w.id}`)); load(); }
    catch (e) { alert(e.message); }
  };

  if (!wards) return <Spinner />;
  return (
    <div className="grid gap-6 lg:grid-cols-3">
      <div className="lg:col-span-2 grid gap-3 sm:grid-cols-2">
        {wards.map((w) => {
          const linkedIds = new Set(w.departments.map((d) => d.id));
          const available = allDepts.filter((d) => !linkedIds.has(d.id));
          const editing = editingId === w.id;
          return (
            <div key={w.id} className="rounded-2xl border border-black/10 bg-white p-5">
              {editing ? (
                <div className="space-y-2">
                  <input className={inputCls} value={editForm.name} placeholder="Name"
                    onChange={(e) => setEditForm({ ...editForm, name: e.target.value })} />
                  <input className={inputCls} value={editForm.zone} placeholder="Zone"
                    onChange={(e) => setEditForm({ ...editForm, zone: e.target.value })} />
                  <div className="flex gap-3 pt-1">
                    <button className="text-maroon font-semibold text-xs" onClick={() => saveEdit(w.id)}>Save</button>
                    <button className="text-gray-400 text-xs" onClick={cancelEdit}>Cancel</button>
                  </div>
                </div>
              ) : (
                <>
                  <div className="flex items-center justify-between">
                    <h3 className="font-bold text-ink">{w.name}</h3>
                    <span className="font-mono text-xs rounded bg-maroon-50 text-maroon px-2 py-0.5">{w.code}</span>
                  </div>
                  {w.zone && <p className="text-xs text-gray-400 mt-0.5">{w.zone}</p>}
                  <div className="mt-2 flex gap-3">
                    <button className="text-maroon font-semibold text-xs" onClick={() => startEdit(w)}>Edit</button>
                    <button className="text-flag font-semibold text-xs" onClick={() => deleteWard(w)}>Delete</button>
                  </div>
                </>
              )}
              <div className="mt-3 flex flex-wrap gap-1.5">
                {w.departments.length === 0 && <span className="text-xs text-gray-400">No departments linked yet</span>}
                {w.departments.map((d) => (
                  <span key={d.id} className="inline-flex items-center gap-1 rounded-full bg-cream border border-black/10 px-2.5 py-1 text-xs font-semibold">
                    {d.code}
                    <button onClick={() => unlinkDept(w.id, d.id)} className="text-gray-400 hover:text-flag" title={`Remove ${d.name}`}>×</button>
                  </span>
                ))}
              </div>
              {available.length > 0 && (
                <div className="mt-3 flex gap-2">
                  <select className="flex-1 rounded-lg border border-black/10 px-2 py-1.5 text-xs"
                    value={picker[w.id] || ''} onChange={(e) => setPicker({ ...picker, [w.id]: e.target.value })}>
                    <option value="">Add department…</option>
                    {available.map((d) => <option key={d.id} value={d.id}>{d.name}</option>)}
                  </select>
                  <button onClick={() => linkDept(w.id)} className="text-xs font-semibold text-maroon">Add</button>
                </div>
              )}
            </div>
          );
        })}
      </div>

      <div className="rounded-2xl border border-black/10 bg-white p-5 space-y-3 h-fit">
        <h3 className="font-bold text-ink">Add ward</h3>
        <Field label="Code" hint="e.g. K/W, H/E"><input className={inputCls} value={newWard.code} onChange={(e) => setNewWard({ ...newWard, code: e.target.value })} /></Field>
        <Field label="Name"><input className={inputCls} value={newWard.name} onChange={(e) => setNewWard({ ...newWard, name: e.target.value })} /></Field>
        <Field label="Zone (optional)"><input className={inputCls} value={newWard.zone} onChange={(e) => setNewWard({ ...newWard, zone: e.target.value })} /></Field>
        {err && <p className="text-flag text-sm">{err}</p>}
        <Btn variant="primary" className="w-full" disabled={busy || !newWard.code || !newWard.name} onClick={createWard}>Create ward</Btn>
      </div>
    </div>
  );
}
