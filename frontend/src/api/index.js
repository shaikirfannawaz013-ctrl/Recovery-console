/**
 * Every backend call the UI makes lives here. Each function either hits the
 * Spring Boot API or, when VITE_USE_MOCK=true, the in-browser mock.
 */
import { http } from './http';
import { USE_MOCK } from './config';
import * as mock from './mock';

const get = (url, params) => http.get(url, { params }).then((r) => r.data);
const post = (url, body) => http.post(url, body).then((r) => r.data);
const put = (url, body) => http.put(url, body).then((r) => r.data);
const patch = (url, body) => http.patch(url, body).then((r) => r.data);
const del = (url) => http.delete(url).then((r) => r.data);

const pick = (real, fake) => (USE_MOCK ? fake : real);

export const api = {
  // Auth
  login: pick((body) => post('/auth/login', body), mock.login),

  // Analytics
  summary: pick((p) => get('/analytics/summary', p), mock.summary),
  trend: pick((p) => get('/analytics/trend', p), mock.trend),
  failureReasons: pick((p) => get('/analytics/failure-reasons', p), mock.failureReasons),

  // Payments
  payments: pick((p) => get('/payments', p), mock.payments),
  payment: pick((id) => get(`/payments/${id}`), mock.payment),
  retryNow: pick((id) => post(`/payments/${id}/retry`), mock.retryNow),
  runAction: pick((id, action) => post(`/payments/${id}/actions`, { action }), mock.runAction),

  // Customers
  customers: pick((p) => get('/customers', p), mock.customers),

  // Retry scheduler
  retries: pick((p) => get('/retries', p), mock.retries),
  reschedule: pick((id, scheduledAt) => patch(`/retries/${id}`, { scheduledAt }), mock.reschedule),
  cancelRetry: pick((id) => del(`/retries/${id}`), mock.cancelRetry),

  // Fraud / anomaly
  fraudAlerts: pick((p) => get('/fraud/alerts', p), mock.fraudAlerts),
  resolveFraud: pick((id, decision) => post(`/fraud/alerts/${id}/resolve`, { decision }), mock.resolveFraud),

  // Duplicates
  duplicates: pick((p) => get('/duplicates', p), mock.duplicates),
  resolveDuplicate: pick((id, decision) => post(`/duplicates/${id}/resolve`, { decision }), mock.resolveDuplicate),

  // Recovery workflows
  workflows: pick(() => get('/workflows'), mock.workflows),
  updateWorkflow: pick((id, body) => put(`/workflows/${id}`, body), mock.updateWorkflow),
};
