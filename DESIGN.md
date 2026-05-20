# Event Ledger API — Design Document

## 1. Overview

Event Ledger is a self-contained REST API that ingests financial transaction events (CREDIT and DEBIT) from upstream systems and computes accurate account balances on demand. It provides three core guarantees: **idempotency** (submitting the same event twice has no additional effect), **out-of-order tolerance** (events may arrive in any sequence and balances are always correct), and **concurrency safety** (simultaneous submissions of the same event are race-safe without explicit locking). The entire service runs on an embedded H2 in-memory database — no external infrastructure is required to run or test it.

---

## 2. Tech Stack

| Layer | Technology | Version |
|---|---|---|
| Language | Java | 21 |
| Framework | Spring Boot | 3.3.0 |
| Database | H2 (in-memory embedded) | Runtime-managed |
| ORM | Spring Data JPA / Hibernate | Via Spring Boot parent |
| Validation | Jakarta Bean Validation | Via Spring Boot parent |
| API Documentation | springdoc-openapi-starter-webmvc-ui | 2.5.0 |
| Build | Maven | 3.9 |
| Container | Docker (multi-stage) | — |

---

## 3. System Architecture

```
┌─────────────────────────────────────────────────────┐
│                   HTTP Request                      │
└───────────────────────┬─────────────────────────────┘
                        │
┌───────────────────────▼─────────────────────────────┐
│               Controller Layer                      │
│   EventController          AccountController        │
│   (POST/GET /events)       (GET /accounts/balance)  │
│                                                     │
│   • Routes HTTP verbs to service methods            │
│   • Applies @Valid on request bodies                │
│   • Translates EventResult → 201 or 200             │
│   • No business logic                               │
└───────────────────────┬─────────────────────────────┘
                        │
┌───────────────────────▼─────────────────────────────┐
│                Service Layer                        │
│                  EventService                       │
│                                                     │
│   • Idempotency: optimistic insert + catch          │
│   • Balance: fresh aggregate on every call          │
│   • DTO → Entity mapping (mapToEntity)              │
│   • Throws EventNotFoundException for 404           │
└───────────────────────┬─────────────────────────────┘
                        │
┌───────────────────────▼─────────────────────────────┐
│              Repository Layer                       │
│                EventRepository                      │
│                                                     │
│   • Spring Data JPA interface                       │
│   • JPQL aggregate for balance computation          │
│   • Pageable queries for event listing              │
└───────────────────────┬─────────────────────────────┘
                        │
┌───────────────────────▼─────────────────────────────┐
│          H2 In-Memory Database                      │
│              events table                           │
│                                                     │
│   • Single table, natural PK (eventId)              │
│   • Index on account_id                             │
│   • Schema created/dropped on startup/shutdown      │
└─────────────────────────────────────────────────────┘
```

All Spring components use **constructor-based dependency injection** — no `@Autowired` on fields. `GlobalExceptionHandler` (`@RestControllerAdvice`) intercepts all exceptions before they reach the HTTP response.

---

## 4. Project Structure

```
src/main/java/com/eventledger/
├── EventLedgerApplication.java              ← @SpringBootApplication entry point
│
├── config/
│   └── OpenApiConfig.java                   ← Sets Swagger title, version, description
│
├── controller/
│   ├── EventController.java                 ← POST /events, GET /events/{id}, GET /events
│   └── AccountController.java               ← GET /accounts/{accountId}/balance
│
├── service/
│   └── EventService.java                    ← Business logic: idempotency, balance, listing
│
├── repository/
│   └── EventRepository.java                 ← JPA interface: balance aggregate, pageable queries
│
├── model/
│   ├── Event.java                           ← JPA entity (@Id = eventId, 8 columns)
│   ├── EventRequest.java                    ← Inbound DTO with Bean Validation annotations
│   ├── EventResult.java                     ← record(Event event, boolean created) — 201 vs 200 signal
│   ├── BalanceResponse.java                 ← Outbound DTO: {accountId, balance, currency}
│   ├── PagedResponse.java                   ← Wraps Spring Page<T> into clean JSON shape
│   ├── BigDecimalScaleSerializer.java       ← Forces amount to 2 decimal places in JSON
│   └── MapToJsonConverter.java              ← JPA converter: Map<String,Object> ↔ TEXT column
│
└── exception/
    ├── GlobalExceptionHandler.java          ← @RestControllerAdvice: 400 / 404 / 500 handlers
    ├── EventNotFoundException.java          ← RuntimeException that triggers 404
    └── ErrorResponse.java                   ← Error DTO: {status, error, messages[]}

src/main/resources/
└── application.yml                          ← H2, JPA, Swagger, server port config

src/test/java/com/eventledger/
└── EventLedgerIntegrationTest.java          ← 20 integration tests
```

