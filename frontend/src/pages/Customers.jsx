import { useEffect, useState } from 'react';
import { Search } from 'lucide-react';
import { api } from '../api';
import { useAsync } from '../hooks/useAsync';
import { Badge, Empty, ErrorState, Loading, Meter, PageHeader, Pagination, Select } from '../components/ui';
import { formatDate, formatMoney, formatPercent } from '../utils/format';

export default function Customers() {
  const [riskBand, setRiskBand] = useState('');
  const [q, setQ] = useState('');
  const [query, setQuery] = useState('');
  const [page, setPage] = useState(0);

  useEffect(() => { const t = setTimeout(() => { setQuery(q); setPage(0); }, 300); return () => clearTimeout(t); }, [q]);
  const { data, loading, error, reload } = useAsync(() => api.customers({ riskBand, q: query, page, size: 15 }), [riskBand, query, page]);

  return (
    <>
      <PageHeader title="Customer risk" description="Risk scores come from the ML service and update after every payment event." />
      <div className="toolbar">
        <label className="search">
          <Search size={16} aria-hidden />
          <input placeholder="Search by name, email or ID" value={q} onChange={(e) => setQ(e.target.value)} aria-label="Search customers" />
        </label>
        <Select label="Risk" value={riskBand} onChange={(v) => { setRiskBand(v); setPage(0); }} options={['HIGH', 'MEDIUM', 'LOW']} />
      </div>
      <section className="panel panel--flush">
        {error ? <ErrorState error={error} onRetry={reload} />
          : loading && !data ? <Loading />
          : data.content.length === 0 ? <Empty title="No customers found" hint="Try a different name or risk level." />
          : (
            <div className="table-wrap">
              <table className="table">
                <thead>
                  <tr><th>Customer</th><th>Risk score</th><th>Risk</th><th className="r">Failed payments</th><th className="r">Recovered</th><th className="r">Still owed</th><th>Customer since</th></tr>
                </thead>
                <tbody>
                  {data.content.map((c) => (
                    <tr key={c.id}>
                      <td><strong>{c.name}</strong><div className="muted small">{c.id} · {c.city}</div></td>
                      <td><Meter value={c.riskScore} max={100} invert /></td>
                      <td><Badge value={c.riskBand} /></td>
                      <td className="r num">{c.failedPayments}</td>
                      <td className="r num">{formatPercent(c.recoveryRate)}</td>
                      <td className="r num">{formatMoney(c.openBalance)}</td>
                      <td>{formatDate(c.memberSince)}</td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          )}
        <Pagination page={data} onChange={setPage} />
      </section>
    </>
  );
}
