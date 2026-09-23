import { useEffect, useState } from 'react';
import { Link, useNavigate, useSearchParams } from 'react-router-dom';
import { Search } from 'lucide-react';
import { api } from '../api';
import { useAsync } from '../hooks/useAsync';
import { Badge, Empty, ErrorState, Loading, Meter, PageHeader, Pagination, Select } from '../components/ui';
import { formatDateTime, formatMoney, label } from '../utils/format';

const STATUSES = ['FAILED', 'RETRY_SCHEDULED', 'UNDER_REVIEW', 'RECOVERED', 'WRITTEN_OFF', 'BLOCKED'];
const CATEGORIES = ['SOFT_DECLINE', 'HARD_DECLINE', 'TECHNICAL', 'FRAUD'];

export default function Payments() {
  const navigate = useNavigate();
  const [params, setParams] = useSearchParams();
  const status = params.get('status') || '';
  const category = params.get('category') || '';
  const sort = params.get('sort') || 'failedAt';
  const page = Number(params.get('page') || 0);
  const [q, setQ] = useState(params.get('q') || '');

  const update = (patch) => {
    const next = new URLSearchParams(params);
    Object.entries({ page: 0, ...patch }).forEach(([k, v]) => (v === '' || v == null ? next.delete(k) : next.set(k, v)));
    setParams(next, { replace: true });
  };

  // debounce search box into the URL
  useEffect(() => {
    const t = setTimeout(() => { if (q !== (params.get('q') || '')) update({ q }); }, 300);
    return () => clearTimeout(t);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [q]);

  const { data, loading, error, reload } = useAsync(
    () => api.payments({ status, category, sort, q: params.get('q') || '', page, size: 15 }),
    [status, category, sort, page, params.get('q')]
  );

  return (
    <>
      <PageHeader title="Failed payments" description="Each failure is classified and given a recovery chance and a next action." />

      <div className="toolbar">
        <label className="search">
          <Search size={16} aria-hidden />
          <input placeholder="Search by payment ID, customer or plan" value={q} onChange={(e) => setQ(e.target.value)} aria-label="Search payments" />
        </label>
        <Select label="Status" value={status} onChange={(v) => update({ status: v })} options={STATUSES} />
        <Select label="Type" value={category} onChange={(v) => update({ category: v })} options={CATEGORIES} />
        <label className="field field--inline">
          <span>Sort</span>
          <select value={sort} onChange={(e) => update({ sort: e.target.value })}>
            <option value="failedAt">Newest first</option>
            <option value="recoveryProbability">Best recovery chance</option>
            <option value="amount">Largest amount</option>
          </select>
        </label>
      </div>

      <section className="panel panel--flush">
        {error ? <ErrorState error={error} onRetry={reload} />
          : loading && !data ? <Loading />
          : data.content.length === 0 ? <Empty title="No payments match these filters" hint="Clear a filter or search for a different customer." />
          : (
            <div className="table-wrap">
              <table className={`table ${loading ? 'is-loading' : ''}`}>
                <thead>
                  <tr>
                    <th>Payment</th><th>Customer</th><th className="r">Amount</th><th>Why it failed</th>
                    <th>Recovery chance</th><th>Next action</th><th>Status</th><th>Failed</th>
                  </tr>
                </thead>
                <tbody>
                  {data.content.map((p) => (
                    <tr key={p.id} className="clickable" onClick={() => navigate(`/payments/${p.id}`)}>
                      <td>
                        <Link to={`/payments/${p.id}`} className="link" onClick={(e) => e.stopPropagation()}>{p.id}</Link>
                        <div className="muted small">{p.description} · {label(p.method)}</div>
                      </td>
                      <td>{p.customerName}</td>
                      <td className="r num">{formatMoney(p.amount, p.currency)}</td>
                      <td>
                        {label(p.failureReason)}
                        <div><Badge value={p.failureCategory} /></div>
                      </td>
                      <td><Meter value={p.recoveryProbability} /></td>
                      <td>{p.nextAction ? label(p.nextAction) : <span className="muted">None</span>}</td>
                      <td><Badge value={p.status} /></td>
                      <td className="nowrap">{formatDateTime(p.failedAt)}</td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          )}
        <Pagination page={data} onChange={(n) => update({ page: n })} />
      </section>
    </>
  );
}