---

## 5. Data Model

### `events` Table

| Column | Type | Constraints | Notes |
|---|---|---|---|
| `event_id` | VARCHAR | **PRIMARY KEY** | Natural key — enforces idempotency at DB level |
| `account_id` | VARCHAR | NOT NULL, INDEXED | `@Index` for fast account lookups |
| `type` | VARCHAR | NOT NULL | `CREDIT` or `DEBIT` only |
| `amount` | DECIMAL(19,4) | NOT NULL | 4dp stored; serialized as 2dp in JSON |
| `currency` | VARCHAR | NOT NULL | e.g. `USD` |
| `event_timestamp` | TIMESTAMP | NOT NULL | **Business time** — the sort key for ordering |
| `received_at` | TIMESTAMP | NOT NULL | Wall-clock arrival time — audit only, hidden from API |
| `metadata` | TEXT | NULLABLE | JSON blob; not queried, only stored and returned |

### Key Rationale

**No `accounts` table.** Account balances are always derived from the `events` table by aggregation. There is no mutable account record to update or get out of sync. Adding an accounts table would introduce a dual-write problem (event insert + balance update must be atomic).

**`eventId` is the primary key, not a surrogate.** Using the business identifier as the PK gives idempotency for free at the database constraint level. A surrogate auto-increment key would require a separate unique index and more complex duplicate-detection logic.

**`eventTimestamp` vs `receivedAt`.** `eventTimestamp` is the business timestamp — when the transaction actually occurred. `receivedAt` is when the API received the HTTP request. Sorting and balance aggregation always use `eventTimestamp`. `receivedAt` is stored for audit/debugging but never exposed in API responses.

---

## 6. API Contract

### POST /events — Submit a Transaction Event

**Request Body:**
```json
{
  "eventId":        "evt-001",
  "accountId":      "acct-123",
  "type":           "CREDIT",
  "amount":         150.00,
  "currency":       "USD",
  "eventTimestamp": "2026-05-15T14:02:11Z",
  "metadata":       { "source": "mainframe-batch", "batchId": "B-9042" }
}
```

**Validation Rules:**

| Field | Rule |
|---|---|
| `eventId` | Required, non-blank |
| `accountId` | Required, non-blank |
| `type` | Required, must be exactly `CREDIT` or `DEBIT` |
| `amount` | Required, must be > 0 (rejects 0 and negatives) |
| `currency` | Required, non-blank |
| `eventTimestamp` | Required, valid ISO 8601 instant |
| `metadata` | Optional |

**Responses:**

| Status | Condition | Body |
|---|---|---|
| `201 Created` | New event stored | Full event object |
| `200 OK` | Duplicate `eventId` — original returned unchanged | Full event object (identical to original) |
| `400 Bad Request` | Validation failed | `ErrorResponse` with per-field messages |

**Event Response Shape (201 / 200):**
```json
{
  "eventId":        "evt-001",
  "accountId":      "acct-123",
  "type":           "CREDIT",
  "amount":         150.00,
  "currency":       "USD",
  "eventTimestamp": "2026-05-15T14:02:11Z",
  "metadata":       { "source": "mainframe-batch", "batchId": "B-9042" }
}
```
Note: `receivedAt` is **never present** in responses.

---

### GET /events/{id} — Retrieve a Single Event

