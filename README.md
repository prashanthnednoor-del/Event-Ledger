# Event Ledger API

A REST API that receives financial transaction events from multiple upstream systems, enforces idempotency, tolerates out-of-order delivery, and computes accurate account balances.

---

## Prerequisites

| Tool | Version | Download |
|---|---|---|
| Java (JDK) | 21 | [Eclipse Temurin](https://adoptium.net/temurin/releases/?version=21) |
| Maven | 3.9+ | [apache.org/maven](https://maven.apache.org/download.cgi) |

Verify your installation:
```bash
java -version   # openjdk version "21.x.x"
mvn --version   # Apache Maven 3.9.x
```

---

## Setup

Clone the repository and install dependencies:

```bash
git clone https://github.com/prashanthnednoor-del/Event-Ledger.git
cd Event-Ledger
mvn dependency:resolve
```

No external database setup is required — the application uses an **H2 in-memory database** that is created fresh on each startup.

---

## Run the Application

```bash
mvn spring-boot:run
```

The API starts on **http://localhost:8080**.

To confirm it is running, open the H2 database console at:
```
http://localhost:8080/h2-console
JDBC URL: jdbc:h2:mem:eventledger
Username: sa
Password: (leave blank)
```

---

## Run the Tests

```bash
mvn test
```

Expected output:
```
Tests run: 18, Failures: 0, Errors: 0, Skipped: 0
BUILD SUCCESS
```

The test suite uses an H2 in-memory database and requires no external dependencies. Each test starts with a clean database state.

---

## API Reference

### POST /events — Submit a transaction event

```bash
curl -X POST http://localhost:8080/events \
  -H "Content-Type: application/json" \
  -d '{
    "eventId": "evt-001",
    "accountId": "acct-123",
    "type": "CREDIT",
    "amount": 150.00,
    "currency": "USD",
    "eventTimestamp": "2026-05-15T14:02:11Z",
    "metadata": { "source": "mainframe-batch", "batchId": "B-9042" }
  }'
```

| Scenario | HTTP Status |
|---|---|
| New event | `201 Created` |
| Duplicate `eventId` | `200 OK` (returns original, no side effects) |
| Invalid payload | `400 Bad Request` with field-level error messages |

---

### GET /events/{id} — Retrieve a single event

```bash
curl http://localhost:8080/events/evt-001
```

Returns `404 Not Found` if the event does not exist.

---

### GET /events?account={accountId} — List events for an account

```bash
curl "http://localhost:8080/events?account=acct-123"
```

Always returns events in chronological order by `eventTimestamp`, regardless of the order they were received.

---

### GET /accounts/{accountId}/balance — Get account balance

```bash
curl http://localhost:8080/accounts/acct-123/balance
```

Returns the net balance: `sum(CREDIT amounts) - sum(DEBIT amounts)`.

```json
{
  "accountId": "acct-123",
  "balance": 100.00,
  "currency": "USD"
}
```

Returns `balance: 0.00` for accounts with no events.

---

## Swagger UI

Interactive API documentation is available at:
```
http://localhost:8080/swagger-ui.html
```

---

## Design Highlights

**Idempotency** — `eventId` is the database primary key. Duplicate submissions are caught by the unique constraint and the original event is returned — no double-counting, no errors. This approach is race-safe under concurrent submissions.

**Out-of-order tolerance** — Events are always listed and aggregated by `eventTimestamp`, never by insertion order. There is no running balance counter that could drift; every balance read is a fresh aggregate over all events.

**Validation** — All required fields are validated before persistence. Invalid payloads receive a `400` response with per-field error messages.

---

## Project Structure

```
src/
├── main/java/com/eventledger/
│   ├── EventLedgerApplication.java      ← entry point
│   ├── controller/
│   │   ├── EventController.java         ← POST /events, GET /events
│   │   └── AccountController.java       ← GET /accounts/{id}/balance
│   ├── service/
│   │   └── EventService.java            ← business logic, idempotency
│   ├── repository/
│   │   └── EventRepository.java         ← JPA queries, balance aggregate
│   ├── model/
│   │   ├── Event.java                   ← JPA entity
│   │   ├── EventRequest.java            ← inbound DTO (validated)
│   │   ├── BalanceResponse.java         ← outbound DTO
│   │   ├── EventResult.java             ← created vs duplicate signal
│   │   └── MapToJsonConverter.java      ← metadata JSON serialization
│   └── exception/
│       ├── GlobalExceptionHandler.java  ← validation + error responses
│       ├── EventNotFoundException.java  ← 404 trigger
│       └── ErrorResponse.java           ← error response shape
└── test/java/com/eventledger/
    └── EventLedgerIntegrationTest.java  ← 18 integration tests
```
