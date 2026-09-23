import { useState } from 'react';
import { api } from '../api';
import { useAsync } from '../hooks/useAsync';
import { useAction } from '../hooks/useAction';
import { useAuth } from '../context/AuthContext';
import { Badge, ErrorState, Loading, PageHeader } from '../components/ui';

function WorkflowCard({ wf, canEdit, onSaved }) {
  const [draft, setDraft] = useState(null);
  const { busy, run } = useAction();
  const editing = draft !== null;
  const v = editing ? draft : wf;

  const save = async (body, msg) => {
    const res = await run(wf.id, () => api.updateWorkflow(wf.id, body), msg);
    if (res) { setDraft(null); onSaved(res); }
  };

  return (
    <article className={`workflow ${wf.enabled ? '' : 'is-off'}`}>
      <header className="workflow__head">
        <div>
          <h2>{wf.name}</h2>
          <Badge value={wf.category} />
        </div>
        {canEdit ? (
          <label className="switch">
            <input type="checkbox" checked={wf.enabled} disabled={!!busy}
              onChange={(e) => save({ enabled: e.target.checked }, e.target.checked ? `${wf.name} rule turned on.` : `${wf.name} rule turned off.`)} />
            <span>{wf.enabled ? 'On' : 'Off'}</span>
          </label>
        ) : <span className="muted">{wf.enabled ? 'On' : 'Off'}</span>}
      </header>

      {/* The steps really are an ordered sequence, so they are numbered */}
      <ol className="steps">{wf.steps.map((s) => <li key={s}>{s}</li>)}</ol>

      <dl className="facts facts--row">
        <div>
          <dt>Max retries</dt>
          <dd>{editing ? <input type="number" min={0} max={10} value={v.maxAttempts} onChange={(e) => setDraft({ ...draft, maxAttempts: Number(e.target.value) })} /> : v.maxAttempts}</dd>
        </div>
        <div>
          <dt>Wait between retries</dt>
          <dd>{editing ? <><input type="number" min={0} max={168} value={v.backoffHours} onChange={(e) => setDraft({ ...draft, backoffHours: Number(e.target.value) })} /> h</> : `${v.backoffHours} h`}</dd>
        </div>
        <div>
          <dt>Stop below recovery chance</dt>
          <dd>{editing ? <><input type="number" min={0} max={100} value={Math.round(v.minRecoveryProbability * 100)} onChange={(e) => setDraft({ ...draft, minRecoveryProbability: Number(e.target.value) / 100 })} /> %</> : `${Math.round(v.minRecoveryProbability * 100)}%`}</dd>
        </div>
      </dl>

      {canEdit && (
        <div className="row-actions">
          {editing ? (
            <>
              <button className="btn btn--primary" disabled={!!busy}
                onClick={() => save({ maxAttempts: draft.maxAttempts, backoffHours: draft.backoffHours, minRecoveryProbability: draft.minRecoveryProbability }, `${wf.name} rule saved.`)}>
                Save rule
              </button>
              <button className="btn btn--ghost" onClick={() => setDraft(null)}>Discard changes</button>
            </>
          ) : <button className="btn" onClick={() => setDraft({ ...wf })}>Edit limits</button>}
        </div>
      )}
    </article>
  );
}

export default function Workflows() {
  const { isAdmin } = useAuth();
  const { data, loading, error, reload, setData } = useAsync(() => api.workflows(), []);

  if (loading && !data) return <Loading text="Loading rules" />;
  if (error) return <ErrorState error={error} onRetry={reload} />;

  return (
    <>
      <PageHeader title="Recovery rules"
        description={isAdmin
          ? 'What happens automatically after each type of failure. Changes apply to new failures right away.'
          : 'What happens automatically after each type of failure. Only admins can change these rules.'} />
      <div className="grid grid--2">
        {data.map((wf) => (
          <WorkflowCard key={wf.id} wf={wf} canEdit={isAdmin}
            onSaved={(updated) => setData(data.map((w) => (w.id === updated.id ? updated : w)))} />
        ))}
      </div>
    </>
  );
}