| Status | Condition |
|---|---|
| `200 OK` | Event found — returns full event object |
| `404 Not Found` | No event with that `eventId` — returns `ErrorResponse` |

---

### GET /events?account={accountId}&page={n}&size={m} — List Account Events

**Query Parameters:**

| Parameter | Default | Description |
|---|---|---|
| `account` | Required | Account ID to filter by |
| `page` | `0` | Zero-based page number |
| `size` | `20` | Events per page |

**Response (200 OK):**
```json
{
  "content": [
    { "eventId": "evt-a", "accountId": "acct-123", "type": "CREDIT", "amount": 100.00, ... },
    { "eventId": "evt-b", "accountId": "acct-123", "type": "DEBIT",  "amount":  50.00, ... }
  ],
  "page":          0,
  "size":          20,
  "totalElements": 2,
  "totalPages":    1
}
```

Events are always returned **sorted by `eventTimestamp` ascending** regardless of insertion order.

---

### GET /accounts/{accountId}/balance — Compute Account Balance

**Response (always 200 OK):**
```json
{
  "accountId": "acct-123",
  "balance":   250.00,
  "currency":  "USD"
}
```

- Returns `balance: 0.00` (not 404) if the account has no events.
- `currency` is taken from the account's **earliest event** (by `eventTimestamp`). Defaults to `"USD"` if no events exist.

---

## 7. Idempotency Strategy

### Why Check-Then-Insert Is Wrong

A naive implementation checks for existence before inserting:
```
if (repository.findById(eventId).isPresent()) {
    return existing;  // ← race window here
}
repository.save(newEvent);  // ← two threads can both reach this
```
Two concurrent threads can both pass the existence check simultaneously, then both attempt the insert. One succeeds, the other corrupts state or throws an unhandled exception.

### The Optimistic Insert + Catch Pattern

```java
public EventResult submitEvent(EventRequest request) {
    Event event = mapToEntity(request);
    try {
        return new EventResult(eventRepository.save(event), true);   // attempt insert
    } catch (DataIntegrityViolationException e) {
        // DB PK constraint rejected this eventId — fetch and return the original
        Event existing = eventRepository.findById(request.getEventId())
                .orElseThrow(() -> new IllegalStateException("..."));
        return new EventResult(existing, false);
    }
}
```

The database primary key constraint is the **sole synchronization point**. Exactly one concurrent thread wins the insert; all others catch the constraint violation and return the winner's stored event. No `synchronized` block, no distributed lock, no explicit transaction needed.

### Why `@Transactional` Must NOT Be Added Here

If `submitEvent()` is wrapped in a single `@Transactional` boundary:
1. `repository.save()` fails with `DataIntegrityViolationException`
2. Spring marks the active transaction for **rollback**
3. The catch block executes, but `findById()` runs inside a transaction already marked for rollback
4. `findById()` throws `TransactionSystemException` or returns nothing

The current design intentionally omits `@Transactional` on `submitEvent()`. The save and the catch-then-fetch are separate transaction boundaries. This is correct behavior, not an oversight.

### What Callers Receive

| Caller | Status | Body |
|---|---|---|
| First to store the event | `201 Created` | Stored event |
| All subsequent callers (any timing) | `200 OK` | The same stored event, unchanged |

No `409 Conflict` is ever returned — duplicate submission is not an error.

---

## 8. Concurrency Handling

When 10 threads simultaneously POST the same `eventId`:

```
Thread 1 ──┐
Thread 2 ──┤
Thread 3 ──┤
...        ├──► DB: INSERT INTO events (event_id=...) 
Thread 10 ─┘
             ↓
        Exactly 1 INSERT succeeds (wins the PK constraint)
        Remaining 9 receive DataIntegrityViolationException
             ↓
        9 threads: catch → findById → return original (200 OK)
        1 thread:  save succeeds → return new event (201 Created)
```

**Result:** Exactly 1 row in the database. All 10 callers receive a valid event response (200 or 201). No 500 errors. No data corruption.

This is verified by `concurrentDuplicates_onlyOneEventStored` in the test suite, which uses `ExecutorService(10 threads) + CountDownLatch` to fire all POSTs simultaneously and asserts `eventRepository.count() == 1`.

