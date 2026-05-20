# Event Ledger — Architecture Diagrams

---

## 1. System Layers

```mermaid
graph TD
    Client(["Upstream System / API Client"])

    subgraph API["Event Ledger API (Spring Boot 3.3 · Java 21 · Port 8080)"]
        direction TB

        subgraph Controllers["Controller Layer"]
            EC["EventController\nPOST /events\nGET /events/{id}\nGET /events?account="]
            AC["AccountController\nGET /accounts/{id}/balance"]
        end

        subgraph Service["Service Layer"]
            ES["EventService\nIdempotency · Balance · Listing"]
        end

        subgraph Repository["Repository Layer"]
            ER["EventRepository\nJPA · JPQL Aggregates · Pagination"]
        end

        subgraph CrossCutting["Cross-Cutting"]
            GEH["GlobalExceptionHandler\n@RestControllerAdvice"]
            OAC["OpenApiConfig\nSwagger UI"]
        end
    end

    subgraph DB["Embedded Database"]
        H2[("H2 In-Memory\nevents table")]
    end

    Client -->|"HTTP REST"| Controllers
    EC --> ES
    AC --> ES
    ES --> ER
    ER --> H2
    GEH -.->|"intercepts exceptions"| Controllers
    OAC -.->|"generates docs"| Controllers
```

---

## 2. Data Model (ER Diagram)

```mermaid
erDiagram
    EVENTS {
        varchar event_id PK "Natural PK — enforces idempotency"
        varchar account_id    "Indexed for fast lookups"
        varchar type          "CREDIT or DEBIT"
        decimal amount        "DECIMAL(19-4) — stored 4dp, served 2dp"
        varchar currency      "e.g. USD"
        timestamp event_timestamp "Business time — sort key"
        timestamp received_at "Wall-clock arrival — audit only, hidden from API"
        text metadata         "Optional JSON blob"
    }
```

> No `ACCOUNTS` table. Balances are always derived from `EVENTS` by aggregation — there is no mutable account record.

---

## 3. POST /events — Idempotency Flow

```mermaid
sequenceDiagram
    participant C as Client
    participant EC as EventController
    participant ES as EventService
    participant DB as H2 Database

    C->>EC: POST /events {eventId, ...}
    EC->>EC: @Valid — validate all fields
    alt Validation fails
        EC-->>C: 400 Bad Request + ErrorResponse
    end

    EC->>ES: submitEvent(request)
    ES->>DB: INSERT INTO events (event_id=X, ...)

    alt eventId is new
        DB-->>ES: success
        ES-->>EC: EventResult(event, created=true)
        EC-->>C: 201 Created + Event body
    else eventId already exists (PK violation)
        DB-->>ES: DataIntegrityViolationException
        ES->>DB: SELECT * FROM events WHERE event_id=X
        DB-->>ES: original event
        ES-->>EC: EventResult(event, created=false)
        EC-->>C: 200 OK + original Event body (unchanged)
    end
```

---

## 4. Concurrent Duplicate Handling

```mermaid
sequenceDiagram
    participant T1 as Thread 1
    participant T2 as Thread 2
    participant T3 as Thread 3
    participant DB as H2 Database (PK Constraint)

    Note over T1,T3: All 3 threads fire simultaneously with same eventId

    par Simultaneous inserts
        T1->>DB: INSERT event_id='evt-X'
    and
        T2->>DB: INSERT event_id='evt-X'
    and
        T3->>DB: INSERT event_id='evt-X'
    end

    DB-->>T1: ✅ success (wins the race)
    DB-->>T2: ❌ DataIntegrityViolationException
    DB-->>T3: ❌ DataIntegrityViolationException

    T1-->>T1: return 201 Created
    T2->>DB: SELECT * WHERE event_id='evt-X'
    DB-->>T2: original event
    T2-->>T2: return 200 OK
    T3->>DB: SELECT * WHERE event_id='evt-X'
    DB-->>T3: original event
    T3-->>T3: return 200 OK

    Note over T1,T3: Result: 1 row in DB · All threads return valid responses
```

---

## 5. GET /events — Out-of-Order Tolerance

