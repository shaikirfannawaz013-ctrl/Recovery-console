import { Link } from 'react-router-dom';
import {
  Area, AreaChart, Bar, BarChart, CartesianGrid, Cell, ResponsiveContainer, Tooltip, XAxis, YAxis,
} from 'recharts';
import { api } from '../api';
import { useAsync } from '../hooks/useAsync';
import { useStream } from '../context/StreamContext';
import { Badge, ErrorState, Loading, PageHeader } from '../components/ui';
import { formatDate, formatMoney, formatPercent, label, timeFrom } from '../utils/format';

const COLORS = { recovered: '#1E7A55', progress: '#B7791F', lost: '#B3261E', failed: '#8A97A8', fraud: '#6B3FA0' };
const CATEGORY_COLOR = { SOFT_DECLINE: COLORS.progress, TECHNICAL: '#2D6CB0', HARD_DECLINE: COLORS.lost, FRAUD: COLORS.fraud };

/** The one signature element: where the failed money went. */
function RecoveryLedger({ s }) {
  const parts = [
    { key: 'recovered', label: 'Recovered', amount: s.recoveredAmount, count: s.recoveredCount, color: COLORS.recovered },
    { key: 'progress', label: 'Still being recovered', amount: s.inProgressAmount, count: s.inProgressCount, color: COLORS.progress },
    { key: 'lost', label: 'Written off or blocked', amount: s.lostAmount, count: s.lostCount, color: COLORS.lost },
  ];
  return (
    <section className="ledger" aria-label="Where failed payments ended up">
      <div className="ledger__head">
        <div>
          <p className="ledger__caption">Failed in the last {s.periodDays} days</p>
          <p className="ledger__total">{formatMoney(s.failedAmount, s.currency)}</p>
        </div>
        <div className="ledger__rate">
          <span className="ledger__rate-value">{formatPercent(s.recoveryRate)}</span>
          <span className="ledger__caption">of failed payments recovered</span>
        </div>
      </div>
      <div className="ledger__bar" role="img"
        aria-label={parts.map((p) => `${p.label} ${formatMoney(p.amount)}`).join(', ')}>
        {parts.map((p) => (
          <span key={p.key} style={{ flexGrow: p.amount, background: p.color }} />
        ))}
      </div>
      <dl className="ledger__legend">
        {parts.map((p) => (
          <div key={p.key}>
            <dt><span className="swatch" style={{ background: p.color }} /> {p.label}</dt>
            <dd>{formatMoney(p.amount, s.currency)} <span className="muted">· {p.count} payments</span></dd>
          </div>
        ))}
      </dl>
    </section>
  );
}

export default function Dashboard() {
  const summary = useAsync(() => api.summary(), []);
  const trend = useAsync(() => api.trend({ days: 14 }), []);
  const reasons = useAsync(() => api.failureReasons(), []);
  const { events } = useStream();

  if (summary.loading && !summary.data) return <Loading text="Loading overview" />;
  if (summary.error) return <ErrorState error={summary.error} onRetry={summary.reload} />;
  const s = summary.data;

  return (
    <>
      <PageHeader title="Overview" description="Recovery performance and what needs a decision today." />
      <RecoveryLedger s={s} />

      <div className="attention">
        <Link to="/fraud" className="attention__item">
          <strong>{s.openFraudAlerts}</strong> fraud alerts waiting for review
        </Link>
        <Link to="/duplicates" className="attention__item">
          <strong>{s.openDuplicates}</strong> possible duplicate charges
        </Link>
        <Link to="/retries" className="attention__item">
          <strong>{s.retriesNext24h}</strong> retries running in the next 24 hours
        </Link>
      </div>

      <div className="grid grid--2">
        <section className="panel">
          <h2 className="panel__title">Failed vs recovered, daily</h2>
          {trend.error ? <ErrorState error={trend.error} onRetry={trend.reload} /> : (
            <div className="chart">
              <ResponsiveContainer width="100%" height={260}>
                <AreaChart data={trend.data || []} margin={{ top: 8, right: 8, left: 0, bottom: 0 }}>
                  <CartesianGrid stroke="#E3E7EC" vertical={false} />
                  <XAxis dataKey="date" tickFormatter={formatDate} tick={{ fontSize: 12 }} stroke="#8A97A8" />
                  <YAxis tickFormatter={(v) => formatMoney(v, 'INR', true)} tick={{ fontSize: 12 }} stroke="#8A97A8" width={64} />
                  <Tooltip formatter={(v, n) => [formatMoney(v), n === 'failed' ? 'Failed' : 'Recovered']} labelFormatter={formatDate} />
                  <Area type="monotone" dataKey="failed" stroke={COLORS.failed} fill={COLORS.failed} fillOpacity={0.15} strokeWidth={2} />
                  <Area type="monotone" dataKey="recovered" stroke={COLORS.recovered} fill={COLORS.recovered} fillOpacity={0.25} strokeWidth={2} />
                </AreaChart>
              </ResponsiveContainer>
            </div>
          )}
        </section>

        <section className="panel">
          <h2 className="panel__title">Why payments failed</h2>
          {reasons.error ? <ErrorState error={reasons.error} onRetry={reasons.reload} /> : (
            <div className="chart">
              <ResponsiveContainer width="100%" height={260}>
                <BarChart data={reasons.data || []} layout="vertical" margin={{ top: 4, right: 16, left: 8, bottom: 0 }}>
                  <CartesianGrid stroke="#E3E7EC" horizontal={false} />
                  <XAxis type="number" tick={{ fontSize: 12 }} stroke="#8A97A8" allowDecimals={false} />
                  <YAxis type="category" dataKey="reason" tickFormatter={label} width={130} tick={{ fontSize: 12 }} stroke="#8A97A8" />
                  <Tooltip formatter={(v, n, p) => [`${v} failed · ${p.payload.recovered} recovered`, label(p.payload.category)]} labelFormatter={label} />
                  <Bar dataKey="count" radius={[0, 3, 3, 0]}>
                    {(reasons.data || []).map((r) => <Cell key={r.reason} fill={CATEGORY_COLOR[r.category]} />)}
                  </Bar>
                </BarChart>
              </ResponsiveContainer>
            </div>
          )}
        </section>
      </div>

      <section className="panel">
        <div className="panel__row">
          <h2 className="panel__title">Latest events</h2>
          <Link to="/live" className="link">Open live events</Link>
        </div>
        {events.length === 0 ? <p className="muted">Waiting for the first event from Kafka…</p> : (
          <ul className="feed feed--compact">
            {events.slice(0, 6).map((e) => (
              <li key={e.eventId}>
                <Badge value={e.type} />
                <Link to={`/payments/${e.paymentId}`} className="link">{e.paymentId}</Link>
                <span>{e.customerName}</span>
                <span className="num">{formatMoney(e.amount, e.currency)}</span>
                <span className="muted">{timeFrom(e.timestamp)}</span>
              </li>
            ))}
          </ul>
        )}
      </section>
    </>
  );
}
