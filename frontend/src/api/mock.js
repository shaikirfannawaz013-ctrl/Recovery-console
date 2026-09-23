/**
 * In-browser mock of the Spring Boot backend. Response shapes match the real
 * API (Spring Data Page objects, enums as UPPER_SNAKE strings) so switching
 * VITE_USE_MOCK=false needs no UI changes.
 */

// ---------- deterministic random ----------
let seed = 20260923;
const rand = () => {
  seed = (seed * 16807) % 2147483647;
  return (seed - 1) / 2147483646;
};
const pick = (arr) => arr[Math.floor(rand() * arr.length)];
const between = (min, max) => Math.round(min + rand() * (max - min));
const clamp = (v, lo = 0, hi = 1) => Math.max(lo, Math.min(hi, v));
const delay = (data, ms = 220 + Math.random() * 200) =>
  new Promise((res) => setTimeout(() => res(structuredClone(data)), ms));
const fail = (message, status = 400) =>
  Promise.reject(Object.assign(new Error(message), { status }));

const HOUR = 3600e3;
const DAY = 24 * HOUR;
const now = Date.now();

// ---------- reference data ----------
export const REASONS = [
  { code: 'INSUFFICIENT_FUNDS', category: 'SOFT_DECLINE', base: 0.62, msg: 'Insufficient balance in account', action: 'RETRY_AFTER_PAYDAY' },
  { code: 'NETWORK_TIMEOUT', category: 'TECHNICAL', base: 0.9, msg: 'Gateway did not respond within 30s', action: 'RETRY_SMART' },
  { code: 'BANK_DOWNTIME', category: 'TECHNICAL', base: 0.84, msg: 'Issuer bank unavailable', action: 'SWITCH_GATEWAY' },
  { code: 'DO_NOT_HONOR', category: 'SOFT_DECLINE', base: 0.42, msg: 'Issuer declined: do not honor', action: 'NOTIFY_CUSTOMER' },
  { code: 'LIMIT_EXCEEDED', category: 'SOFT_DECLINE', base: 0.56, msg: 'Daily transaction limit exceeded', action: 'RETRY_SMART' },
  { code: 'CARD_EXPIRED', category: 'HARD_DECLINE', base: 0.24, msg: 'Card expired', action: 'REQUEST_CARD_UPDATE' },
  { code: 'INVALID_CVV', category: 'HARD_DECLINE', base: 0.2, msg: 'Incorrect CVV entered', action: 'REQUEST_CARD_UPDATE' },
  { code: 'SUSPECTED_FRAUD', category: 'FRAUD', base: 0.04, msg: 'Blocked by risk engine', action: 'MANUAL_REVIEW' },
];
const REASON_WEIGHTS = [26, 16, 8, 14, 10, 10, 8, 8];
const weightedReason = () => {
  let r = rand() * REASON_WEIGHTS.reduce((a, b) => a + b, 0);
  for (let i = 0; i < REASONS.length; i++) {
    r -= REASON_WEIGHTS[i];
    if (r <= 0) return REASONS[i];
  }
  return REASONS[0];
};

const FIRST = ['Aarav', 'Diya', 'Vihaan', 'Ananya', 'Arjun', 'Fathima', 'Rohan', 'Meera', 'Kabir', 'Sneha', 'Aditya', 'Nisha', 'Irfan', 'Lakshmi', 'Rahul', 'Zara', 'Karthik', 'Priya', 'Sameer', 'Divya'];
const LAST = ['Sharma', 'Nair', 'Reddy', 'Khan', 'Iyer', 'Menon', 'Patel', 'Das', 'Rao', 'Pillai', 'Gupta', 'Varma', 'Joseph', 'Singh', 'Hegde'];
const PLANS = ['Pro plan renewal', 'Annual membership', 'Order checkout', 'Insurance premium', 'EMI instalment', 'Team plan renewal', 'Wallet top-up'];
const METHODS = ['CARD', 'UPI', 'NETBANKING', 'WALLET'];
const GATEWAYS = ['Razorpay', 'PayU', 'Cashfree', 'Stripe'];
const CITIES = ['Kozhikode', 'Bengaluru', 'Hyderabad', 'Chennai', 'Mumbai', 'Pune', 'Delhi', 'Tirupati'];

