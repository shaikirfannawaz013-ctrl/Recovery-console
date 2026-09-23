import { useState } from 'react';
import { Link } from 'react-router-dom';
import { Pause, Play } from 'lucide-react';
import { useStream } from '../context/StreamContext';
import { Badge, Empty, PageHeader, Select } from '../components/ui';
import { formatMoney, label } from '../utils/format';

const TYPES = ['PAYMENT_FAILED', 'RETRY_SCHEDULED', 'RETRY_SUCCEEDED', 'RETRY_FAILED', 'FRAUD_FLAGGED', 'DUPLICATE_BLOCKED'];

export default function LiveFeed() {
  const { events, status, paused, setPaused } = useStream();
  const [type, setType] = useState('');
  const shown = type ? events.filter((e) => e.type === type) : events;

  return (
    <>
      <PageHeader title="Live events"
        description="Transaction events from Kafka, newest first. The last 200 are kept while this tab is open."
        actions={
          <button className="btn" onClick={() => setPaused(!paused)}>
            {paused ? <><Play size={15} aria-hidden /> Resume</> : <><Pause size={15} aria-hidden /> Pause</>}
          </button>
        } />
      <div className="toolbar">
        <Select label="Event" value={type} onChange={setType} options={TYPES} />
        {paused && <span className="muted">Paused — new events are being skipped.</span>}
      </div>
      <section className="panel panel--flush">
        {shown.length === 0 ? (
          <Empty title={status === 'live' ? 'Waiting for events' : 'Not connected to the event stream'}
            hint={status === 'live' ? 'Events appear here as soon as the backend publishes them.' : 'Check that the backend WebSocket at /ws is running.'} />
        ) : (
          <div className="table-wrap">
            <table className="table table--feed">
              <thead><tr><th>Time</th><th>Event</th><th>Payment</th><th>Customer</th><th className="r">Amount</th><th>Reason</th><th>Gateway</th></tr></thead>
              <tbody>
                {shown.map((e) => (
                  <tr key={e.eventId}>
                    <td className="num nowrap">{new Date(e.timestamp).toLocaleTimeString('en-IN')}</td>
                    <td><Badge value={e.type} /></td>
                    <td><Link to={`/payments/${e.paymentId}`} className="link">{e.paymentId}</Link></td>
                    <td>{e.customerName}</td>
                    <td className="r num">{formatMoney(e.amount, e.currency)}</td>
                    <td>{label(e.reason)}</td>
                    <td>{e.gateway}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </section>
    </>
  );
}