```mermaid
sequenceDiagram
    participant US as Upstream Systems
    participant API as Event Ledger API
    participant DB as H2 Database

    Note over US,DB: Events arrive in random order

    US->>API: POST evt-C  (eventTimestamp: 12:00)
    API->>DB: INSERT evt-C
    US->>API: POST evt-A  (eventTimestamp: 10:00)
    API->>DB: INSERT evt-A
    US->>API: POST evt-B  (eventTimestamp: 11:00)
    API->>DB: INSERT evt-B

    Note over DB: DB row order: evt-C, evt-A, evt-B (arrival order)

    US->>API: GET /events?account=X
    API->>DB: SELECT ... ORDER BY event_timestamp ASC
    DB-->>API: [evt-A, evt-B, evt-C]
    API-->>US: 200 OK · content: [evt-A, evt-B, evt-C] ✅

    US->>API: GET /accounts/X/balance
    API->>DB: SELECT SUM(CASE WHEN type='CREDIT' THEN amount ELSE -amount END)
    DB-->>API: correct net balance
    API-->>US: 200 OK · balance: correct ✅

    Note over API: No reprocessing · No replay · Sort key = eventTimestamp
```

---

## 6. Balance Computation

```mermaid
flowchart TD
    A([GET /accounts/{accountId}/balance]) --> B[AccountController]
    B --> C[EventService.getBalance]
    C --> D[(JPQL Aggregate Query)]

    D --> E{"Any events\nfor account?"}

    E -->|No| F["COALESCE returns 0\ncurrency defaults to USD"]
    E -->|Yes| G["SUM CREDIT amounts\n− SUM DEBIT amounts"]

    G --> H[Fetch earliest event's currency]
    H --> I["Build BalanceResponse\naccountId · balance · currency"]
    F --> I

    I --> J["Serialize balance\nto 2 decimal places"]
    J --> K([200 OK · BalanceResponse JSON])

    style D fill:#e8f4f8
    style K fill:#d4edda
    style F fill:#fff3cd
```

---

## 7. Validation & Error Handling

```mermaid
flowchart TD
    REQ(["Inbound HTTP Request"]) --> PARSE["Jackson deserializes\nrequest body"]

    PARSE -->|malformed JSON\nor bad Instant| HMR["HttpMessageNotReadable\nException"]
    HMR --> R400A([400 Bad Request\nMalformed request body])

    PARSE --> VAL["@Valid triggers\nBean Validation\non EventRequest"]
    VAL -->|constraint violated| MAV["MethodArgumentNotValid\nException"]
    MAV --> R400B(["400 Bad Request\n{ messages: [field: reason, ...] }"])

    VAL -->|all fields valid| SVC["EventService"]
    SVC -->|eventId not found| ENF["EventNotFoundException"]
    ENF --> R404([404 Not Found])

    SVC -->|unhandled error| EX["Exception (catch-all)"]
    EX --> R500([500 Internal Server Error])

    SVC -->|success| R2XX(["200 OK or 201 Created"])

    style R400A fill:#f8d7da
    style R400B fill:#f8d7da
    style R404 fill:#f8d7da
    style R500 fill:#f8d7da
    style R2XX fill:#d4edda
```

---

## 8. Deployment Architecture

```mermaid
graph LR
    subgraph Local["Local Development"]
        DEV["mvn spring-boot:run\nPort 8080"]
    end

    subgraph Docker["Docker (docker compose up)"]
        subgraph Build["Build Stage\nmaven:3.9-eclipse-temurin-21-alpine"]
            MVN["mvn package -DskipTests\n→ event-ledger.jar"]
        end
        subgraph Runtime["Runtime Stage\neclipse-temurin:21-jre-alpine"]
            JAR["java -jar app.jar\nPort 8080"]
        end
        MVN -->|"COPY *.jar"| JAR
    end

    subgraph Endpoints["Exposed Endpoints"]
        API2["localhost:8080\nREST API"]
        SW["localhost:8080/swagger-ui.html\nSwagger UI"]
        H2C["localhost:8080/h2-console\nH2 Browser Console"]
    end

    Local --> Endpoints
    Runtime --> Endpoints
```