// ---------- customers ----------
const customers = Array.from({ length: 60 }, (_, i) => {
  const name = `${pick(FIRST)} ${pick(LAST)}`;
  const riskScore = clamp(Math.pow(rand(), 1.6), 0.02, 0.98);
  return {
    id: `CUS-${1001 + i}`,
    name,
    email: `${name.toLowerCase().replace(' ', '.')}@mail.com`,
    city: pick(CITIES),
    riskScore: Math.round(riskScore * 100),
    riskBand: riskScore > 0.66 ? 'HIGH' : riskScore > 0.33 ? 'MEDIUM' : 'LOW',
    memberSince: new Date(now - between(40, 900) * DAY).toISOString(),
    totalPayments: between(6, 80),
  };
});
const customerById = Object.fromEntries(customers.map((c) => [c.id, c]));

// ---------- payments ----------
const payments = Array.from({ length: 240 }, (_, i) => {
  const customer = pick(customers);
  const reason = weightedReason();
  const failedAt = now - rand() * 14 * DAY;
  const recoveryProbability = clamp(reason.base + (rand() - 0.5) * 0.25 - customer.riskScore / 400, 0.01, 0.99);
  const riskScore = Math.round(clamp(customer.riskScore / 100 + (rand() - 0.5) * 0.2 + (reason.category === 'FRAUD' ? 0.35 : 0)) * 100);
  const ageDays = (now - failedAt) / DAY;

  let status;
  if (reason.category === 'FRAUD') status = rand() > 0.5 ? 'UNDER_REVIEW' : 'BLOCKED';
  else if (ageDays > 1 && rand() < recoveryProbability * 0.85) status = 'RECOVERED';
  else if (reason.category === 'HARD_DECLINE' && ageDays > 7) status = 'WRITTEN_OFF';
  else status = rand() > 0.35 ? 'RETRY_SCHEDULED' : 'FAILED';

  const attemptsCount = status === 'RECOVERED' ? between(1, 3) : status === 'FAILED' ? 0 : between(1, 3);
  const attempts = Array.from({ length: attemptsCount }, (_, a) => {
    const last = a === attemptsCount - 1;
    return {
      attempt: a + 1,
      at: new Date(failedAt + (a + 1) * between(2, 30) * HOUR).toISOString(),
      gateway: pick(GATEWAYS),
      outcome: last && status === 'RECOVERED' ? 'SUCCESS' : 'DECLINED',
      responseCode: last && status === 'RECOVERED' ? '00' : pick(['51', '05', '91', '61']),
    };
  });

  return {
    id: `PAY-${(870100 + i * 7).toString()}`,
    customerId: customer.id,
    customerName: customer.name,
    description: pick(PLANS),
    amount: pick([199, 499, 999, 1499, 2999, 4999, 7999, 12499, 24999]) + (rand() > 0.7 ? between(1, 99) : 0),
    currency: 'INR',
    method: pick(METHODS),
    gateway: pick(GATEWAYS),
    failureReason: reason.code,
    failureCategory: reason.category,
    gatewayMessage: reason.msg,
    status,
    recoveryProbability: Number(recoveryProbability.toFixed(2)),
    riskScore,
    nextAction: status === 'RECOVERED' || status === 'WRITTEN_OFF' || status === 'BLOCKED' ? null : reason.action,
    failedAt: new Date(failedAt).toISOString(),
    recoveredAt: status === 'RECOVERED' ? attempts.at(-1)?.at : null,
    attempts,
    model: {
      version: 'recovery-xgb-1.4.2',
      topFactors: [
        { feature: 'Failure reason', impact: Number((reason.base - 0.5).toFixed(2)) },
        { feature: 'Customer risk score', impact: Number((-customer.riskScore / 250).toFixed(2)) },
        { feature: 'Past recovery rate', impact: Number(((rand() - 0.4) * 0.3).toFixed(2)) },
        { feature: 'Days since salary credit', impact: Number(((rand() - 0.5) * 0.2).toFixed(2)) },
      ],
    },
  };
}).sort((a, b) => new Date(b.failedAt) - new Date(a.failedAt));
const paymentById = () => Object.fromEntries(payments.map((p) => [p.id, p]));

// ---------- retries ----------
const TIMING_NOTES = {
  RETRY_AFTER_PAYDAY: 'Timed for 1 day after the expected salary credit',
  RETRY_SMART: 'Off-peak window with the highest issuer approval rate',
  SWITCH_GATEWAY: 'Routed to the backup gateway',
  NOTIFY_CUSTOMER: 'After the customer reminder is delivered',
};
let retries = payments
  .filter((p) => p.status === 'RETRY_SCHEDULED')
  .map((p, i) => ({
    id: `RTY-${5000 + i}`,
    paymentId: p.id,
    customerName: p.customerName,
    amount: p.amount,
    currency: p.currency,
    failureReason: p.failureReason,
    strategy: p.nextAction,
    timingNote: TIMING_NOTES[p.nextAction] || 'Standard backoff',
    attempt: p.attempts.length + 1,
    maxAttempts: 4,
    recoveryProbability: p.recoveryProbability,
    scheduledAt: new Date(now + between(1, 96) * HOUR).toISOString(),
  }))
  .sort((a, b) => new Date(a.scheduledAt) - new Date(b.scheduledAt));