---

## 9. Out-of-Order Tolerance

The system handles out-of-order delivery **by design** — no special logic is needed.

**Core insight:** Balance is always a fresh full-table aggregate. The event list is always sorted by `eventTimestamp`, not by insertion order. Neither operation cares about arrival sequence.

**Example:**
```
Arrive order:  evt-c (timestamp 12:00) → evt-a (timestamp 10:00) → evt-b (timestamp 11:00)

GET /events?account=X  →  [evt-a, evt-b, evt-c]    ← sorted by eventTimestamp ASC
GET /accounts/X/balance →  correct sum              ← aggregates all three regardless of order
```

**What is NOT present in this codebase:**
- No event replay queue
- No reprocessing logic
- No timestamp comparison on insert
- No running balance counter to recompute

---

## 10. Balance Computation

### JPQL Aggregate Query

```java
@Query("""
    SELECT COALESCE(
        SUM(CASE WHEN e.type = 'CREDIT' THEN e.amount ELSE -e.amount END),
        0
    )
    FROM Event e WHERE e.accountId = :accountId
    """)
BigDecimal computeBalance(@Param("accountId") String accountId);
```

- `CASE WHEN` handles CREDIT/DEBIT in a single pass
- `COALESCE(..., 0)` returns zero (not null) for accounts with no events — this is why the balance endpoint always returns `200 OK` rather than `404`
- Result is always a `BigDecimal`, serialized to 2 decimal places

### Currency Selection

```java
@Query("SELECT e.currency FROM Event e WHERE e.accountId = :accountId ORDER BY e.eventTimestamp ASC")
List<String> findCurrenciesByAccountId(@Param("accountId") String accountId);
```

The first element is used as the balance's currency. Defaults to `"USD"` if the list is empty. The system does not validate that all events for an account share the same currency — that is the caller's responsibility.

### Why Computed on Read (Not Cached)

| Approach | Pros | Cons |
|---|---|---|
| **Compute on read (current)** | Always correct, no sync issues, simple | Slower at very high event volume |
| Cached/materialized balance | Fast reads | Can drift if update logic has a bug; dual-write complexity |

At the scope of this project (in-memory DB, take-home scale), correctness outweighs read performance. At production scale, a CQRS read model would be appropriate.

---

## 11. Validation & Error Handling

### Validation

All constraints are declared on `EventRequest` (the inbound DTO) using Jakarta Bean Validation annotations. The `Event` entity has zero validation annotations — it is only populated from validated data.

`@Valid` on the controller's `@RequestBody` parameter triggers validation before the method body executes. Any constraint violation throws `MethodArgumentNotValidException`, which is caught by `GlobalExceptionHandler`.

### Error Response Shape

```json
{
  "status":   400,
  "error":    "Validation Failed",
  "messages": [
    "amount: must be greater than 0",
    "type: must be CREDIT or DEBIT"
  ]
}
```

### Exception Handler Mapping

| Exception | HTTP Status | `error` field |
|---|---|---|
| `MethodArgumentNotValidException` | `400` | `"Validation Failed"` |
| `HttpMessageNotReadableException` | `400` | `"Malformed request body"` |
| `EventNotFoundException` | `404` | `"Not Found"` |
| `Exception` (catch-all) | `500` | `"Internal Server Error"` |

**No `409 Conflict`.** Duplicate `eventId` is not treated as an error — it is an idempotency guarantee. The original event is returned with `200 OK`.

---

## 12. Serialization Details

### Amount: 2 Decimal Places in JSON

`amount` is stored in the database as `DECIMAL(19,4)`. Without intervention, Jackson would serialize it as `150.0000`. `BigDecimalScaleSerializer` normalizes this on output:

```java
@Override
public void serialize(BigDecimal value, JsonGenerator gen, SerializerProvider p) throws IOException {
    gen.writeNumber(value.setScale(2, RoundingMode.HALF_UP));
}
```

