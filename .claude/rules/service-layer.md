---
paths:
  - "src/main/java/com/eventledger/service/**/*.java"
---

# Service Layer Rules

## Idempotency — optimistic insert pattern (do not change)
The correct pattern in `submitEvent()` is:
1. Call `repository.save(event)` directly — no existence check first
2. Catch `DataIntegrityViolationException` if the eventId already exists
3. Fetch and return the original event from the DB

**Do not replace this with check-then-insert** (`findById` → skip if present). That pattern has a race condition: two concurrent threads can both pass the existence check and both attempt the insert.

**Do not wrap `submitEvent()` in `@Transactional`**. If the save and catch are in a single transaction, the `DataIntegrityViolationException` marks the transaction for rollback before the catch block can execute the fallback `findById`. The current design intentionally omits `@Transactional` on this method.

## Balance Computation
- Always delegate to `EventRepository.computeBalance()` — a single JPQL aggregate query
- Never accumulate a running total in application code
- Balance for an account with no events returns `BigDecimal` zero (from `COALESCE(..., 0)` in the query) — return it as-is, not a 404

## Out-of-Order Tolerance
- No special handling required — balance is always a full re-aggregate, list is always `ORDER BY eventTimestamp ASC`
- Do not add arrival-order logic or reprocessing queues
