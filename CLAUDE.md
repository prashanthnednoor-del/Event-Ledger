# Event Ledger API — Claude Instructions

## Build & Run Commands

```bash
# Run all tests
mvn test

# Start the application
mvn spring-boot:run

# Build a JAR (skip tests)
mvn package -DskipTests

# Docker
docker compose up --build
```

App runs on **port 8080**. Swagger UI: `http://localhost:8080/swagger-ui.html`.

---

## Project Structure

```
src/main/java/com/eventledger/
├── EventLedgerApplication.java       ← @SpringBootApplication entry point
├── config/
│   └── OpenApiConfig.java            ← Swagger title/version/description
├── controller/
│   ├── EventController.java          ← POST /events, GET /events, GET /events/{id}
│   └── AccountController.java        ← GET /accounts/{accountId}/balance
├── service/
│   └── EventService.java             ← business logic, idempotency, balance
├── repository/
│   └── EventRepository.java          ← JPA queries + JPQL aggregate for balance
├── model/
│   ├── Event.java                    ← JPA entity (@Id = eventId)
│   ├── EventRequest.java             ← inbound DTO with Bean Validation
│   ├── EventResult.java              ← record(Event, boolean created) for 200 vs 201
│   ├── BalanceResponse.java          ← outbound DTO for balance endpoint
│   ├── PagedResponse.java            ← wraps Spring Page<T> into clean JSON
│   ├── BigDecimalScaleSerializer.java← forces amount to 2dp in JSON output
│   └── MapToJsonConverter.java       ← JPA converter: Map<String,Object> ↔ TEXT
└── exception/
    ├── GlobalExceptionHandler.java   ← @RestControllerAdvice: 400/404/500
    ├── EventNotFoundException.java   ← triggers 404
    └── ErrorResponse.java            ← {status, error, messages[]}
```

Single test class: `src/test/java/com/eventledger/EventLedgerIntegrationTest.java` — 20 tests.

---

## Key Design Decisions (Non-Obvious)

### Idempotency — optimistic insert, catch the constraint
`eventId` is the JPA `@Id` (primary key). On duplicate POST, `save()` throws `DataIntegrityViolationException`. The service catches it and returns the already-stored original. **Do not change this to a check-then-insert pattern** — that has a race condition window. The catch approach is race-safe.

### Out-of-order tolerance — no special logic needed
Balance is always a fresh JPQL aggregate over all events. Event list is always `ORDER BY eventTimestamp ASC`. There is **no mutable running balance**. Out-of-order delivery is a non-issue by design.

### Amount serialization — 2 decimal places
The DB column is `DECIMAL(19,4)`. Without the serializer, Jackson outputs `150.0000`. `BigDecimalScaleSerializer` forces 2dp (`setScale(2, HALF_UP)`) on JSON output only — the DB precision is unchanged.

### receivedAt is hidden from API responses
`receivedAt` is set by `@PrePersist` and annotated `@JsonIgnore` on the entity. It is audit-only. Do not expose it in API responses.

### Balance currency
Uses the currency from the account's earliest event (`findCurrenciesByAccountId` returns `ORDER BY eventTimestamp ASC`). Defaults to `"USD"` if no events exist. The system does not validate cross-currency consistency.

### Metadata stored as JSON TEXT
`metadata` is an optional `Map<String,Object>` serialized to a TEXT column via `MapToJsonConverter`. It is never queried — only stored and returned.

---

## API Endpoints

| Method | Path | Response | Notes |
|--------|------|----------|-------|
| POST | `/events` | 201 (new) / 200 (duplicate) / 400 (invalid) | Idempotent by eventId |
| GET | `/events/{id}` | 200 / 404 | Look up by eventId |
| GET | `/events?account=X&page=0&size=20` | 200 | Paginated, sorted by eventTimestamp ASC |
| GET | `/accounts/{accountId}/balance` | 200 | Always 200, returns 0.00 if no events |

---

## Coding Conventions

- **No Lombok** — all getters/setters/constructors are written explicitly
- **No MapStruct** — mapping from `EventRequest` → `Event` is in `EventService.mapToEntity()`
- **Validation on DTO only** — `EventRequest` carries `@NotBlank`, `@Positive`, `@Pattern` etc.; the `Event` entity has no validation annotations
- **Integration tests only** — `@SpringBootTest + @AutoConfigureMockMvc`, no unit tests with mocks. `@BeforeEach` calls `eventRepository.deleteAll()` to isolate each test
- **OpenAPI annotations** — all endpoints use `@Tag`, `@Operation`, `@ApiResponse`, `@Parameter`; all DTOs use `@Schema`
- **Java text blocks** — used in tests for JSON payloads (`""" ... """`)

---

## Technology Stack

| Layer | Choice |
|-------|--------|
| Language | Java 21 |
| Framework | Spring Boot 3.3.0 |
| Database | H2 in-memory (`jdbc:h2:mem:eventledger`) |
| ORM | Spring Data JPA / Hibernate |
| Validation | Jakarta Bean Validation |
| API Docs | springdoc-openapi-starter-webmvc-ui 2.5.0 |
| Build | Maven 3.9 |
| Container | Docker (multi-stage: maven:3.9-eclipse-temurin-21-alpine → eclipse-temurin:21-jre-alpine) |

H2 console available at `http://localhost:8080/h2-console` (JDBC URL: `jdbc:h2:mem:eventledger`, user: `sa`, no password).

---

## Things to Avoid

- Do not add an `accounts` table — balances are always derived from events
- Do not cache balances — correctness over read performance at this scale
- Do not expose `receivedAt` in API responses
- Do not switch to check-then-insert for idempotency — use the current catch-on-constraint pattern
- Do not add `@Transactional` to `submitEvent()` without understanding the idempotency catch — wrapping the save + catch in one transaction causes the exception to roll back before the catch can fetch the original
