import { Routes, Route, Navigate } from 'react-router-dom';
import { useAuth } from './context/AuthContext';
import { dashFor } from './components/TopBar';

import PublicMap from './pages/PublicMap';
import PublicDashboard from './pages/PublicDashboard';
import SubmitComplaint from './pages/SubmitComplaint';
import TrackComplaint from './pages/TrackComplaint';
import Login from './pages/Login';
import StaffDashboard from './pages/StaffDashboard';
import WatchdogView from './pages/WatchdogView';
import AdminView from './pages/AdminView';

function Protected({ roles, children }) {
  const { user } = useAuth();
  if (!user) return <Navigate to="/login" replace />;
  if (roles && !roles.includes(user.role)) return <Navigate to={dashFor(user.role)} replace />;
  return children;
}

export default function App() {
  return (
    <Routes>
      <Route path="/" element={<PublicMap />} />
      <Route path="/dashboard" element={<PublicDashboard />} />
      <Route path="/report" element={<SubmitComplaint />} />
      <Route path="/track" element={<TrackComplaint />} />
      <Route path="/login" element={<Login />} />
      <Route path="/staff" element={<Protected roles={['OFFICER', 'SUPERVISOR']}><StaffDashboard /></Protected>} />
      <Route path="/watchdog" element={<Protected roles={['WATCHDOG', 'ADMIN']}><WatchdogView /></Protected>} />
      <Route path="/admin" element={<Protected roles={['ADMIN']}><AdminView /></Protected>} />
      <Route path="*" element={<Navigate to="/" replace />} />
    </Routes>
  );
}
