---
paths:
  - "src/main/java/com/eventledger/controller/**/*.java"
---

# Controller Rules

## OpenAPI Annotations (required on every endpoint)
- Class level: `@Tag(name = "...", description = "...")`
- Method level: `@Operation(summary = "...", description = "...")` with one `@ApiResponse` per possible HTTP status
- Path/query variables: `@Parameter(description = "...", example = "...")`
- For 400/404 error responses, set `content = @Content(schema = @Schema(implementation = ErrorResponse.class))`

## Response Codes
- `POST /events` — 201 for new events, 200 for duplicate eventId (never 409)
- `GET /events/{id}` — 404 if not found (throws `EventNotFoundException`, handled globally)
- `GET /accounts/{accountId}/balance` — always 200, even if account has no events (returns 0.00)

## Controller Responsibilities
- Controllers contain no business logic — delegate everything to `EventService`
- Use `@Valid` on `@RequestBody` parameters to trigger Bean Validation
- Return `ResponseEntity<T>` so status codes are explicit
- Do not catch exceptions in controllers — `GlobalExceptionHandler` handles them all
