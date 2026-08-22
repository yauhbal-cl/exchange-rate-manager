# Implementation Plan: Exchange Rate API

**Branch**: `004-exchange-rate-api` | **Date**: 2026-08-22 | **Spec**: [spec.md](./spec.md)

**Input**: Feature specification from `/specs/004-exchange-rate-api/spec.md`

## Summary

Add `GET /exchange` (spread-adjusted rate lookup) and `GET /exchange/usage` (per-currency usage
analytics) to `contracts/openapi.yaml`, regenerate the server interfaces, and implement them in
the existing controller → service → repository stack. Core logic: an `ExchangeRateService`
resolves the effective rate date (most recent common date, or the requested date), loads both
currencies' `ExchangeRate` rows, applies a keyed spread lookup, computes
`(toRateUsd / fromRateUsd) × ((100 − MAX(toSpread, fromSpread)) / 100)` with `BigDecimal`, and —
only on success — atomically upserts both currencies' usage counters via one native
`INSERT ... ON CONFLICT` statement each. Unknown currency and same-currency pair reject with 400;
no-data-for-date rejects with 404 (per Clarifications, 2026-08-22) — both short-circuit before any
counter touch and surface as `ProblemDetail` via the existing `GlobalExceptionHandler`. The usage
analytics endpoint returns every currency the system has ever stored a rate record for, including
ones never queried (`queryCount = 0`, `lastQueriedAt = null`), per the same clarification session.

## Technical Context

**Language/Version**: Java 21

**Primary Dependencies**: Spring Boot 4.1.1 (Spring Framework 7.0.x), Spring Data JPA/Hibernate,
Lombok 1.18.42, MapStruct 1.6.3, openapi-generator-maven-plugin (server interfaces from
`contracts/openapi.yaml`)

**Storage**: PostgreSQL 17 — existing `exchange_rates`, `currency_usage` tables; no new table
needed for the spread reference (see Research: spread lookup)

**Testing**: `./mvnw verify` — JUnit 5 + Spring Boot Test (`@SpringBootTest`/`@DataJpaTest` /
`MockMvc`), Testcontainers-backed Postgres if already wired for existing tests (verify in
research), concurrent-increment test for FR-009/SC-003

**Target Platform**: Linux server (containerized), existing `docker-compose.yml` Postgres for
local dev

**Project Type**: web-service (backend module of the monorepo; no frontend change in this
feature)

**Performance Goals**: Not specified beyond correctness; lookup is a handful of indexed reads plus
two single-row atomic upserts — no batch/streaming concern

**Constraints**: No external Fixer.io call from this endpoint (FR-002); `BigDecimal` exact
precision throughout (FR-014); usage counter increments must not lose updates under concurrency
(FR-009, Constitution Principle V)

**Scale/Scope**: Two new endpoints, one new service, one new mapper, two repository additions, no
new entity (reuses `ExchangeRate`, `CurrencyUsage`), one new small static spread-lookup component

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| Principle | Applicability | Status |
|---|---|---|
| I. Monetary Precision | Rate calc uses `BigDecimal` end to end | PASS — design uses `BigDecimal` for rate, spread %, and division with explicit `MathContext`/scale |
| II. Accurate Rate Provenance | N/A here — this feature only reads `ExchangeRate.rateDate`, doesn't write it | PASS (no write path touched) |
| III. Idempotent Data Collection | N/A — no ingestion in this feature | N/A |
| IV. Multi-Instance Scheduler Safety | N/A — no scheduled job in this feature | N/A |
| V. Concurrency-Safe Usage Counters | FR-008/FR-009 | PASS — single native `INSERT ... ON CONFLICT (currency_code) DO UPDATE SET query_count = currency_usage.query_count + 1, last_queried_at = now()` per currency, no read-modify-write |
| VI. Layered Separation of Concerns | New logic | PASS — controller (`ExchangeController`) stays a thin `ExchangeApi` implementation; validation + spread calc live in a new `ExchangeRateService`; persistence stays in repositories |
| VII. Data-Driven Configuration Over Conditionals | FR-006, spread tiers | PASS — spread tiers modeled as a keyed `Map<String, BigDecimal>` lookup with a `DEFAULT` key, not if/else branches (see Research) |
| VIII. Grounded AI Output | N/A — no AI in this feature | N/A |
| IX. Environment-Configurable Frontend | N/A — backend-only feature | N/A |
| Dev/Quality: ProblemDetail shape | FR-004 (404), FR-007 (400), FR-013 | PASS — new exception types added to existing `GlobalExceptionHandler`, same pattern as `FixerApiException`/`CollectionInProgressException` |
| Dev/Quality: manual refresh excluded from counters | Already satisfied — `refreshExchangeRates` never touches `CurrencyUsageRepository` | PASS (no change needed) |

No violations requiring the Complexity Tracking table.

## Project Structure

### Documentation (this feature)

```text
specs/004-exchange-rate-api/
├── plan.md              # This file
├── research.md          # Phase 0 output
├── data-model.md         # Phase 1 output
├── quickstart.md         # Phase 1 output
├── contracts/            # Phase 1 output (OpenAPI path/schema additions, mirrored into contracts/openapi.yaml)
└── tasks.md              # Phase 2 output (/speckit-tasks — not created here)
```

### Source Code (repository root)

```text
contracts/
└── openapi.yaml                          # add GET /exchange, GET /exchange/usage paths + schemas

backend/src/main/java/com/exchangerate/manager/
├── controller/
│   └── ExchangeController.java           # implement new ExchangeApi methods (existing file)
├── service/
│   ├── ExchangeRateService.java          # NEW — orchestrates lookup + spread calc + counter upsert
│   ├── SpreadLookup.java                 # NEW — keyed spread reference (Map-backed, default tier)
│   ├── UnknownCurrencyException.java     # NEW
│   ├── SameCurrencyException.java        # NEW
│   └── RateDataNotFoundException.java    # NEW
├── repository/
│   ├── ExchangeRateRepository.java       # add: findLatestCommonDate / findByCurrencyCodeInAndRateDate / existsByCurrencyCode
│   └── CurrencyUsageRepository.java      # add: atomic upsert-increment native query;
│                                         # add: findAllCurrencyUsage() — LEFT JOIN of distinct
│                                         # exchange_rates.currency_code against currency_usage,
│                                         # so never-queried currencies return 0/null, not omitted
├── mapper/
│   └── ExchangeRateResponseMapper.java   # NEW — MapStruct: service result -> generated ExchangeRateResponse DTO
└── exception/
    └── GlobalExceptionHandler.java       # add handlers for the 3 new exception types -> ProblemDetail

backend/src/test/java/com/exchangerate/manager/
├── service/ExchangeRateServiceTest.java  # unit: spread formula, date resolution, error paths
└── controller/ExchangeControllerIT.java  # integration: full HTTP round trip incl. concurrency test
```

**Structure Decision**: Existing single-module Spring Boot backend (`backend/`), monorepo sibling
to `frontend/`. No new module. Feature adds one service, one small lookup component, one mapper,
three exception types, and two repository query additions — all within the established
controller → service → repository layering already used by `RateCollectionService`/
`ExchangeController`.

## Complexity Tracking

*No violations — table not needed.*