// ---------- fraud alerts ----------
const SIGNALS = ['Transaction velocity 6x above normal', 'Device seen for the first time', 'IP location differs from billing city', 'Card BIN linked to earlier chargebacks', 'Amount far above customer average', 'Several cards tried in 10 minutes'];
let fraudAlerts = payments
  .filter((p) => p.failureCategory === 'FRAUD' || p.riskScore > 82)
  .slice(0, 22)
  .map((p, i) => ({
    id: `FRD-${300 + i}`,
    paymentId: p.id,
    customerId: p.customerId,
    customerName: p.customerName,
    amount: p.amount,
    currency: p.currency,
    anomalyScore: Number(clamp(p.riskScore / 100 + rand() * 0.1).toFixed(2)),
    signals: [...new Set([pick(SIGNALS), pick(SIGNALS), pick(SIGNALS)])],
    status: i < 14 ? 'OPEN' : pick(['CONFIRMED', 'DISMISSED']),
    detectedAt: p.failedAt,
  }));

// ---------- duplicates ----------
let duplicates = Array.from({ length: 12 }, (_, i) => {
  const p = payments[between(0, payments.length - 1)];
  return {
    id: `DUP-${700 + i}`,
    customerName: p.customerName,
    amount: p.amount,
    currency: p.currency,
    originalId: p.id,
    duplicateId: `PAY-${900000 + i * 13}`,
    secondsApart: between(2, 140),
    similarity: Number((0.86 + rand() * 0.14).toFixed(2)),
    matchedOn: ['Same customer', 'Same amount', rand() > 0.4 ? 'Same card' : 'Same UPI ID', rand() > 0.5 ? 'Same order reference' : 'Same device'],
    status: i < 8 ? 'OPEN' : pick(['REFUNDED', 'NOT_DUPLICATE']),
    detectedAt: new Date(now - between(1, 200) * HOUR).toISOString(),
  };
});

// ---------- workflows ----------
let workflows = [
  { id: 'WF-SOFT', name: 'Soft declines', category: 'SOFT_DECLINE', enabled: true, maxAttempts: 4, backoffHours: 24, minRecoveryProbability: 0.3,
    steps: ['Retry at the best predicted time', 'Send payment reminder', 'Retry after salary credit', 'Escalate to collections'] },
  { id: 'WF-TECH', name: 'Technical failures', category: 'TECHNICAL', enabled: true, maxAttempts: 3, backoffHours: 1, minRecoveryProbability: 0.1,
    steps: ['Retry in 15 minutes', 'Switch to backup gateway', 'Retry on backup gateway'] },
  { id: 'WF-HARD', name: 'Hard declines', category: 'HARD_DECLINE', enabled: true, maxAttempts: 1, backoffHours: 72, minRecoveryProbability: 0.15,
    steps: ['Send card update link', 'Send reminder after 3 days', 'Write off after 7 days'] },
  { id: 'WF-FRAUD', name: 'Suspected fraud', category: 'FRAUD', enabled: true, maxAttempts: 0, backoffHours: 0, minRecoveryProbability: 0,
    steps: ['Hold the payment', 'Send to fraud review', 'Block the customer if confirmed'] },
];

// ---------- helpers ----------
function page(list, { page = 0, size = 20 } = {}) {
  const p = Number(page), s = Number(size);
  return {
    content: list.slice(p * s, p * s + s),
    totalElements: list.length,
    totalPages: Math.max(1, Math.ceil(list.length / s)),
    number: p,
    size: s,
  };
}
const matches = (q, ...fields) => !q || fields.some((f) => String(f).toLowerCase().includes(q.toLowerCase()));

// ---------- auth ----------
const USERS = {
  admin: { password: 'admin123', user: { id: 1, username: 'admin', fullName: 'Ops Admin', role: 'ADMIN' } },
  analyst: { password: 'analyst123', user: { id: 2, username: 'analyst', fullName: 'Recovery Analyst', role: 'ANALYST' } },
};
export function login({ username, password }) {
  const found = USERS[username?.trim().toLowerCase()];
  if (!found || found.password !== password) return fail('Username or password is incorrect.', 401);
  return delay({ accessToken: `mock.${username}.${Date.now()}`, refreshToken: `mock-refresh.${username}`, user: found.user });
}

