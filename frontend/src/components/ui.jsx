import { AlertTriangle, Inbox, Loader2 } from 'lucide-react';
import { label, toneOf } from '../utils/format';

export function Badge({ value, tone, children }) {
  return <span className={`badge badge--${tone || toneOf(value)}`}>{children ?? label(value)}</span>;
}

/** Horizontal meter for probabilities (0-1) or scores (0-100). */
export function Meter({ value, max = 1, invert = false, showValue = true }) {
  if (value == null) return <span className="muted">—</span>;
  const pct = Math.round((value / max) * 100);
  // For recovery chance high is good; for risk (invert) high is bad
  const good = invert ? pct < 34 : pct >= 60;
  const bad = invert ? pct >= 67 : pct < 30;
  const tone = good ? 'good' : bad ? 'bad' : 'warn';
  return (
    <span className="meter" title={`${pct}%`}>
      <span className="meter__track"><span className={`meter__fill meter__fill--${tone}`} style={{ width: `${pct}%` }} /></span>
      {showValue && <span className="meter__value">{max === 1 ? `${pct}%` : value}</span>}
    </span>
  );
}

export function PageHeader({ title, description, actions }) {
  return (
    <header className="page-header">
      <div>
        <h1>{title}</h1>
        {description && <p className="page-header__desc">{description}</p>}
      </div>
      {actions && <div className="page-header__actions">{actions}</div>}
    </header>
  );
}

export function Loading({ text = 'Loading' }) {
  return <div className="state"><Loader2 className="spin" size={20} aria-hidden /> <span>{text}…</span></div>;
}

export function ErrorState({ error, onRetry }) {
  return (
    <div className="state state--error" role="alert">
      <AlertTriangle size={20} aria-hidden />
      <span>{error?.message || 'Something went wrong.'}</span>
      {onRetry && <button className="btn btn--ghost" onClick={onRetry}>Try again</button>}
    </div>
  );
}

export function Empty({ title, hint }) {
  return (
    <div className="state state--empty">
      <Inbox size={22} aria-hidden />
      <strong>{title}</strong>
      {hint && <span className="muted">{hint}</span>}
    </div>
  );
}

export function Pagination({ page, onChange }) {
  if (!page || page.totalPages <= 1) return null;
  const { number, totalPages, totalElements, size } = page;
  const from = number * size + 1;
  const to = Math.min(totalElements, from + size - 1);
  return (
    <nav className="pagination" aria-label="Pagination">
      <span className="muted">{from}–{to} of {totalElements}</span>
      <button className="btn btn--ghost" disabled={number === 0} onClick={() => onChange(number - 1)}>Previous</button>
      <button className="btn btn--ghost" disabled={number >= totalPages - 1} onClick={() => onChange(number + 1)}>Next</button>
    </nav>
  );
}

export function Select({ label: text, value, onChange, options, allLabel = 'All' }) {
  return (
    <label className="field field--inline">
      <span>{text}</span>
      <select value={value} onChange={(e) => onChange(e.target.value)}>
        <option value="">{allLabel}</option>
        {options.map((o) => <option key={o} value={o}>{label(o)}</option>)}
      </select>
    </label>
  );
}
