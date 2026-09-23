# Recovery Console — frontend

React (Vite) admin console for the **Intelligent Payment Recovery & Risk Platform**.
It talks to the Spring Boot backend over REST (JWT) and receives Kafka transaction
events through a STOMP WebSocket.

## Run it

```bash
npm install
cp .env.example .env      # VITE_USE_MOCK=true runs without a backend
npm run dev               # http://localhost:5173
```

Demo logins in mock mode: `admin / admin123` (can edit recovery rules) and
`analyst / analyst123` (read and act on payments, cannot edit rules).

To use the real backend set `VITE_USE_MOCK=false`. In dev, Vite proxies `/api`
and `/ws` to `http://localhost:8080`, so no CORS setup is needed.

### Docker

```bash
docker build -t prp-frontend .
docker run -p 3000:80 prp-frontend
```

nginx serves the build and proxies `/api` and `/ws` to a container named
`backend` on port 8080 — add both to the same `docker-compose` network.

## Screens

| Route | Purpose |
|---|---|
| `/` | Overview: where failed money ended up, daily trend, failure reasons, latest events |
| `/payments` | Failed payments with classification, recovery chance, next action; filters live in the URL |
| `/payments/:id` | One payment: failure details, ML scores and top factors, manual actions, retry history |
| `/retries` | Smart retry schedule grouped by day; retry now, reschedule, cancel |
| `/customers` | Customer risk scores and bands |
| `/fraud` | Anomaly alerts; confirm fraud or mark safe |
| `/duplicates` | Possible duplicate charges; refund or dismiss |
| `/workflows` | Recovery rules per failure type (admin can edit) |
| `/live` | Real-time Kafka event feed with pause and filter |

## Structure

```
src/
  api/          config, axios instance (JWT + refresh), endpoint map, mock backend
  context/      AuthContext, StreamContext (STOMP), ToastContext
  hooks/        useAsync (loading/error/reload), useAction (mutations + toasts)
  components/   Layout, ProtectedRoute, shared UI (Badge, Meter, Pagination, states)
  pages/        one file per screen
  utils/        money/date formatting, enum labels
```

## Backend contract the UI expects

All endpoints are under `/api`, JSON, `Authorization: Bearer <accessToken>`.
Errors may be Spring `ProblemDetail` (`detail`) or `{ "message": "..." }`.
Paged endpoints return a Spring Data `Page` (`content, totalElements, totalPages, number, size`).

**Auth**
- `POST /auth/login` `{username, password}` → `{accessToken, refreshToken, user:{id, username, fullName, role}}` (role `ADMIN` or `ANALYST`)
- `POST /auth/refresh` `{refreshToken}` → same shape

**Analytics**
- `GET /analytics/summary` → `{periodDays, failedAmount, recoveredAmount, inProgressAmount, lostAmount, failedCount, recoveredCount, inProgressCount, lostCount, recoveryRate, openFraudAlerts, openDuplicates, retriesNext24h, currency}`
- `GET /analytics/trend?days=14` → `[{date: "2026-09-23", failed, recovered}]`
- `GET /analytics/failure-reasons` → `[{reason, category, count, recovered}]`

**Payments**
- `GET /payments?status&category&q&sort&page&size` → Page of `{id, customerId, customerName, description, amount, currency, method, gateway, failureReason, failureCategory, gatewayMessage, status, recoveryProbability (0–1), riskScore (0–100), nextAction, failedAt, recoveredAt}`
- `GET /payments/{id}` → above plus `attempts:[{attempt, at, gateway, outcome, responseCode}]`, `model:{version, topFactors:[{feature, impact}]}`, `customer`
- `POST /payments/{id}/retry` → updated payment plus `lastAttemptSucceeded`
- `POST /payments/{id}/actions` `{action}` — `REQUEST_CARD_UPDATE | NOTIFY_CUSTOMER | ESCALATE | WRITE_OFF`

**Customers** — `GET /customers?riskBand&q&page&size` → Page of `{id, name, email, city, riskScore, riskBand, failedPayments, recoveryRate, openBalance, memberSince}`

**Retries**
- `GET /retries` → `[{id, paymentId, customerName, amount, currency, failureReason, strategy, timingNote, attempt, maxAttempts, recoveryProbability, scheduledAt}]`
- `PATCH /retries/{id}` `{scheduledAt}` · `DELETE /retries/{id}`

**Fraud** — `GET /fraud/alerts?status` · `POST /fraud/alerts/{id}/resolve` `{decision: CONFIRM | DISMISS}`

**Duplicates** — `GET /duplicates?status` · `POST /duplicates/{id}/resolve` `{decision: REFUND | KEEP}`

**Workflows** — `GET /workflows` · `PUT /workflows/{id}` (partial: `enabled, maxAttempts, backoffHours, minRecoveryProbability`) — should be `ADMIN` only

**Enums**
- `status`: `FAILED, RETRY_SCHEDULED, UNDER_REVIEW, RECOVERED, WRITTEN_OFF, BLOCKED`
- `failureCategory`: `SOFT_DECLINE, HARD_DECLINE, TECHNICAL, FRAUD`
- `nextAction`: `RETRY_SMART, RETRY_AFTER_PAYDAY, SWITCH_GATEWAY, NOTIFY_CUSTOMER, REQUEST_CARD_UPDATE, MANUAL_REVIEW`

### Real-time events

STOMP over WebSocket at `/ws` (plain WebSocket, not SockJS). The client sends
`Authorization: Bearer …` in the CONNECT frame and subscribes to
`/topic/transactions`. A Kafka listener in Spring can forward each record with
`SimpMessagingTemplate.convertAndSend("/topic/transactions", event)`.

Event body: `{eventId, type, paymentId, customerName, amount, currency, reason, gateway, timestamp}` with
`type` one of `PAYMENT_FAILED, RETRY_SCHEDULED, RETRY_SUCCEEDED, RETRY_FAILED, FRAUD_FLAGGED, DUPLICATE_BLOCKED`.
