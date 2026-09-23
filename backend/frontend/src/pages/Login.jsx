import { useState } from 'react';
import { Navigate, useLocation, useNavigate } from 'react-router-dom';
import { useAuth } from '../context/AuthContext';
import { USE_MOCK } from '../api/config';

export default function Login() {
  const { user, login } = useAuth();
  const navigate = useNavigate();
  const location = useLocation();
  const [form, setForm] = useState({ username: '', password: '' });
  const [error, setError] = useState('');
  const [busy, setBusy] = useState(false);

  if (user) return <Navigate to="/" replace />;

  async function submit(e) {
    e.preventDefault();
    setError('');
    setBusy(true);
    try {
      await login(form.username, form.password);
      navigate(location.state?.from?.pathname || '/', { replace: true });
    } catch (err) {
      setError(err.message);
    } finally {
      setBusy(false);
    }
  }

  return (
    <div className="login">
      <section className="login__story">
        <span className="brand__mark brand__mark--lg" aria-hidden>₹↺</span>
        <h1>Every failed payment gets a next step.</h1>
        <p>
          Failures are classified, scored for recovery chance and risk, and routed to a retry,
          a customer reminder or a fraud review — automatically.
        </p>
      </section>

      <form className="login__form" onSubmit={submit} noValidate>
        <h2>Sign in</h2>
        <label className="field">
          <span>Username</span>
          <input autoFocus autoComplete="username" value={form.username}
            onChange={(e) => setForm({ ...form, username: e.target.value })} required />
        </label>
        <label className="field">
          <span>Password</span>
          <input type="password" autoComplete="current-password" value={form.password}
            onChange={(e) => setForm({ ...form, password: e.target.value })} required />
        </label>
        {error && <p className="form-error" role="alert">{error}</p>}
        <button className="btn btn--primary btn--block" disabled={busy || !form.username || !form.password}>
          {busy ? 'Signing in…' : 'Sign in'}
        </button>
        {USE_MOCK && (
          <p className="muted small">
            Demo accounts: <code>admin</code> / <code>admin123</code> or <code>analyst</code> / <code>analyst123</code>
          </p>
        )}
      </form>
    </div>
  );
}
