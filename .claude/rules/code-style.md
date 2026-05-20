# Java Code Style

- No Lombok — write all constructors, getters, and setters explicitly
- No MapStruct — DTO-to-entity mapping lives in `EventService.mapToEntity()`
- No comments explaining WHAT code does — only add a comment when the WHY is non-obvious (hidden constraint, workaround, subtle invariant)
- Use Java text blocks (`""" ... """`) for multiline JSON strings in tests
- Prefer `record` for simple immutable carriers (see `EventResult.java`)
- All new model classes go under `com.eventledger.model`
- All new exception classes go under `com.eventledger.exception` and must be handled in `GlobalExceptionHandler`
