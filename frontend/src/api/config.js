export const API_BASE = import.meta.env.VITE_API_BASE_URL || '/api';
export const USE_MOCK = String(import.meta.env.VITE_USE_MOCK ?? 'true') === 'true';

function defaultWsUrl() {
  const proto = window.location.protocol === 'https:' ? 'wss' : 'ws';
  return `${proto}://${window.location.host}/ws`;
}
const raw = import.meta.env.VITE_WS_URL;
// Accept a full ws:// URL or a path like /ws (resolved against the current host)
export const WS_URL = !raw
  ? defaultWsUrl()
  : raw.startsWith('/')
    ? `${window.location.protocol === 'https:' ? 'wss' : 'ws'}://${window.location.host}${raw}`
    : raw;
