import { useCallback, useEffect, useRef, useState } from 'react';

/** Runs an async loader, re-running when deps change. Ignores stale responses. */
export function useAsync(loader, deps = []) {
  const [state, setState] = useState({ data: null, loading: true, error: null });
  const callId = useRef(0);

  const run = useCallback(() => {
    const id = ++callId.current;
    setState((s) => ({ ...s, loading: true, error: null }));
    return loader()
      .then((data) => { if (id === callId.current) setState({ data, loading: false, error: null }); })
      .catch((error) => { if (id === callId.current) setState((s) => ({ ...s, loading: false, error })); });
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, deps);

  useEffect(() => { run(); }, [run]);
  return { ...state, reload: run, setData: (data) => setState((s) => ({ ...s, data })) };
}
