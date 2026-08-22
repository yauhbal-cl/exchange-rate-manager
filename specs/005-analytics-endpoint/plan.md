# Implementation Plan: Analytics Endpoint

**Branch**: `005-analytics-endpoint` | **Date**: 2026-08-22 | **Spec**: [spec.md](spec.md)

**Input**: Feature specification from `/specs/005-analytics-endpoint/spec.md`

**Note**: This template is filled in by the `/speckit-plan` command; its definition describes the execution workflow.

## Summary

Add a historical exchange-rate trend endpoint (`GET /exchange/trend`) computed entirely from
locally stored rates using the existing spread-adjustment formula, and extend the existing
usage-analytics endpoint (`GET /exchange/usage`) with optional `limit` (ranking) and `recentDays`
(recency filter) query parameters. No new external calls, no new tables — both features are read
models over `exchange_rates` and `currency_usage`, following the existing controller → service →
repository layering and centralized `ProblemDetail` error handling.

## Technical Context

**Language/Version**: Java 21

**Primary Dependencies**: Spring Boot 4.1.1, Spring Data JPA/Hibernate, Lombok, MapStruct,
openapi-generator-maven-plugin (server interfaces/DTOs generated from `contracts/openapi.yaml`)

**Storage**: PostgreSQL 17 (existing `exchange_rates`, `currency_usage` tables — no new migration)

**Testing**: JUnit 5 + Spring Boot Test, Testcontainers (Postgres) for DB-backed tests, per
Constitution Principle X / [[CLAUDE.md]] test-isolation rule

**Target Platform**: Linux server (containerized)

**Project Type**: Web application (backend + frontend, per repo's Monorepo Layout)

**Performance Goals**: No new goals beyond existing endpoints — trend query is a single bounded
range query per request, not a hot path

**Constraints**: Trend endpoint MUST NOT call the external rate provider (FR-002); analytics
reads MUST NOT mutate usage counters (FR-006, SC-005)

**Scale/Scope**: 2 endpoint changes: 1 new (`GET /exchange/trend`), 1 extended
(`GET /exchange/usage` gains 2 optional query params)

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| Principle | Applies? | Compliance approach |
|---|---|---|
| I. Monetary Precision | Yes | `RateTrendPoint.rate` is `BigDecimal`, computed with the same `MathContext` as `ExchangeRateService.lookup`; serialized as a decimal string like `ExchangeRateResponse.rate`. |
| II. Accurate Rate Provenance | N/A | No new writes; trend reads `rate_date` as already stored. |
| III. Idempotent Data Collection | N/A | No new ingestion. |
| IV. Multi-Instance Scheduler Safety | N/A | No new scheduled job. |
| V. Concurrency-Safe Usage Counters | Yes | Trend and usage-analytics reads never call `incrementUsage`; no read-modify-write introduced. |
| VI. Layered Separation of Concerns | Yes | New `AnalyticsService` (or extension of `ExchangeRateService`) holds validation + spread computation; controller stays a thin passthrough; repository holds the range/join query. |
| VII. Data-Driven Configuration Over Conditionals | Yes | Reuses existing `SpreadLookup`; no new conditional spread logic. |
| VIII. Grounded AI Output, Honest Degradation | N/A | This feature only exposes data; no AI call added here (per spec Assumptions). |
| IX. Environment-Configurable Frontend | N/A | Backend-only feature; frontend consumption is a separate concern. |
| X. Test Isolation via Testcontainers | Yes | New repository/service tests for the trend query and ranking/filter logic use Testcontainers, matching `ExchangeRateRepositoryTest`/`CurrencyUsageRepositoryTest`. |

No violations requiring justification — Complexity Tracking section is not needed.

## Project Structure

### Documentation (this feature)

```text
specs/005-analytics-endpoint/
├── plan.md              # This file (/speckit-plan command output)
├── research.md          # Phase 0 output (/speckit-plan command)
├── data-model.md        # Phase 1 output (/speckit-plan command)
├── quickstart.md        # Phase 1 output (/speckit-plan command)
├── contracts/           # Phase 1 output (/speckit-plan command)
│   └── analytics-endpoints.yaml
└── tasks.md             # Phase 2 output (/speckit-tasks command - NOT created by /speckit-plan)
```

### Source Code (repository root)

```text
backend/
├── src/main/java/com/exchangerate/manager/
│   ├── controller/
│   │   └── ExchangeController.java        # add getExchangeRateTrend, extend getUsageAnalytics
│   ├── service/
│   │   ├── ExchangeRateService.java       # or new AnalyticsService — trend query + validation
│   │   └── RateTrendPoint.java            # new transient result DTO
│   ├── repository/
│   │   ├── ExchangeRateRepository.java    # add range/join native query for trend
│   │   └── CurrencyUsageRepository.java   # add ranked/recency-filtered projection query
│   ├── mapper/
│   │   └── ExchangeRateTrendResponseMapper.java  # new — maps RateTrendPoint list to generated DTO
│   └── exception/
│       └── InvalidDateRangeException.java # new — startDate > endDate
└── src/test/java/com/exchangerate/manager/
    ├── repository/
    │   ├── ExchangeRateRepositoryTest.java     # extend: range/join query cases
    │   └── CurrencyUsageRepositoryTest.java    # extend: ranking/recency cases
    └── service/
        └── ExchangeRateServiceTest.java        # extend: trend validation + computation cases

contracts/openapi.yaml   # add /exchange/trend path + schemas; extend /exchange/usage params
frontend/src/app/api-client/  # regenerated via `npm run generate:api` (implementation step, not hand-edited)
```

**Structure Decision**: Extends the existing single-module Spring Boot backend
(`backend/src/main/java/com/exchangerate/manager/...`) in place — no new module or package root.
Following the repo's existing pattern, trend logic is added to `ExchangeRateService` (it already
owns spread computation and `ExchangeRateRepository`) rather than introducing a parallel
`AnalyticsService`, since the two share the identical spread formula and repository; usage
ranking/filtering is added to `CurrencyUsageRepository`/`UsageAnalyticsMapper`, the existing home
for usage-analytics data. Frontend changes (trend chart consumption) are out of scope for this
plan per spec Assumptions — only the backend contract and client regeneration are covered here.

## Complexity Tracking

*No violations — table not needed.*
