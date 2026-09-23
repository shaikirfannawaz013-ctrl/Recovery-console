import { useState } from 'react';
import { NavLink, Outlet } from 'react-router-dom';
import {
  LayoutDashboard, ReceiptText, Users, CalendarClock, ShieldAlert, Copy, Workflow, Radio, LogOut, Menu, X,
} from 'lucide-react';
import { useAuth } from '../context/AuthContext';
import { useStream } from '../context/StreamContext';
import { USE_MOCK } from '../api/config';
import { label } from '../utils/format';

const NAV = [
  { to: '/', label: 'Overview', icon: LayoutDashboard, end: true },
  { to: '/payments', label: 'Failed payments', icon: ReceiptText },
  { to: '/retries', label: 'Retry schedule', icon: CalendarClock },
  { to: '/customers', label: 'Customer risk', icon: Users },
  { to: '/fraud', label: 'Fraud review', icon: ShieldAlert },
  { to: '/duplicates', label: 'Duplicate charges', icon: Copy },
  { to: '/workflows', label: 'Recovery rules', icon: Workflow },
  { to: '/live', label: 'Live events', icon: Radio },
];

const STREAM_TEXT = { live: 'Live', connecting: 'Connecting', reconnecting: 'Reconnecting', error: 'Stream error', offline: 'Offline' };

export default function Layout() {
  const { user, logout } = useAuth();
  const { status } = useStream();
  const [open, setOpen] = useState(false);

  return (
    <div className={`shell ${open ? 'shell--nav-open' : ''}`}>
      <aside className="sidebar">
        <div className="brand">
          <span className="brand__mark" aria-hidden>₹↺</span>
          <span className="brand__name">Recovery Console</span>
        </div>
        <nav className="nav" onClick={() => setOpen(false)}>
          {NAV.map(({ to, label: text, icon: Icon, end }) => (
            <NavLink key={to} to={to} end={end} className={({ isActive }) => `nav__link ${isActive ? 'is-active' : ''}`}>
              <Icon size={17} aria-hidden /> {text}
            </NavLink>
          ))}
        </nav>
        <div className="sidebar__foot">
          <div className="who">
            <strong>{user?.fullName || user?.username}</strong>
            <span>{label(user?.role)}</span>
          </div>
          <button className="btn btn--sidebar" onClick={logout}><LogOut size={16} aria-hidden /> Sign out</button>
        </div>
      </aside>

      <div className="main">
        <div className="topbar">
          <button className="btn btn--ghost topbar__menu" onClick={() => setOpen((o) => !o)} aria-label="Toggle navigation">
            {open ? <X size={18} /> : <Menu size={18} />}
          </button>
          {USE_MOCK && <span className="demo-flag">Demo data — backend not connected</span>}
          <span className={`stream-status stream-status--${status}`}>
            <span className="dot" aria-hidden /> Kafka events: {STREAM_TEXT[status] || status}
          </span>
        </div>
        <main className="content">
          <Outlet />
        </main>
      </div>
    </div>
  );
}