// ---------- analytics ----------
export function summary() {
  const sum = (list) => list.reduce((a, p) => a + p.amount, 0);
  const recovered = payments.filter((p) => p.status === 'RECOVERED');
  const inProgress = payments.filter((p) => ['FAILED', 'RETRY_SCHEDULED', 'UNDER_REVIEW'].includes(p.status));
  const lost = payments.filter((p) => ['WRITTEN_OFF', 'BLOCKED'].includes(p.status));
  return delay({
    periodDays: 14,
    failedAmount: sum(payments),
    recoveredAmount: sum(recovered),
    inProgressAmount: sum(inProgress),
    lostAmount: sum(lost),
    failedCount: payments.length,
    recoveredCount: recovered.length,
    inProgressCount: inProgress.length,
    lostCount: lost.length,
    recoveryRate: Number((recovered.length / payments.length).toFixed(3)),
    openFraudAlerts: fraudAlerts.filter((f) => f.status === 'OPEN').length,
    openDuplicates: duplicates.filter((d) => d.status === 'OPEN').length,
    retriesNext24h: retries.filter((r) => new Date(r.scheduledAt) - now < DAY).length,
    currency: 'INR',
  });
}

export function trend({ days = 14 } = {}) {
  const buckets = [];
  for (let d = days - 1; d >= 0; d--) {
    const date = new Date(now - d * DAY);
    const key = date.toISOString().slice(0, 10);
    buckets.push({ date: key, failed: 0, recovered: 0 });
  }
  const byKey = Object.fromEntries(buckets.map((b) => [b.date, b]));
  payments.forEach((p) => {
    const b = byKey[p.failedAt.slice(0, 10)];
    if (!b) return;
    b.failed += p.amount;
    if (p.status === 'RECOVERED') b.recovered += p.amount;
  });
  return delay(buckets);
}

export function failureReasons() {
  const counts = {};
  payments.forEach((p) => {
    counts[p.failureReason] ??= { reason: p.failureReason, category: p.failureCategory, count: 0, recovered: 0 };
    counts[p.failureReason].count++;
    if (p.status === 'RECOVERED') counts[p.failureReason].recovered++;
  });
  return delay(Object.values(counts).sort((a, b) => b.count - a.count));
}

// ---------- payments ----------
export function payments_(params = {}) {
  const { status, category, q, sort = 'failedAt' } = params;
  let list = payments.filter((p) =>
    (!status || p.status === status) &&
    (!category || p.failureCategory === category) &&
    matches(q, p.id, p.customerName, p.customerId, p.description)
  );
  if (sort === 'recoveryProbability') list = [...list].sort((a, b) => b.recoveryProbability - a.recoveryProbability);
  if (sort === 'amount') list = [...list].sort((a, b) => b.amount - a.amount);
  // list view omits heavy fields, like a real DTO would
  return delay(page(list.map(({ attempts, model, ...rest }) => rest), params));
}
export { payments_ as payments };

export function payment(id) {
  const p = paymentById()[id];
  if (!p) return fail(`Payment ${id} was not found.`, 404);
  return delay({ ...p, customer: customerById[p.customerId] });
}

export function retryNow(id) {
  const p = paymentById()[id];
  if (!p) return fail(`Payment ${id} was not found.`, 404);
  if (!['FAILED', 'RETRY_SCHEDULED'].includes(p.status)) return fail('Only failed or scheduled payments can be retried.');
  const success = Math.random() < p.recoveryProbability;
  p.attempts.push({
    attempt: p.attempts.length + 1, at: new Date().toISOString(), gateway: p.gateway,
    outcome: success ? 'SUCCESS' : 'DECLINED', responseCode: success ? '00' : '51',
  });
  if (success) {
    p.status = 'RECOVERED'; p.recoveredAt = new Date().toISOString(); p.nextAction = null;
    retries = retries.filter((r) => r.paymentId !== id);
  }
  return delay({ ...p, customer: customerById[p.customerId], lastAttemptSucceeded: success }, 700);
}

