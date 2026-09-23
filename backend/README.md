# Intelligent Payment Recovery & Risk Platform

A backend platform that detects failed payments, predicts how likely each one is
to be recovered, scores customer and transaction risk, and automatically decides
the next recovery action — with a real-time admin dashboard.

```
payment gateways ──▶ Kafka: payments.transactions
                          │
                          ▼
              ┌───────────────────────── Spring Boot backend ─────────────────────────┐
              │ idempotency ─▶ duplicate check (Redis) ─▶ failure classification       │
              │   ─▶ behaviour signals (Redis) ─▶ ML scoring (Python) ─▶ fraud check    │
              │   ─▶ recovery decision ─▶ smart retry schedule (MySQL)                 │
              │ RetryExecutor (Redis locks) ─▶ gateway ─▶ recovered / next retry       │
              └──────────────┬────────────────────────────────────────────────────────┘
                             ▼
                  Kafka: recovery.events ─▶ STOMP /topic/transactions ─▶ React dashboard
```

| Folder | What it is |
|---|---|
| `backend/` | Java 21, Spring Boot 3.3, Security + JWT, JPA/Hibernate, MySQL, Redis, Kafka, WebSocket |
| `ml-service/` | Python FastAPI: recovery model (gradient boosting), risk model (logistic regression), anomaly model (isolation forest), with per-payment explanations |
| `frontend/` | React (Vite) admin console |

## Run everything

```bash
docker compose up --build
```

- Dashboard: http://localhost:3000 — sign in as `admin / admin123` or `analyst / analyst123`
- API: http://localhost:8080 · ML docs: http://localhost:8000/docs

On first start the backend creates the two users, the four default recovery rules
and 14 days of sample history, then keeps publishing a realistic stream of gateway
transactions to Kafka (every 4 s) so the dashboard stays live. Turn these off with
`DEMO_SEED=false` / `DEMO_TRAFFIC=false`.

## Run the pieces separately (for development)

```bash
docker compose up mysql redis kafka ml-service      # infrastructure only
cd backend && mvn spring-boot:run                    # http://localhost:8080
cd frontend && npm install && VITE_USE_MOCK=false npm run dev   # http://localhost:5173
```

Tests: `cd backend && mvn test` · `cd ml-service && pip install -r requirements.txt pytest httpx && pytest`

## How each feature is implemented

| Feature | Where |
|---|---|
| Payment failure classification | `ingest/FailureClassifier` — ISO 8583 response codes first, message keywords as fallback, mapped to soft / hard / technical / fraud |
| Recovery probability | `ml-service` gradient boosting model, called from `ml/MlClient` (falls back to rules if the service is down) |
| Customer risk scoring | ML risk model per payment, smoothed into the customer's running score (`Customer.updateRisk`) |
| Smart retry scheduling | `recovery/RetryPlanner` — best approval hours, day after payday, backup gateway, exponential backoff |
| Duplicate transaction detection | `ingest/DuplicateDetector` — Redis `SET NX` fingerprint (customer, amount, instrument, order) with a 3-minute window |
| Fraud / anomaly detection | `ingest/FraudSignals` (velocity, new device, IP city in Redis) + isolation-forest anomaly score |
| Automated recovery workflows | `recovery/RecoveryDecisionEngine` driven by admin-editable `RecoveryWorkflow` rules |
| Real-time events | `events/EventPublisher` → Kafka (sent after DB commit) → `events/EventRelay` → STOMP WebSocket |
| Admin analytics | `service/AnalyticsService`, cached in Redis for 30 s |

Reliability details: Kafka redeliveries are ignored (unique gateway transaction ID),
bad records go to `payments.transactions.DLT` after two retries, retries are guarded
by a Redis lock plus JPA optimistic locking so a customer is never charged twice, and
refresh tokens live in Redis so they rotate on use and can be revoked.

## Feeding it your own transactions

Publish JSON to the `payments.transactions` topic:

```json
{
  "gatewayTxnId": "pay_Nx81Kq", "customerId": "CUS-1001", "customerName": "Diya Nair",
  "customerEmail": "diya@mail.com", "description": "Pro plan renewal", "amount": 999,
  "currency": "INR", "method": "CARD", "gateway": "Razorpay", "success": false,
  "responseCode": "51", "gatewayMessage": "Insufficient funds",
  "instrumentFingerprint": "fp-card-4417", "orderRef": "ORD-7781",
  "deviceId": "dev-abc", "ipCity": "Kozhikode", "occurredAt": "2026-09-23T10:15:00Z"
}
```

Or, as admin: `POST /api/simulate/transactions?count=20` (scenarios: `normal`, `duplicate`, `fraud-burst`).

The REST API and WebSocket contract are documented in `frontend/README.md`.

## Before production

Change the seeded passwords and `JWT_SECRET`; replace `ddl-auto: update` with Flyway
migrations; replace `SimulatedGatewayClient` with real gateway SDK calls and
`NotificationService` with an email/SMS provider; train the ML models on real labelled
history instead of the synthetic generator in `ml-service/app/model.py`.
