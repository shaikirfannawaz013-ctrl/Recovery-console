import axios from 'axios';
import { API_BASE } from './config';
import { tokenStore } from './tokenStore';

export const http = axios.create({ baseURL: API_BASE, timeout: 15000 });

http.interceptors.request.use((config) => {
  const token = tokenStore.access();
  if (token) config.headers.Authorization = `Bearer ${token}`;
  return config;
});

// Turn Spring errors (ProblemDetail or {message}) into a readable Error
function toError(error) {
  const data = error.response?.data;
  const message =
    data?.detail || data?.message || data?.error ||
    (error.code === 'ECONNABORTED' ? 'The server took too long to respond.' : null) ||
    (!error.response ? 'Cannot reach the server. Check that the backend is running.' : null) ||
    `Request failed with status ${error.response.status}`;
  const err = new Error(message);
  err.status = error.response?.status;
  return err;
}

// One in-flight refresh shared by every request that got a 401
let refreshPromise = null;

http.interceptors.response.use(
  (res) => res,
  async (error) => {
    const original = error.config || {};
    const is401 = error.response?.status === 401;
    const isAuthCall = original.url?.includes('/auth/');

    if (is401 && !original._retry && !isAuthCall && tokenStore.refresh()) {
      original._retry = true;
      try {
        refreshPromise ??= axios
          .post(`${API_BASE}/auth/refresh`, { refreshToken: tokenStore.refresh() })
          .then((r) => { tokenStore.save(r.data); return r.data.accessToken; })
          .finally(() => { refreshPromise = null; });
        const token = await refreshPromise;
        original.headers.Authorization = `Bearer ${token}`;
        return http(original);
      } catch {
        tokenStore.clear();
        window.dispatchEvent(new Event('auth:expired'));
      }
    } else if (is401 && !isAuthCall) {
      tokenStore.clear();
      window.dispatchEvent(new Event('auth:expired'));
    }
    return Promise.reject(toError(error));
  }
);
