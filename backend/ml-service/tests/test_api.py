from fastapi.testclient import TestClient

from app.main import app

client = TestClient(app)

BASE = dict(category="SOFT_DECLINE", amount=999, method="UPI", hourOfDay=11, daysSinceSalary=2,
            customerFailedCount=4, customerRecoveredCount=3, tenureDays=400, customerRiskScore=20,
            velocity10m=1, newDevice=False, ipCityMismatch=False)


def score(**kw):
    r = client.post("/score", json={**BASE, **kw})
    assert r.status_code == 200, r.text
    return r.json()


def test_health():
    assert client.get("/health").json()["status"] == "UP"


def test_timeouts_recover_more_often_than_expired_cards():
    assert score(reasonCode="NETWORK_TIMEOUT")["recoveryProbability"] > score(reasonCode="CARD_EXPIRED")["recoveryProbability"]


def test_suspicious_behaviour_raises_anomaly_and_risk():
    calm = score(reasonCode="DO_NOT_HONOR")
    odd = score(reasonCode="DO_NOT_HONOR", velocity10m=8, newDevice=True, ipCityMismatch=True, hourOfDay=3)
    assert odd["anomalyScore"] > calm["anomalyScore"]
    assert odd["riskScore"] >= calm["riskScore"]


def test_response_shape():
    r = score(reasonCode="INSUFFICIENT_FUNDS")
    assert 0 <= r["recoveryProbability"] <= 1
    assert len(r["topFactors"]) == 4
    assert {"feature", "impact"} <= set(r["topFactors"][0])


def test_rejects_bad_input():
    assert client.post("/score", json={**BASE, "reasonCode": "X", "hourOfDay": 30}).status_code == 422
