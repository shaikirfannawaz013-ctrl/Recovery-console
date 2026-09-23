import { Route, Routes } from 'react-router-dom';
import Layout from './components/Layout';
import ProtectedRoute from './components/ProtectedRoute';
import { StreamProvider } from './context/StreamContext';
import Login from './pages/Login';
import Dashboard from './pages/Dashboard';
import Payments from './pages/Payments';
import PaymentDetail from './pages/PaymentDetail';
import Customers from './pages/Customers';
import Retries from './pages/Retries';
import FraudAlerts from './pages/FraudAlerts';
import Duplicates from './pages/Duplicates';
import Workflows from './pages/Workflows';
import LiveFeed from './pages/LiveFeed';
import NotFound from './pages/NotFound';

export default function App() {
  return (
    <Routes>
      <Route path="/login" element={<Login />} />
      <Route
        element={
          <ProtectedRoute>
            {/* the event stream only connects once the user is signed in */}
            <StreamProvider><Layout /></StreamProvider>
          </ProtectedRoute>
        }
      >
        <Route index element={<Dashboard />} />
        <Route path="payments" element={<Payments />} />
        <Route path="payments/:id" element={<PaymentDetail />} />
        <Route path="customers" element={<Customers />} />
        <Route path="retries" element={<Retries />} />
        <Route path="fraud" element={<ProtectedRoute roles={['ADMIN', 'ANALYST']}><FraudAlerts /></ProtectedRoute>} />
        <Route path="duplicates" element={<Duplicates />} />
        <Route path="workflows" element={<Workflows />} />
        <Route path="live" element={<LiveFeed />} />
        <Route path="*" element={<NotFound />} />
      </Route>
    </Routes>
  );
}
