# Take-Home Project: Event Ledger API

## Overview

Welcome, and thank you for your interest in this role!

This take-home project is designed to give you a chance to demonstrate your software engineering skills in a realistic scenario. **You are expected and encouraged to use AI tools** (GitHub Copilot, ChatGPT, Claude, Cursor, or any other tool) to help you work — just as you would on the job. What we're evaluating is the quality of the end result and the engineering decisions you make along the way.

**Time expectation:** 1–2 hours to produce a working solution.

---

## The Problem

You are building an **Event Ledger API** that receives financial transaction events from multiple upstream systems. These upstream systems are not perfectly synchronized, so:

- **Events may arrive out of order** — an event with an earlier timestamp may arrive after one with a later timestamp.
- **Events may be delivered more than once** — the same event could be sent to your API multiple times.

Your API must handle both of these scenarios correctly.

---

## Required Endpoints

| Method | Endpoint | Description |
|---|---|---|
| `POST` | `/events` | Submit a transaction event |
| `GET` | `/events/{id}` | Retrieve a single event by its ID |
| `GET` | `/events?account={accountId}` | List events for an account, ordered by event timestamp |
| `GET` | `/accounts/{accountId}/balance` | Get the current computed balance for an account |

---

## Event Payload

```json
{
  "eventId": "evt-001",
  "accountId": "acct-123",
  "type": "CREDIT",
  "amount": 150.00,
  "currency": "USD",
  "eventTimestamp": "2026-05-15T14:02:11Z",
  "metadata": {
    "source": "mainframe-batch",
    "batchId": "B-9042"
  }
}
```

| Field | Type | Required | Notes |
|---|---|---|---|
| `eventId` | string | Yes | Unique identifier for the event |
| `accountId` | string | Yes | The account this event belongs to |
| `type` | string | Yes | Must be `"CREDIT"` or `"DEBIT"` |
| `amount` | number | Yes | Must be greater than 0 |
| `currency` | string | Yes | e.g., `"USD"` |
| `eventTimestamp` | string (ISO 8601) | Yes | When the event originally occurred |
| `metadata` | object | No | Optional additional context |

---

## Requirements

### 1. Idempotency

Submitting the same `eventId` more than once must **not** create a duplicate event or alter the account balance. On a duplicate submission, return the original event with an appropriate status code.

### 2. Out-of-Order Tolerance

Events may arrive with `eventTimestamp` values that are earlier than previously received events. Your API must ensure that:
- The event listing (`GET /events?account=...`) always returns events in chronological order by `eventTimestamp`, regardless of the order they were received.
- The balance computation is always correct, regardless of arrival order.

### 3. Balance Computation

The `GET /accounts/{accountId}/balance` endpoint returns the **net balance** for the account:

```
balance = sum(CREDIT amounts) - sum(DEBIT amounts)
```

### 4. Validation

Reject events with:
- Missing required fields
- Negative or zero amounts
- Unknown event types (anything other than `CREDIT` or `DEBIT`)

Return clear, meaningful error messages with appropriate HTTP status codes.

### 5. Automated Tests

Include tests that cover:
- Idempotency (duplicate event submissions)
- Out-of-order event arrival
- Balance computation accuracy
- Input validation and error cases

Tests must be runnable with a standard command (e.g., `mvn test`, `pytest`, `dotnet test`).

### 6. README

Include a `README.md` with:
- Setup instructions (prerequisites, how to install dependencies)
- How to start the application
- How to run the tests

---

## Constraints

- **Language:** Java, Python, or C# (your choice)
- **Database:** Use an in-memory or embedded database (e.g., H2, SQLite) — no external database setup should be required
- **Runnable locally** with a single command
- **Framework:** Your choice (Spring Boot, Flask/FastAPI, ASP.NET, etc.)

---

## Submission

- Submit your solution as a **Git repository** (GitHub, GitLab, Bitbucket, etc.)
- Your **commit history should reflect your working process** — please don't squash everything into a single commit

---

## Bonus Opportunities (Not Required)

If you finish early or want to go further, consider:

- Pagination on the event listing endpoint
- Dockerized setup (`docker compose up`)
- API documentation (Swagger/OpenAPI)
- Concurrency handling (what happens with simultaneous POSTs for the same `eventId`?)

---

## Questions?

If anything is unclear, please reach out to your recruiter or hiring manager. We'd rather you ask than guess.

Good luck, and have fun with it!
