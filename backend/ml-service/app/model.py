"""
Models trained at start-up on synthetic data that mimics real payment behaviour.
Swap `synthetic_history()` for your own labelled history to train on real data.

- Recovery model: gradient boosting classifier -> P(payment is recovered)
- Risk model: logistic regression -> P(payment ends in fraud / chargeback)
- Anomaly model: isolation forest on behaviour features -> 0..1 score
"""
import math
from dataclasses import dataclass
from types import SimpleNamespace

import numpy as np
from sklearn.ensemble import GradientBoostingClassifier, IsolationForest
from sklearn.linear_model import LogisticRegression

from .features import ANOMALY_COLUMNS, COLUMNS, GROUPS, METHODS, REASONS, encode, to_matrix

MODEL_VERSION = "recovery-gbm-1.0"

REASON_BASE = {  # log-odds of recovery by reason
    "INSUFFICIENT_FUNDS": 0.4, "NETWORK_TIMEOUT": 2.2, "BANK_DOWNTIME": 1.6, "DO_NOT_HONOR": -0.4,
    "LIMIT_EXCEEDED": 0.2, "CARD_EXPIRED": -1.3, "INVALID_CVV": -1.5, "SUSPECTED_FRAUD": -3.0, "UNKNOWN": -0.6,
}
REASON_WEIGHTS = np.array([26, 16, 8, 14, 10, 10, 8, 4, 4], dtype=float)


def _sigmoid(x):
    return 1 / (1 + math.exp(-x))


def synthetic_history(n: int, rng: np.random.Generator):
    rows, recovered, fraud = [], [], []
    for _ in range(n):
        reason = REASONS[rng.choice(len(REASONS), p=REASON_WEIGHTS / REASON_WEIGHTS.sum())]
        failed = int(rng.integers(0, 30))
        req = SimpleNamespace(
            reasonCode=reason,
            method=METHODS[int(rng.integers(0, 4))],
            amount=float(rng.choice([199, 499, 999, 1499, 2999, 4999, 7999, 12499, 24999])),
            hourOfDay=int(rng.integers(0, 24)),
            daysSinceSalary=int(rng.integers(0, 30)),
            customerFailedCount=failed,
            customerRecoveredCount=int(rng.binomial(failed, rng.uniform(0.1, 0.9))) if failed else 0,
            tenureDays=int(rng.integers(0, 1500)),
            customerRiskScore=int(min(99, rng.beta(1.2, 3) * 100)),
            velocity10m=int(rng.poisson(1.2)) + 1,
            newDevice=bool(rng.random() < 0.08),
            ipCityMismatch=bool(rng.random() < 0.1),
        )
        f = encode(req)
        # ground truth used to generate labels
        logit = (REASON_BASE[reason]
                 + 1.6 * (f["past_recovery_rate"] - 0.5)
                 - 2.2 * f["customer_risk"]
                 - 0.035 * f["days_since_salary"] * (1 if reason == "INSUFFICIENT_FUNDS" else 0.2)
                 - 0.12 * (f["log_amount"] - 7)
                 + 0.35 * f["hour_cos"] * -1  # daytime retries do better
                 + 0.2 * f["tenure_years"]
                 + (0.25 if req.method == "UPI" else 0))
        recovered.append(int(rng.random() < _sigmoid(logit)))
        fraud_logit = (-4 + 4 * f["customer_risk"] + 1.5 * f["new_device"] + 1.3 * f["ip_mismatch"]
                       + 0.5 * max(f["velocity"] - 2, 0) + (3 if reason == "SUSPECTED_FRAUD" else 0))
        fraud.append(int(rng.random() < _sigmoid(fraud_logit)))
        rows.append(f)
    return rows, np.array(recovered), np.array(fraud)


@dataclass
class ScoringModels:
    recovery: GradientBoostingClassifier
    risk: LogisticRegression
    anomaly: IsolationForest
    anomaly_lo: float
    anomaly_hi: float
    baseline: dict

    @classmethod
    def train(cls, n: int = 12000, seed: int = 42) -> "ScoringModels":
        rng = np.random.default_rng(seed)
        rows, y_rec, y_fraud = synthetic_history(n, rng)
        X = to_matrix(rows, COLUMNS)
        recovery = GradientBoostingClassifier(n_estimators=150, max_depth=3, learning_rate=0.08, random_state=seed)
        recovery.fit(X, y_rec)

        risk_cols = COLUMNS + ["velocity", "new_device", "ip_mismatch"]
        risk = LogisticRegression(max_iter=2000)
        risk.fit(to_matrix(rows, risk_cols), y_fraud)

        # anomaly model learns "normal" behaviour from non-fraud rows
        normal = [r for r, f in zip(rows, y_fraud) if f == 0]
        A = to_matrix(normal, ANOMALY_COLUMNS)
        anomaly = IsolationForest(n_estimators=200, contamination="auto", random_state=seed).fit(A)
        raw = -anomaly.score_samples(A)
        baseline = {c: float(np.mean([r[c] for r in rows])) for c in COLUMNS}
        return cls(recovery, risk, anomaly, float(np.percentile(raw, 5)), float(np.percentile(raw, 99.5)), baseline)

    # ---------- scoring ----------
    def score(self, req) -> dict:
        f = encode(req)
        p = float(self.recovery.predict_proba(to_matrix([f], COLUMNS))[0, 1])

        risk_cols = COLUMNS + ["velocity", "new_device", "ip_mismatch"]
        risk = float(self.risk.predict_proba(to_matrix([f], risk_cols))[0, 1])
        # blend model risk with the customer's running score so it stays stable
        risk_score = round(100 * min(1.0, 0.6 * risk + 0.4 * f["customer_risk"]))

        raw = float(-self.anomaly.score_samples(to_matrix([f], ANOMALY_COLUMNS))[0])
        anomaly = (raw - self.anomaly_lo) / max(self.anomaly_hi - self.anomaly_lo, 1e-6)
        anomaly = min(1.0, max(0.0, anomaly))

        return {
            "recoveryProbability": round(p, 3),
            "riskScore": int(risk_score),
            "anomalyScore": round(anomaly, 3),
            "topFactors": self._explain(f, p),
            "signals": self._signals(f, anomaly),
            "modelVersion": MODEL_VERSION,
        }

    def _explain(self, f: dict, p: float, top: int = 4) -> list[dict]:
        """Group-wise perturbation: replace a feature group with the average customer and see how p moves."""
        impacts = []
        for name, cols in GROUPS.items():
            g = dict(f)
            for c in cols:
                g[c] = self.baseline[c]
            p_without = float(self.recovery.predict_proba(to_matrix([g], COLUMNS))[0, 1])
            impacts.append({"feature": name, "impact": round(p - p_without, 3)})
        impacts.sort(key=lambda x: abs(x["impact"]), reverse=True)
        return impacts[:top]

    @staticmethod
    def _signals(f: dict, anomaly: float) -> list[str]:
        out = []
        if anomaly >= 0.75:
            out.append("Behaviour differs sharply from normal payment patterns")
        if f["log_amount"] > math.log1p(20000) and f["customer_risk"] > 0.5:
            out.append("Large amount for a higher-risk customer")
        hour = round(math.atan2(f["hour_sin"], f["hour_cos"]) * 24 / (2 * math.pi)) % 24
        if 1 <= hour <= 4:
            out.append("Attempted in the middle of the night")
        return out
