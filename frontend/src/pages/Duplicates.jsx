import { useState } from 'react';
import { Link } from 'react-router-dom';
import { api } from '../api';
import { useAsync } from '../hooks/useAsync';
import { useAction } from '../hooks/useAction';
import { Badge, Empty, ErrorState, Loading, PageHeader } from '../components/ui';
import { formatMoney, formatPercent, timeFrom } from '../utils/format';

export default function Duplicates() {
  const [status, setStatus] = useState('OPEN');
  const { data, loading, error, reload } = useAsync(() => api.duplicates({ status }), [status]);
  const { busy, run } = useAction();

  const resolve = async (d, decision) => {
    const res = await run(d.id, () => api.resolveDuplicate(d.id, decision),
      decision === 'REFUND' ? `Refund started for ${d.duplicateId}.` : 'Marked as a separate payment.');
    if (res) reload();
  };

  return (
    <>
      <PageHeader title="Duplicate charges" description="Pairs of charges that look like the same payment made twice." />
      <div className="tabs" role="tablist">
        {[['OPEN', 'To check'], ['', 'All']].map(([key, text]) => (
          <button key={text} role="tab" aria-selected={status === key} className={`tab ${status === key ? 'is-active' : ''}`} onClick={() => setStatus(key)}>{text}</button>
        ))}
      </div>
      <section className="panel panel--flush">
        {error ? <ErrorState error={error} onRetry={reload} />
          : loading && !data ? <Loading />
          : data.length === 0 ? <Empty title="No possible duplicates to check" />
          : (
            <div className="table-wrap">
              <table className="table">
                <thead>
                  <tr><th>Customer</th><th className="r">Amount</th><th>First charge</th><th>Second charge</th><th>Time apart</th><th>Match</th><th>Matched on</th><th /></tr>
                </thead>
                <tbody>
                  {data.map((d) => (
                    <tr key={d.id}>
                      <td>{d.customerName}<div className="muted small">{timeFrom(d.detectedAt)}</div></td>
                      <td className="r num">{formatMoney(d.amount, d.currency)}</td>
                      <td><Link to={`/payments/${d.originalId}`} className="link">{d.originalId}</Link></td>
                      <td>{d.duplicateId}</td>
                      <td className="num">{d.secondsApart}s</td>
                      <td className="num">{formatPercent(d.similarity)}</td>
                      <td className="small">{d.matchedOn.join(', ')}</td>
                      <td className="nowrap">
                        {d.status === 'OPEN' ? (
                          <div className="row-actions">
                            <button className="btn btn--primary" disabled={!!busy} onClick={() => resolve(d, 'REFUND')}>Refund second charge</button>
                            <button className="btn btn--ghost" disabled={!!busy} onClick={() => resolve(d, 'KEEP')}>Not a duplicate</button>
                          </div>
                        ) : <Badge value={d.status} />}
                      </td>
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
