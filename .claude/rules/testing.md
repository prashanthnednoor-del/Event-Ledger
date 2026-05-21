---
paths:
  - "src/test/**/*.java"
---

# Testing Rules

## Two Test Suites

The project has three test classes/groups — choose the right one for new tests:

| Type | Annotation | When to use |
|------|-----------|-------------|
| Integration | `@SpringBootTest + @AutoConfigureMockMvc` | End-to-end HTTP behaviour, real H2 DB |
| Unit (service/filter) | `@ExtendWith(MockitoExtension.class)` + `@Mock` + `@InjectMocks` | Isolated logic, branch coverage, edge cases |
| Unit (controller) | `@WebMvcTest(XController.class)` + `@MockBean EventService` | HTTP layer in isolation |
| Unit (model/converter/exception) | Plain JUnit 5, no mocks | Pure logic, no Spring context |
| Contract | `@SpringBootTest(webEnvironment = RANDOM_PORT)` + `@AutoConfigureMockMvc` | Response shape vs OpenAPI spec |

## Test-First Agent

Before writing a new method, run `/test-first ClassName.methodName` in Claude Code.
Claude reads the method signature and `.claude/rules/` files, then writes failing tests.
Implement the method until the tests pass.

## Integration Tests (`EventLedgerIntegrationTest.java`)

- `@BeforeEach` must call `eventRepository.deleteAll()` to fully isolate each test
- Use the `postEvent()` and `eventJson()` helper methods for setup — do not inline raw JSON
- Use MockMvc `jsonPath()` for all response body assertions
- Use `status().isCreated()` (201), `status().isOk()` (200), `status().isBadRequest()` (400), `status().isNotFound()` (404)
- For paginated responses, assert on `$.content[n].fieldName`, `$.totalElements`, `$.totalPages`, `$.page`, `$.size`
- Assert `$.receivedAt` with `.doesNotExist()` — it must never appear in responses
- Do not mock `EventService` or `EventRepository` — tests must hit the real H2 DB

## Unit Tests

- Use `@ExtendWith(MockitoExtension.class)` for service and filter tests
- Use `@WebMvcTest` for controller tests (lighter than `@SpringBootTest`)
- Use plain JUnit 5 for model, converter, and exception tests
- Use Java text blocks (`""" ... """`) for JSON payloads in controller tests
- No Lombok; no comments explaining WHAT the code does

## Concurrency Tests (Integration only)

- Use `ExecutorService` + `CountDownLatch` to fire threads simultaneously
- All responses must be 200 or 201 — never 500
- After the concurrent test, assert `eventRepository.count()` equals 1 for same-eventId submissions

## What NOT to Test

- Do not test Spring Boot internals (auto-configuration, bean wiring)
- Do not write tests for `mapToEntity()` in isolation — covered by the integration happy-path test
- Do not duplicate assertions already in `EventLedgerIntegrationTest` with identical unit tests
