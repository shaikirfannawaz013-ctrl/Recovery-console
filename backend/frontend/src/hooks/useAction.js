import { useState } from 'react';
import { useToast } from '../context/ToastContext';

/** Wraps a mutating call with a busy flag and success / error toasts. */
export function useAction() {
  const notify = useToast();
  const [busy, setBusy] = useState(null);
  async function run(key, fn, successMessage) {
    setBusy(key);
    try {
      const result = await fn();
      const msg = typeof successMessage === 'function' ? successMessage(result) : successMessage;
      if (msg) notify(msg, result?.lastAttemptSucceeded === false ? 'bad' : 'good');
      return result;
    } catch (e) {
      notify(e.message, 'bad');
      return null;
    } finally {
      setBusy(null);
    }
  }
  return { busy, run };
}
