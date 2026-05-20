---
paths:
  - "src/test/**/*.java"
---

# Testing Rules

## Test Style
- Integration tests only — `@SpringBootTest` + `@AutoConfigureMockMvc`; no unit tests with Mockito mocks
- All tests live in `EventLedgerIntegrationTest.java` (single class)
- `@BeforeEach` must call `eventRepository.deleteAll()` to fully isolate each test
- Use the `postEvent()` and `eventJson()` helper methods for setup — do not inline raw JSON in each test

## Assertions
- Use MockMvc `jsonPath()` for all response body assertions
- Use `status().isCreated()` (201), `status().isOk()` (200), `status().isBadRequest()` (400), `status().isNotFound()` (404)
- For paginated responses, assert on `$.content[n].fieldName`, `$.totalElements`, `$.totalPages`, `$.page`, `$.size`
- Assert `$.receivedAt` with `.doesNotExist()` — it must never appear in responses

## Concurrency Tests
- Use `ExecutorService` + `CountDownLatch` to fire threads simultaneously
- All responses must be 200 or 201 — never 500
- After the concurrent test, assert `eventRepository.count()` equals 1 for same-eventId submissions

## What NOT to Test
- Do not mock `EventService` or `EventRepository` — tests must hit the real H2 DB
- Do not test Spring Boot internals (auto-configuration, bean wiring)
- Do not write tests for `mapToEntity()` in isolation — it is covered by the submit happy-path test
