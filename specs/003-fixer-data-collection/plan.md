# Implementation Plan: Fixer.io Data Collection

**Branch**: `003-fixer-data-collection` | **Date**: 2026-08-22 | **Spec**: [spec.md](./spec.md)

**Input**: Feature specification from `/specs/003-fixer-data-collection/spec.md`

## Summary

Add a daily-scheduled (00:05 GMT) and manually-triggerable (`POST /exchange/refresh`) job that
calls Fixer.io's `/latest` endpoint (EUR base, free-tier constraint), derives each currency's
USD-relative rate via cross-rate calculation, and upserts one row per currency into the existing
`exchange_rates` table (spec 002) keyed on `(currency_code, rate_date)`. ShedLock (already
provisioned) guarantees a single call per run across instances and prevents the scheduled and
manual paths from overlapping. No usage-counter writes; no new persisted entities.

## Technical Context

**Language/Version**: Java 17 (per current `backend/pom.xml`; CLAUDE.md targets 21 — pre-existing
gap carried over from spec 002, out of scope here)

**Primary Dependencies**: `spring-boot-starter-web` (already present — provides `RestClient` for
the Fixer.io call), `shedlock-spring` + `shedlock-provider-jdbc-template` (already present —
needs `@EnableSchedulerLock` + a `LockProvider` bean added), `jackson-databind` (already present,
via Spring Boot — for `/latest` response deserialization). No new Maven dependencies required.

**Storage**: PostgreSQL 17 (existing `exchange_rates` table from spec 002; no new migration)

**Testing**: JUnit 5 + `spring-boot-starter-test` (already present); `MockRestServiceServer` for
the Fixer.io client; a real-Postgres repository test for the new upsert query, consistent with
`ExchangeRateRepositoryTest`'s existing pattern

**Target Platform**: Linux server (containerized/backend service), same as spec 002

**Project Type**: Web application — backend (`backend/`) + frontend (`frontend/`); this feature is
backend-only (contract addition only touches the frontend indirectly, via regenerated client)

**Performance Goals**: N/A — one batch call per scheduled/manual run, no request-path latency
target

**Constraints**: Exactly one Fixer.io call per run (FR-004, free-tier quota); rate values stored
as `BigDecimal` only (Constitution I); upsert must be DB-level atomic (Constitution III); scheduler
must be multi-instance-safe via ShedLock (Constitution IV); collection must never write
`currency_usage` (FR-009)

**Scale/Scope**: ~170 currencies per run (Fixer.io's full supported set), one call/day plus
occasional manual triggers — negligible load

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| Principle | Applies? | How this feature satisfies it |
|---|---|---|
| I. Monetary Precision | Yes | Cross-rate computation and persistence use `BigDecimal` throughout; no `double`/`float` touches a rate value |
| II. Accurate Rate Provenance | Yes | `rate_date` persisted from the provider's `response.date`, never `LocalDate.now()` (FR-002) |
| III. Idempotent Data Collection | Yes | Native `INSERT ... ON CONFLICT (currency_code, rate_date) DO UPDATE` upsert (research.md §5) |
| IV. Multi-Instance Scheduler Safety | Yes | `@SchedulerLock` via already-provisioned ShedLock JDBC table; same lock name shared with the manual-refresh path (research.md §4) |
| V. Concurrency-Safe Usage Counters | N/A | This feature never touches `currency_usage` (FR-009) |
| VI. Layered Separation of Concerns | Yes | `FixerClient` (integration) → `RateCollectionService` (orchestration, cross-rate calc, upsert) → thin `ExchangeController` refresh endpoint |
| VII. Data-Driven Configuration | N/A | Spread table is a separate, later feature |
| VIII. Grounded AI Output | N/A | Not touched by this feature |
| IX. Environment-Configurable Frontend | N/A | Backend-only feature |

No violations. Nothing to record in Complexity Tracking.

## Project Structure

### Documentation (this feature)

```text
specs/003-fixer-data-collection/
├── plan.md              # This file (/speckit-plan command output)
├── research.md          # Phase 0 output (/speckit-plan command)
├── data-model.md        # Phase 1 output (/speckit-plan command)
├── quickstart.md        # Phase 1 output (/speckit-plan command)
├── contracts/           # N/A — the project's single contract lives at /contracts/openapi.yaml (repo root, shared across features); this feature's addition (POST /exchange/refresh) was made directly there
└── tasks.md             # Phase 2 output (/speckit-tasks command - NOT created by /speckit-plan)
```

### Source Code (repository root)

```text
backend/
├── src/main/java/com/exchangerate/manager/
│   ├── client/
│   │   ├── FixerClient.java                  # RestClient wrapper calling /latest
│   │   ├── FixerLatestResponse.java           # deserialization DTO
│   │   └── FixerApiException.java             # wraps network/error-envelope failures
│   ├── config/
│   │   └── SchedulerLockConfig.java           # LockProvider bean + @EnableSchedulerLock
│   ├── service/
│   │   ├── RateCollectionService.java         # cross-rate calc, upsert orchestration, @SchedulerLock
│   │   └── RefreshResult.java                 # transient result DTO (data-model.md)
│   ├── scheduler/
│   │   └── RateCollectionScheduler.java       # @Scheduled cron "0 5 0 * * *" GMT, delegates to service
│   ├── controller/
│   │   └── ExchangeController.java            # implements generated refreshExchangeRates interface
│   └── repository/
│       └── ExchangeRateRepository.java        # + upsert(...) native @Modifying @Query (existing file)
└── src/test/java/com/exchangerate/manager/
    ├── client/FixerClientTest.java             # MockRestServiceServer
    ├── service/RateCollectionServiceTest.java  # cross-rate math, missing-currency handling
    └── repository/ExchangeRateRepositoryTest.java  # + upsert test cases (existing file)

contracts/openapi.yaml   # + POST /exchange/refresh (already added)
frontend/   # untouched directly; typed client regenerates from the contract when the frontend needs it
```

**Structure Decision**: Extends the existing `backend/` web-application layout from spec 002 with
`client/`, `scheduler/`, and `config/` packages alongside the existing `entity/`, `repository/`,
`controller/`, `service/` packages — no new top-level structure, no changes to `frontend/`.
