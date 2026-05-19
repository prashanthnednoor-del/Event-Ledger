# Event Ledger API — Architecture Design

## 1. Overview

A single-service REST API that ingests financial transaction events, enforces idempotency, tolerates out-of-order delivery, and provides accurate account balance computation. Built with Java 21 + Spring Boot 3 + H2 in-memory database.

---

## 2. Tech Stack

| Layer | Choice | Reason |
|---|---|---|
| Language | Java 21 | Required by constraint |
| Framework | Spring Boot 3 | Standard, runnable with `mvn spring-boot:run` |
| Database | H2 (in-memory) | No external setup required |
| ORM | Spring Data JPA / Hibernate | Handles schema, queries, unique constraints |
| Validation | Jakarta Bean Validation | Declarative, clean controller layer |
| Testing | JUnit 5 + Spring MockMvc | Standard, covers unit + integration |
| Build | Maven | Single command: `mvn test`, `mvn spring-boot:run` |
| Docs (bonus) | SpringDoc OpenAPI / Swagger UI | Auto-generated from annotations |

---

## 3. Project Structure

```
event-ledger/
├── src/
│   ├── main/java/com/eventledger/
│   │   ├── EventLedgerApplication.java       ← entry point
│   │   ├── controller/
│   │   │   └── EventController.java          ← REST endpoints
│   │   ├── service/
│   │   │   └── EventService.java             ← business logic, idempotency
│   │   ├── repository/
│   │   │   └── EventRepository.java          ← JPA queries
│   │   ├── model/
│   │   │   ├── Event.java                    ← JPA entity
│   │   │   ├── EventRequest.java             ← inbound DTO (validated)
│   │   │   └── BalanceResponse.java          ← outbound DTO
│   │   └── exception/
│   │       └── GlobalExceptionHandler.java   ← validation + error responses
│   ├── main/resources/
│   │   └── application.yml                   ← H2 config, server port
│   └── test/java/com/eventledger/
│       └── EventLedgerIntegrationTest.java   ← all test cases
└── pom.xml
```

---

## 4. Data Model

### `events` Table (single table, no joins needed)

| Column | Type | Notes |
|---|---|---|
| `event_id` | VARCHAR (PK) | Natural key — enforces idempotency at DB level |
| `account_id` | VARCHAR | Indexed for fast account lookups |
| `type` | VARCHAR | `CREDIT` or `DEBIT` |
| `amount` | DECIMAL(19,4) | Positive only |
| `currency` | VARCHAR | e.g. `USD` |
| `event_timestamp` | TIMESTAMP | Original business time — used for ordering |
| `received_at` | TIMESTAMP | Wall-clock arrival time — audit/debug only |
| `metadata` | TEXT | JSON blob, nullable |

**No separate `accounts` table.** Balance is always derived by aggregating events — no mutable account state to get out of sync.

---

## 5. API Design

### POST /events — Submit an event

**Happy path:** store event, return `201 Created` with the event body.

**Duplicate (same `eventId`):** return `200 OK` with the original stored event. Do **not** throw an error, do **not** modify state.

**Invalid payload:** return `400 Bad Request` with a structured error body.

```
Request  →  Validate fields  →  Check eventId exists?
                                      │
                                 YES  │  NO
                                  ↓       ↓
                           return 200   save to DB
                           (original)   return 201
```

### GET /events/{id}

Look up by `eventId` (primary key). Return `404` if not found.

### GET /events?account={accountId}

Query all events for the account, **ORDER BY event_timestamp ASC**.
Out-of-order tolerance is automatic — we always sort by business timestamp, never by insertion order.

### GET /accounts/{accountId}/balance

Single aggregate query:
```sql
SELECT
  SUM(CASE WHEN type = 'CREDIT' THEN amount ELSE 0 END) -
  SUM(CASE WHEN type = 'DEBIT'  THEN amount ELSE 0 END)
FROM events
WHERE account_id = :accountId
```

Returns `{ "accountId": "acct-123", "balance": 250.00, "currency": "USD" }`.
Returns `balance: 0.00` if no events exist for the account (not a 404).

---

## 6. Key Design Decisions

### 6.1 Idempotency — Database Unique Constraint as the Source of Truth

`event_id` is the primary key. Two strategies considered:

