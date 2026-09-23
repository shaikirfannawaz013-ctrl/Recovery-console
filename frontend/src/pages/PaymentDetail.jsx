import { Link, useParams } from 'react-router-dom';
import { ArrowLeft } from 'lucide-react';
import { api } from '../api';
import { useAsync } from '../hooks/useAsync';
import { useAction } from '../hooks/useAction';
import { Badge, ErrorState, Loading, Meter } from '../components/ui';
import { formatDateTime, formatMoney, formatPercent, label } from '../utils/format';

const ACTION_HELP = {
  RETRY_SMART: 'Retry at the time of day this issuer approves most often.',
  RETRY_AFTER_PAYDAY: 'Retry one day after the customer’s expected salary credit.',
  SWITCH_GATEWAY: 'Send the next attempt through the backup gateway.',
  NOTIFY_CUSTOMER: 'Remind the customer before trying again.',
  REQUEST_CARD_UPDATE: 'Ask the customer to add a new card; retrying the same card will fail.',
  MANUAL_REVIEW: 'Hold the payment until the fraud team reviews it.',
};

export default function PaymentDetail() {
  const { id } = useParams();
  const { data: p, loading, error, reload, setData } = useAsync(() => api.payment(id), [id]);
  const { busy, run } = useAction();

  if (loading && !p) return <Loading text="Loading payment" />;
  if (error) return <ErrorState error={error} onRetry={reload} />;

  const open = ['FAILED', 'RETRY_SCHEDULED'].includes(p.status);

  const retry = () => run('retry', () => api.retryNow(p.id),
    (r) => (r.lastAttemptSucceeded === false ? 'Retry declined by the issuer.' : r.status === 'RECOVERED' ? 'Payment recovered.' : 'Retry sent.'))
    .then((r) => r && setData(r));
  const action = (a, msg) => run(a, () => api.runAction(p.id, a), msg).then((r) => r && setData(r));

  return (
    <>
      <Link to="/payments" className="back"><ArrowLeft size={16} aria-hidden /> Failed payments</Link>

      <header className="detail-head">
        <div>
          <p className="muted">{p.id} · {p.description}</p>
          <h1 className="detail-head__amount">{formatMoney(p.amount, p.currency)}</h1>
          <p>{p.customerName} paid by {label(p.method)} through {p.gateway}</p>
        </div>
        <Badge value={p.status} />
      </header>

      <div className="grid grid--3">
        <section className="panel">
          <h2 className="panel__title">Failure</h2>
          <dl className="facts">
            <div><dt>Reason</dt><dd>{label(p.failureReason)}</dd></div>
            <div><dt>Type</dt><dd><Badge value={p.failureCategory} /></dd></div>
            <div><dt>Gateway said</dt><dd>{p.gatewayMessage}</dd></div>
            <div><dt>Failed at</dt><dd>{formatDateTime(p.failedAt)}</dd></div>
            {p.recoveredAt && <div><dt>Recovered at</dt><dd>{formatDateTime(p.recoveredAt)}</dd></div>}
          </dl>
        </section>

        <section className="panel">
          <h2 className="panel__title">Model scores</h2>
          <dl className="facts">
            <div><dt>Recovery chance</dt><dd><Meter value={p.recoveryProbability} /></dd></div>
            <div><dt>Transaction risk</dt><dd><Meter value={p.riskScore} max={100} invert /></dd></div>
            <div><dt>Customer risk</dt><dd>{p.customer ? <Badge value={p.customer.riskBand}>{label(p.customer.riskBand)} ({p.customer.riskScore})</Badge> : '—'}</dd></div>
          </dl>
          {p.model && (
            <>
              <h3 className="panel__sub">What moved the recovery chance</h3>
              <ul className="factors">
                {p.model.topFactors.map((f) => (
                  <li key={f.feature}>
                    <span>{f.feature}</span>
                    <span className={`num ${f.impact >= 0 ? 'pos' : 'neg'}`}>{f.impact >= 0 ? '+' : ''}{formatPercent(f.impact)}</span>
                  </li>
                ))}
              </ul>
              <p className="muted small">Model {p.model.version}</p>
            </>
          )}
        </section>

        <section className="panel panel--accent">
          <h2 className="panel__title">Next action</h2>
          {p.nextAction ? (
            <>
              <p className="next-action">{label(p.nextAction)}</p>
              <p className="muted">{ACTION_HELP[p.nextAction]}</p>
            </>
          ) : <p className="muted">No action needed — this payment is closed.</p>}
          {open && (
            <div className="stack">
              <button className="btn btn--primary" disabled={!!busy} onClick={retry}>{busy === 'retry' ? 'Retrying…' : 'Retry now'}</button>
              <button className="btn" disabled={!!busy} onClick={() => action('REQUEST_CARD_UPDATE', 'Card update link sent.')}>Send card update link</button>
              <button className="btn" disabled={!!busy} onClick={() => action('NOTIFY_CUSTOMER', 'Reminder sent.')}>Send reminder</button>
              <button className="btn" disabled={!!busy} onClick={() => action('ESCALATE', 'Sent to manual review.')}>Send to review</button>
              <button className="btn btn--danger" disabled={!!busy} onClick={() => action('WRITE_OFF', 'Payment written off.')}>Write off</button>
            </div>
          )}
        </section>
      </div>

      <section className="panel">
        <h2 className="panel__title">Retry attempts</h2>
        {p.attempts.length === 0 ? <p className="muted">No retries yet.</p> : (
          <ol className="timeline">
            {p.attempts.map((a) => (
              <li key={a.attempt} className={`timeline__item timeline__item--${a.outcome === 'SUCCESS' ? 'good' : 'bad'}`}>
                <strong>Attempt {a.attempt}</strong> <Badge value={a.outcome} />
                <span className="muted"> {formatDateTime(a.at)} via {a.gateway} · response code {a.responseCode}</span>
              </li>
            ))}
          </ol>
        )}
      </section>
    </>
  );
}