Applied via `@JsonSerialize(using = BigDecimalScaleSerializer.class)` on the `amount` field in `Event.java`. The database column precision is unchanged — only the JSON representation is affected.

### Metadata: Map ↔ JSON TEXT

`MapToJsonConverter` implements `AttributeConverter<Map<String,Object>, String>`:

- **To DB:** `objectMapper.writeValueAsString(map)` → stores `{"key":"value"}` as a TEXT string
- **From DB:** `objectMapper.readValue(json, new TypeReference<Map<String,Object>>(){})` → restores the map

Metadata is nullable. When null, the converter returns null in both directions.

### PagedResponse: Clean JSON Wrapper

Spring's `Page<T>` interface contains many internal fields (sort details, pageable object, etc.) that should not leak into the API response. `PagedResponse<T>` extracts only the fields the API contract defines:

```java
public PagedResponse(Page<T> page) {
    this.content       = page.getContent();
    this.page          = page.getNumber();
    this.size          = page.getSize();
    this.totalElements = page.getTotalElements();
    this.totalPages    = page.getTotalPages();
}
```

### EventResult: 201 vs 200 Signal

`EventResult` is a Java `record` that carries both the stored event and a boolean flag:

```java
public record EventResult(Event event, boolean created) {}
```

The controller uses `result.created()` to decide the HTTP status — `201 Created` if true, `200 OK` if false. This keeps the status-code logic in the controller (where it belongs) and the idempotency logic in the service.

---

## 13. Pagination

`GET /events?account=X` supports pagination via `page` and `size` query parameters.

**Service implementation:**
```java
PageRequest pageable = PageRequest.of(page, size, Sort.by("eventTimestamp").ascending());
return new PagedResponse<>(eventRepository.findByAccountId(accountId, pageable));
```

**Behaviour:**
- Page and size default to `0` and `20` if omitted
- Requesting a page beyond the last page returns `content: []` with correct `totalElements` and `totalPages` — not a `404`
- Sort is always `eventTimestamp ASC` — cannot be changed by the caller

---

## 14. Testing Strategy

### Approach

Integration-only testing: `@SpringBootTest` loads the full Spring context; `@AutoConfigureMockMvc` provides an in-process HTTP client. All tests hit the real H2 database — no mocks, no stubs.

```java
@SpringBootTest
@AutoConfigureMockMvc
class EventLedgerIntegrationTest {
    @BeforeEach
    void setUp() { eventRepository.deleteAll(); }  // full isolation per test
}
```

### Test Coverage (20 Tests)

| Category | Count | What Is Verified |
|---|---|---|
| Happy path | 2 | Submit → 201 with full body; GET → 200 |
| Idempotency | 3 | Duplicate → 200 same body; duplicate doesn't affect balance; 10 concurrent → count = 1 |
| Out-of-order | 4 | List sorted by timestamp; pagination page 2; empty page; balance unaffected by arrival order |
| Balance | 3 | CREDIT+DEBIT net; debit-only → negative; no events → 0.00 |
| Not found | 1 | Unknown eventId → 404 with ErrorResponse |
| Validation | 7 | Missing eventId, accountId, timestamp, currency; zero amount; negative amount; invalid type |

### Concurrency Test Detail

```java
ExecutorService executor = Executors.newFixedThreadPool(10);
CountDownLatch ready = new CountDownLatch(10);

// All 10 threads count down, then await — they all unblock simultaneously
for (int i = 0; i < 10; i++) {
    futures.add(executor.submit(() -> {
        ready.countDown();
        ready.await();  // synchronization point — all fire at once
        return mockMvc.perform(post("/events").content(sameJson)).andReturn().getStatus();
    }));
}

// Assert: all responses are 200 or 201, exactly 1 row stored
assertThat(f.get()).isIn(200, 201);
assertThat(eventRepository.count()).isEqualTo(1);
```

### Key Assertion Patterns

```java
// Verify receivedAt is hidden
.andExpect(jsonPath("$.receivedAt").doesNotExist())

// Verify paginated response shape
.andExpect(jsonPath("$.content[0].eventId").value("evt-a"))
.andExpect(jsonPath("$.totalElements").value(3))
.andExpect(jsonPath("$.totalPages").value(1))

// Verify DB state directly
assertThat(eventRepository.count()).isEqualTo(1);
```

