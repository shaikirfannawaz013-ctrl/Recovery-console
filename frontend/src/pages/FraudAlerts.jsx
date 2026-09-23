import { useState } from 'react';
import { Link } from 'react-router-dom';
import { api } from '../api';
import { useAsync } from '../hooks/useAsync';
import { useAction } from '../hooks/useAction';
import { Badge, Empty, ErrorState, Loading, Meter, PageHeader } from '../components/ui';
import { formatDateTime, formatMoney } from '../utils/format';

const TABS = [['OPEN', 'Waiting for review'], ['CONFIRMED', 'Confirmed fraud'], ['DISMISSED', 'Marked safe']];

export default function FraudAlerts() {
  const [status, setStatus] = useState('OPEN');
  const { data, loading, error, reload } = useAsync(() => api.fraudAlerts({ status }), [status]);
  const { busy, run } = useAction();

  const resolve = async (a, decision) => {
    const res = await run(a.id, () => api.resolveFraud(a.id, decision),
      decision === 'CONFIRM' ? `${a.paymentId} blocked as fraud.` : `${a.paymentId} marked safe and queued for retry.`);
    if (res) reload();
  };

  return (
    <>
      <PageHeader title="Fraud review" description="Payments the anomaly model flagged. Confirming blocks the payment; marking safe sends it back to recovery." />
      <div className="tabs" role="tablist">
        {TABS.map(([key, text]) => (
          <button key={key} role="tab" aria-selected={status === key} className={`tab ${status === key ? 'is-active' : ''}`} onClick={() => setStatus(key)}>{text}</button>
        ))}
      </div>
      {error ? <ErrorState error={error} onRetry={reload} />
        : loading && !data ? <Loading />
        : data.length === 0 ? <Empty title={status === 'OPEN' ? 'Nothing waiting for review' : 'No alerts here yet'} />
        : (
          <div className="alerts">
            {data.map((a) => (
              <article key={a.id} className="alert">
                <header className="alert__head">
                  <div>
                    <Link to={`/payments/${a.paymentId}`} className="link">{a.paymentId}</Link>
                    <span className="muted"> · {a.customerName} · {formatDateTime(a.detectedAt)}</span>
                  </div>
                  <span className="num alert__amount">{formatMoney(a.amount, a.currency)}</span>
                </header>
                <div className="alert__score"><span className="muted small">Anomaly score</span><Meter value={a.anomalyScore} invert /></div>
                <ul className="signals">{a.signals.map((s) => <li key={s}>{s}</li>)}</ul>
                {a.status === 'OPEN' ? (
                  <div className="row-actions">
                    <button className="btn btn--danger" disabled={!!busy} onClick={() => resolve(a, 'CONFIRM')}>Confirm fraud</button>
                    <button className="btn" disabled={!!busy} onClick={() => resolve(a, 'DISMISS')}>Mark safe</button>
                  </div>
                ) : <Badge value={a.status} />}
              </article>
            ))}
          </div>
        )}
    </>
  );
}
