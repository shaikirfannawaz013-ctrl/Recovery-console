const moneyFormatters = {};
export function formatMoney(amount, currency = 'INR', compact = false) {
  if (amount == null) return '—';
  // Indian short form for chart axes: ₹1.5L, ₹2Cr, ₹40K
  if (compact && currency === 'INR') {
    const a = Math.abs(amount);
    const short = a >= 1e7 ? `${+(amount / 1e7).toFixed(1)}Cr` : a >= 1e5 ? `${+(amount / 1e5).toFixed(1)}L`
      : a >= 1e3 ? `${+(amount / 1e3).toFixed(0)}K` : `${amount}`;
    return `₹${short}`;
  }
  const key = `${currency}-${compact}`;
  moneyFormatters[key] ??= new Intl.NumberFormat('en-IN', {
    style: 'currency', currency, maximumFractionDigits: compact ? 1 : 0,
    notation: compact ? 'compact' : 'standard',
  });
  return moneyFormatters[key].format(amount);
}

export const formatPercent = (v, digits = 0) => (v == null ? '—' : `${(v * 100).toFixed(digits)}%`);

export function formatDateTime(iso) {
  if (!iso) return '—';
  return new Date(iso).toLocaleString('en-IN', { day: 'numeric', month: 'short', hour: '2-digit', minute: '2-digit' });
}
export function formatDate(iso) {
  if (!iso) return '—';
  return new Date(iso).toLocaleDateString('en-IN', { day: 'numeric', month: 'short' });
}

export function timeFrom(iso) {
  if (!iso) return '—';
  const diff = new Date(iso) - Date.now();
  const abs = Math.abs(diff);
  const unit = abs < 3600e3 ? ['minute', 60e3] : abs < 86400e3 ? ['hour', 3600e3] : ['day', 86400e3];
  const rtf = new Intl.RelativeTimeFormat('en', { numeric: 'auto' });
  return rtf.format(Math.round(diff / unit[1]), unit[0]);
}

// Human labels for backend enums
const LABELS = {
  INSUFFICIENT_FUNDS: 'Insufficient funds', NETWORK_TIMEOUT: 'Gateway timeout', BANK_DOWNTIME: 'Bank downtime',
  DO_NOT_HONOR: 'Do not honor', LIMIT_EXCEEDED: 'Limit exceeded', CARD_EXPIRED: 'Card expired',
  INVALID_CVV: 'Wrong CVV', SUSPECTED_FRAUD: 'Suspected fraud',
  SOFT_DECLINE: 'Soft decline', HARD_DECLINE: 'Hard decline', TECHNICAL: 'Technical', FRAUD: 'Fraud',
  FAILED: 'Failed', RETRY_SCHEDULED: 'Retry scheduled', RECOVERED: 'Recovered', WRITTEN_OFF: 'Written off',
  UNDER_REVIEW: 'Under review', BLOCKED: 'Blocked',
  RETRY_SMART: 'Smart retry', RETRY_AFTER_PAYDAY: 'Retry after payday', SWITCH_GATEWAY: 'Switch gateway',
  NOTIFY_CUSTOMER: 'Remind customer', REQUEST_CARD_UPDATE: 'Ask for new card', MANUAL_REVIEW: 'Manual review',
  PAYMENT_FAILED: 'Payment failed', RETRY_SUCCEEDED: 'Retry succeeded', RETRY_FAILED: 'Retry declined',
  FRAUD_FLAGGED: 'Fraud flagged', DUPLICATE_BLOCKED: 'Duplicate blocked',
  OPEN: 'Open', CONFIRMED: 'Confirmed fraud', DISMISSED: 'Marked safe', REFUNDED: 'Refunded', NOT_DUPLICATE: 'Not a duplicate',
  LOW: 'Low', MEDIUM: 'Medium', HIGH: 'High', CARD: 'Card', UPI: 'UPI', NETBANKING: 'Net banking', WALLET: 'Wallet',
  SUCCESS: 'Approved', DECLINED: 'Declined', ADMIN: 'Admin', ANALYST: 'Analyst',
};
export const label = (v) =>
  v == null ? '—' : LABELS[v] ?? String(v).toLowerCase().replace(/_/g, ' ').replace(/^\w/, (c) => c.toUpperCase());

// Tone used by badges: recovered / pending / lost / fraud / neutral
export function toneOf(v) {
  if (['RECOVERED', 'SUCCESS', 'RETRY_SUCCEEDED', 'LOW', 'REFUNDED', 'DISMISSED', 'NOT_DUPLICATE', 'TECHNICAL'].includes(v)) return 'good';
  if (['RETRY_SCHEDULED', 'FAILED', 'MEDIUM', 'SOFT_DECLINE', 'PAYMENT_FAILED', 'RETRY_SCHEDULED', 'OPEN'].includes(v)) return 'warn';
  if (['WRITTEN_OFF', 'BLOCKED', 'HIGH', 'HARD_DECLINE', 'DECLINED', 'RETRY_FAILED', 'DUPLICATE_BLOCKED', 'CONFIRMED'].includes(v)) return 'bad';
  if (['FRAUD', 'UNDER_REVIEW', 'FRAUD_FLAGGED', 'SUSPECTED_FRAUD'].includes(v)) return 'fraud';
  return 'neutral';
}
