# Data Model Rules

- Do NOT add an `accounts` table — account balances are always derived by aggregating the `events` table; there is no mutable account state
- Do NOT cache or store a running balance — compute it fresh on every read via the JPQL aggregate in `EventRepository.computeBalance()`
- `eventId` is the JPA `@Id` (natural primary key) — this is what enforces idempotency at the DB level; do not add a surrogate key
- `receivedAt` is audit-only — it is set by `@PrePersist` and must stay `@JsonIgnore`; never expose it in API responses
- `amount` is stored as `DECIMAL(19,4)` for precision — serialized to 2dp via `BigDecimalScaleSerializer`; do not change the DB column precision
- `metadata` is stored as a JSON TEXT column via `MapToJsonConverter` — it is never queried, only stored and returned; do not add a separate metadata table
- Add an `@Index` annotation whenever a new column will be used as a filter in queries (as done for `account_id`)