export function runAction(id, action) {
  const p = paymentById()[id];
  if (!p) return fail(`Payment ${id} was not found.`, 404);
  if (action === 'ESCALATE') { p.status = 'UNDER_REVIEW'; p.nextAction = 'MANUAL_REVIEW'; }
  if (action === 'WRITE_OFF') { p.status = 'WRITTEN_OFF'; p.nextAction = null; retries = retries.filter((r) => r.paymentId !== id); }
  if (action === 'REQUEST_CARD_UPDATE' || action === 'NOTIFY_CUSTOMER') p.lastNotifiedAt = new Date().toISOString();
  return delay({ ...p, customer: customerById[p.customerId] });
}

// ---------- customers ----------
export function customers_(params = {}) {
  const { riskBand, q } = params;
  const list = customers
    .filter((c) => (!riskBand || c.riskBand === riskBand) && matches(q, c.id, c.name, c.email))
    .map((c) => {
      const own = payments.filter((p) => p.customerId === c.id);
      const recovered = own.filter((p) => p.status === 'RECOVERED').length;
      return {
        ...c,
        failedPayments: own.length,
        recoveryRate: own.length ? Number((recovered / own.length).toFixed(2)) : null,
        openBalance: own.filter((p) => ['FAILED', 'RETRY_SCHEDULED', 'UNDER_REVIEW'].includes(p.status)).reduce((a, p) => a + p.amount, 0),
      };
    })
    .sort((a, b) => b.riskScore - a.riskScore);
  return delay(page(list, params));
}
export { customers_ as customers };

// ---------- retries ----------
export function retries_() { return delay(retries); }
export { retries_ as retries };

export function reschedule(id, scheduledAt) {
  const r = retries.find((x) => x.id === id);
  if (!r) return fail('This retry no longer exists.', 404);
  if (new Date(scheduledAt) < new Date()) return fail('Pick a time in the future.');
  r.scheduledAt = new Date(scheduledAt).toISOString();
  retries.sort((a, b) => new Date(a.scheduledAt) - new Date(b.scheduledAt));
  return delay(r);
}
export function cancelRetry(id) {
  const r = retries.find((x) => x.id === id);
  retries = retries.filter((x) => x.id !== id);
  const p = r && paymentById()[r.paymentId];
  if (p) p.status = 'FAILED';
  return delay({ ok: true });
}

// ---------- fraud ----------
export function fraudAlerts_({ status } = {}) {
  return delay(fraudAlerts.filter((f) => !status || f.status === status).sort((a, b) => b.anomalyScore - a.anomalyScore));
}
export { fraudAlerts_ as fraudAlerts };
export function resolveFraud(id, decision) {
  const f = fraudAlerts.find((x) => x.id === id);
  if (!f) return fail('Alert not found.', 404);
  f.status = decision === 'CONFIRM' ? 'CONFIRMED' : 'DISMISSED';
  const p = paymentById()[f.paymentId];
  if (p) p.status = decision === 'CONFIRM' ? 'BLOCKED' : 'RETRY_SCHEDULED';
  return delay(f);
}

// ---------- duplicates ----------
export function duplicates_({ status } = {}) {
  return delay(duplicates.filter((d) => !status || d.status === status));
}
export { duplicates_ as duplicates };
export function resolveDuplicate(id, decision) {
  const d = duplicates.find((x) => x.id === id);
  if (!d) return fail('Duplicate record not found.', 404);
  d.status = decision === 'REFUND' ? 'REFUNDED' : 'NOT_DUPLICATE';
  return delay(d);
}

// ---------- workflows ----------
export function workflows_() { return delay(workflows); }
export { workflows_ as workflows };
export function updateWorkflow(id, body) {
  workflows = workflows.map((w) => (w.id === id ? { ...w, ...body } : w));
  return delay(workflows.find((w) => w.id === id));
}

// ---------- live event stream (stands in for Kafka -> STOMP) ----------
const EVENT_TYPES = ['PAYMENT_FAILED', 'PAYMENT_FAILED', 'RETRY_SCHEDULED', 'RETRY_SUCCEEDED', 'RETRY_FAILED', 'FRAUD_FLAGGED', 'DUPLICATE_BLOCKED'];
let eventSeq = 1;
export function subscribeStream(onEvent) {
  const timer = setInterval(() => {
    const p = payments[Math.floor(Math.random() * payments.length)];
    const type = EVENT_TYPES[Math.floor(Math.random() * EVENT_TYPES.length)];
    onEvent({
      eventId: `EVT-${Date.now()}-${eventSeq++}`,
      type,
      paymentId: p.id,
      customerName: p.customerName,
      amount: p.amount,
      currency: p.currency,
      reason: p.failureReason,
      gateway: p.gateway,
      timestamp: new Date().toISOString(),
    });
  }, 2200);
  return () => clearInterval(timer);
}
