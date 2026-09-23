"""Feature encoding shared by training and scoring."""
import math
import numpy as np

REASONS = ["INSUFFICIENT_FUNDS", "NETWORK_TIMEOUT", "BANK_DOWNTIME", "DO_NOT_HONOR",
           "LIMIT_EXCEEDED", "CARD_EXPIRED", "INVALID_CVV", "SUSPECTED_FRAUD", "UNKNOWN"]
METHODS = ["CARD", "UPI", "NETBANKING", "WALLET"]

# Columns grouped by the human-readable factor they explain.
GROUPS = {
    "Failure reason": [f"reason_{r}" for r in REASONS],
    "Payment method": [f"method_{m}" for m in METHODS],
    "Amount": ["log_amount"],
    "Time of day": ["hour_sin", "hour_cos"],
    "Days since salary credit": ["days_since_salary"],
    "Customer history": ["past_recovery_rate", "past_failures", "tenure_years"],
    "Customer risk score": ["customer_risk"],
}
COLUMNS = [c for cols in GROUPS.values() for c in cols]
ANOMALY_COLUMNS = ["log_amount", "hour_sin", "hour_cos", "velocity", "new_device", "ip_mismatch"]


def encode(req) -> dict:
    """Turn a request (pydantic model or dict-like with attributes) into a flat feature dict."""
    hour = req.hourOfDay
    failed = max(req.customerFailedCount, 0)
    recovered = min(max(req.customerRecoveredCount, 0), failed) if failed else 0
    row = {f"reason_{r}": 1.0 if req.reasonCode == r else 0.0 for r in REASONS}
    row.update({f"method_{m}": 1.0 if req.method == m else 0.0 for m in METHODS})
    row.update({
        "log_amount": math.log1p(max(req.amount, 0)),
        "hour_sin": math.sin(2 * math.pi * hour / 24),
        "hour_cos": math.cos(2 * math.pi * hour / 24),
        "days_since_salary": float(req.daysSinceSalary),
        "past_recovery_rate": recovered / failed if failed else 0.5,
        "past_failures": float(min(failed, 50)),
        "tenure_years": req.tenureDays / 365.0,
        "customer_risk": req.customerRiskScore / 100.0,
        "velocity": float(req.velocity10m),
        "new_device": 1.0 if req.newDevice else 0.0,
        "ip_mismatch": 1.0 if req.ipCityMismatch else 0.0,
    })
    return row


def to_matrix(rows: list[dict], columns: list[str]) -> np.ndarray:
    return np.array([[r[c] for c in columns] for r in rows], dtype=float)