| Strategy | Pros | Cons |
|---|---|---|
| **Check-then-insert** (SELECT first, then INSERT) | Simple to read | Race condition under concurrent POSTs |
| **Insert-or-ignore + unique constraint** | Race-safe, atomic | Requires catching `DataIntegrityViolationException` |

**Decision:** Use the unique PK constraint. On `DataIntegrityViolationException`, fetch and return the original — this is race-safe with no locking required.

### 6.2 Out-of-Order Tolerance — No Special Logic Required

Because balance is always computed as a full aggregate over all events, and the event list is always sorted by `event_timestamp`, out-of-order arrival is a non-issue by design. There is no running balance counter to update — every read is a fresh computation.

### 6.3 Balance Storage — Computed on Read vs. Cached

| Approach | Pros | Cons |
|---|---|---|
| **Compute on read (chosen)** | Always correct, no sync issues | Slower at scale |
| Cached/materialized balance | Fast reads | Can drift from events if update logic has a bug |

**Decision:** Compute on read. For this scale (in-memory DB, take-home scope), correctness outweighs read performance. At production scale, a read-model / CQRS cache would be appropriate.

### 6.4 Metadata Storage — JSON as TEXT

Metadata has no defined schema and is optional. Storing as a TEXT column (serialized JSON) avoids a separate table and keeps the schema simple. It is not queried, only returned.

---

## 7. Validation Rules

| Field | Rule | HTTP Response |
|---|---|---|
| `eventId` | Required, non-blank | 400 |
| `accountId` | Required, non-blank | 400 |
| `type` | Must be `CREDIT` or `DEBIT` | 400 |
| `amount` | Required, must be > 0 | 400 |
| `currency` | Required, non-blank | 400 |
| `eventTimestamp` | Required, valid ISO 8601 | 400 |

Error response shape:
```json
{
  "status": 400,
  "error": "Validation Failed",
  "messages": [
    "amount: must be greater than 0",
    "type: must be CREDIT or DEBIT"
  ]
}
```

---

## 8. Test Plan

| Test Case | What it verifies |
|---|---|
| POST valid event → 201 | Happy path ingest |
| POST same eventId twice → 200 same body | Idempotency |
| POST same eventId concurrently (2 threads) | Race-condition idempotency |
| GET /events/{id} for existing event → 200 | Single event retrieval |
| GET /events/{id} for unknown id → 404 | Not found handling |
| GET /events?account=X returns events in timestamp order | Out-of-order tolerance |
| Events arrive out of order, GET list is still sorted | Out-of-order tolerance |
| GET /accounts/{id}/balance after mix of CREDIT + DEBIT | Balance correctness |
| Events arrive out of order, balance is still correct | Balance + out-of-order |
| GET balance for account with no events → 0.00 | Edge case |
| POST with missing required field → 400 with message | Validation |
| POST with amount = 0 → 400 | Validation |
| POST with amount = -5 → 400 | Validation |
| POST with type = "TRANSFER" → 400 | Validation |

---

## 9. Request / Response Examples

### POST /events — 201 Created
```json
POST /events
{
  "eventId": "evt-001",
  "accountId": "acct-123",
  "type": "CREDIT",
  "amount": 150.00,
  "currency": "USD",
  "eventTimestamp": "2026-05-15T14:02:11Z",
  "metadata": { "source": "mainframe-batch", "batchId": "B-9042" }
}

→ 201 Created
{
  "eventId": "evt-001",
  "accountId": "acct-123",
  "type": "CREDIT",
  "amount": 150.00,
  "currency": "USD",
  "eventTimestamp": "2026-05-15T14:02:11Z",
  "receivedAt": "2026-05-19T10:00:00Z",
  "metadata": { "source": "mainframe-batch", "batchId": "B-9042" }
}
```

### GET /accounts/{accountId}/balance — 200 OK
```json
GET /accounts/acct-123/balance

→ 200 OK
{
  "accountId": "acct-123",
  "balance": 100.00,
  "currency": "USD"
}
```

---

## 10. Bonus Features (if time permits)

| Feature | Approach |
|---|---|
| Swagger UI | `springdoc-openapi-starter-webmvc-ui` dependency — zero config |
| Docker | `Dockerfile` + `docker-compose.yml`, `docker compose up` starts the app |
| Pagination | `Pageable` parameter on `GET /events?account=X&page=0&size=20` |
| Concurrency test | `ExecutorService` with 10 threads all POSTing the same `eventId` |
