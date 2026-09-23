import { useState } from 'react';
import { Link } from 'react-router-dom';
import { api } from '../api';
import { useAsync } from '../hooks/useAsync';
import { useAction } from '../hooks/useAction';
import { Empty, ErrorState, Loading, Meter, PageHeader } from '../components/ui';
import { formatMoney, label, timeFrom } from '../utils/format';

// yyyy-MM-ddTHH:mm in local time for <input type="datetime-local">
const toLocalInput = (iso) => {
  const d = new Date(iso);
  return new Date(d.getTime() - d.getTimezoneOffset() * 60000).toISOString().slice(0, 16);
};

function groupByDay(list) {
  const groups = {};
  list.forEach((r) => {
    const key = new Date(r.scheduledAt).toLocaleDateString('en-IN', { weekday: 'long', day: 'numeric', month: 'long' });
    (groups[key] ??= []).push(r);
  });
  return Object.entries(groups);
}

export default function Retries() {
  const { data, loading, error, reload } = useAsync(() => api.retries(), []);
  const { busy, run } = useAction();
  const [editing, setEditing] = useState(null);
  const [when, setWhen] = useState('');

  if (loading && !data) return <Loading text="Loading schedule" />;
  if (error) return <ErrorState error={error} onRetry={reload} />;

  const saveReschedule = async (r) => {
    const res = await run(r.id, () => api.reschedule(r.id, new Date(when).toISOString()), 'Retry rescheduled.');
    if (res) { setEditing(null); reload(); }
  };
  const retryNow = async (r) => {
    const res = await run(r.id, () => api.retryNow(r.paymentId),
      (x) => (x.status === 'RECOVERED' ? `${r.paymentId} recovered.` : `${r.paymentId} was declined again.`));
    if (res) reload();
  };
  const cancel = async (r) => {
    if (!window.confirm(`Cancel the scheduled retry for ${r.paymentId}?`)) return;
    const res = await run(r.id, () => api.cancelRetry(r.id), 'Retry cancelled.');
    if (res) reload();
  };

  return (
    <>
      <PageHeader title="Retry schedule"
        description="Retries are timed by the model to the moment each payment is most likely to succeed." />
      {data.length === 0 ? <Empty title="No retries scheduled" hint="New failures with a good recovery chance will appear here." /> : (
        groupByDay(data).map(([day, items]) => (
          <section key={day} className="panel panel--flush">
            <h2 className="panel__title panel__title--pad">{day} <span className="muted">· {items.length}</span></h2>
            <ul className="retry-list">
              {items.map((r) => (
                <li key={r.id} className="retry">
                  <div className="retry__time">
                    <strong>{new Date(r.scheduledAt).toLocaleTimeString('en-IN', { hour: '2-digit', minute: '2-digit' })}</strong>
                    <span className="muted small">{timeFrom(r.scheduledAt)}</span>
                  </div>
                  <div className="retry__body">
                    <div>
                      <Link to={`/payments/${r.paymentId}`} className="link">{r.paymentId}</Link> · {r.customerName} ·{' '}
                      <span className="num">{formatMoney(r.amount, r.currency)}</span>
                    </div>
                    <div className="muted small">
                      {label(r.strategy)} — {r.timingNote}. Attempt {r.attempt} of {r.maxAttempts}. Failed for {label(r.failureReason).toLowerCase()}.
                    </div>
                  </div>
                  <Meter value={r.recoveryProbability} />
                  <div className="retry__actions">
                    {editing === r.id ? (
                      <>
                        <input type="datetime-local" value={when} min={toLocalInput(new Date().toISOString())}
                          onChange={(e) => setWhen(e.target.value)} aria-label="New retry time" />
                        <button className="btn btn--primary" disabled={!!busy || !when} onClick={() => saveReschedule(r)}>Save time</button>
                        <button className="btn btn--ghost" onClick={() => setEditing(null)}>Keep original</button>
                      </>
                    ) : (
                      <>
                        <button className="btn" disabled={!!busy} onClick={() => retryNow(r)}>{busy === r.id ? 'Working…' : 'Retry now'}</button>
                        <button className="btn btn--ghost" disabled={!!busy} onClick={() => { setEditing(r.id); setWhen(toLocalInput(r.scheduledAt)); }}>Reschedule</button>
                        <button className="btn btn--ghost btn--danger-text" disabled={!!busy} onClick={() => cancel(r)}>Cancel</button>
                      </>
                    )}
                  </div>
                </li>
              ))}
            </ul>
          </section>
        ))
      )}
      <p className="muted small">Times are shown in your local time zone.</p>
    </>
  );
}
