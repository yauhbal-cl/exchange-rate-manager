# Phase 1 Data Model: Scaffold Backend and Frontend

This feature introduces no persisted domain entities (no rate data, currencies, or usage counters
— those belong to later features per spec Assumptions). The only structural artifacts are the
contract-defined shapes below, which exist to prove the generation pipeline works end-to-end.

## ServiceStatus (contract schema, not a DB entity)

Represents the backend's self-reported health, returned by the sample contract endpoint.

| Field | Type | Notes |
|---|---|---|
| `status` | string (enum: `UP`, `DOWN`) | Overall service status |
| `databaseConnected` | boolean | Result of a live datasource check |
| `timestamp` | string (date-time, ISO-8601) | When the check was performed |

**Validation rules**: `status` and `databaseConnected` are required; `status` MUST be `DOWN` if
`databaseConnected` is `false` (enforced in the service layer per Constitution Principle VI —
thin controller, logic in service).

**State transitions**: None — computed fresh per request, not persisted.

## Local Development Infrastructure (operational, not a domain entity)

- **PostgreSQL 17 instance**: started via `docker-compose.yml`; connection parameters
  (host/port/db/user/password) consumed by `backend/src/main/resources/application.yml`. No
  schema/migrations required for this feature — the datasource only needs to be reachable and
  authenticate successfully for the health check to report `databaseConnected: true`.

## Out of scope for this feature (tracked for later features)

- ExchangeRate (currency_code, rate_date, rate value as BigDecimal) — Constitution II, III
- UsageCounter (atomic increment target) — Constitution V
- CurrencySpread (keyed lookup table, Appendix B) — Constitution VII
- AI Insight request/response shapes — Constitution VIII