---

## 15. OpenAPI / Swagger

**Global API info** (`OpenApiConfig.java`):
- **Title:** Event Ledger API
- **Version:** 1.0.0
- **Description:** Receives financial transaction events from upstream systems. Enforces idempotency, tolerates out-of-order delivery, and computes accurate account balances.

**Annotation coverage:**

| Annotation | Applied to |
|---|---|
| `@Tag` | Controller classes |
| `@Operation` + `@ApiResponse` | Every endpoint method |
| `@Parameter` | Path variables and query params |
| `@Schema` | `EventRequest`, `BalanceResponse` DTOs |
| `@Content(schema = @Schema(implementation = ErrorResponse.class))` | 400/404 error responses |

**Swagger UI:** `http://localhost:8080/swagger-ui.html`

---

## 16. Configuration Reference

```yaml
server:
  port: 8080                                   # API port

spring:
  application:
    name: event-ledger

  datasource:
    url: jdbc:h2:mem:eventledger;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE
    #                            ↑ keeps H2 alive for the full app/test lifecycle
    driver-class-name: org.h2.Driver
    username: sa
    password:                                  # no password

  jpa:
    database-platform: org.hibernate.dialect.H2Dialect
    hibernate:
      ddl-auto: create-drop                    # fresh schema on every startup; wiped on shutdown
    show-sql: false

  h2:
    console:
      enabled: true
      path: /h2-console                        # browser DB inspector at localhost:8080/h2-console

springdoc:
  swagger-ui:
    path: /swagger-ui.html                     # redirects to /swagger-ui/index.html
```

---

## 17. Build & Deployment

### Local Development

```bash
mvn spring-boot:run          # start on port 8080
mvn test                     # run all 20 integration tests
mvn package -DskipTests      # build executable JAR in target/
```

### Docker

**`Dockerfile`** uses a two-stage build to minimise the final image:

```
Stage 1 — Build (maven:3.9-eclipse-temurin-21-alpine)
  COPY pom.xml → RUN mvn dependency:go-offline   # cache dependencies
  COPY src     → RUN mvn package -DskipTests      # compile and package

Stage 2 — Runtime (eclipse-temurin:21-jre-alpine)
  COPY --from=build target/*.jar app.jar
  EXPOSE 8080
  ENTRYPOINT ["java", "-jar", "app.jar"]
```

```bash
docker compose up --build    # build image and start container
```

**URLs after startup:**

| URL | Purpose |
|---|---|
| `http://localhost:8080/events` | API |
| `http://localhost:8080/swagger-ui.html` | Swagger UI |
| `http://localhost:8080/h2-console` | H2 database browser |

---

## 18. Design Decisions Summary

| Decision | Why | Impact |
|---|---|---|
| `eventId` as natural primary key | Idempotency enforced at DB constraint level — atomic and race-safe | No surrogate key; eventId must be unique across all events |
| No `@Transactional` on `submitEvent()` | Adding it causes rollback before catch block can execute `findById()` | Must understand exception flow before modifying idempotency logic |
| No `accounts` table | Avoids dual-write problem; balance is always a fresh aggregate | No mutable account state; schema stays simple |
| Sort by `eventTimestamp`, not insertion order | Handles out-of-order delivery without any reprocessing logic | Business timestamp is the source of truth for ordering |
| Balance computed on read | Correctness over read performance; no sync issues | Slower at high volume — CQRS cache appropriate at production scale |
| `receivedAt` hidden (`@JsonIgnore`) | Audit field only; not part of the API contract | Stored in DB but never returned to callers |
| `DECIMAL(19,4)` + 2dp JSON serializer | DB precision preserved; JSON follows financial formatting convention | Custom serializer (`BigDecimalScaleSerializer`) required |
| Metadata as JSON TEXT | Flexible schema; metadata is never queried, only passed through | `MapToJsonConverter` adds serialization overhead; acceptable for optional field |
