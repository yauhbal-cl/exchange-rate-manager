# Implementation Plan: Database Migration Tool, Schema, and Persistence Model

**Branch**: `002-db-schema-migration` | **Date**: 2026-08-22 | **Spec**: [spec.md](./spec.md)

**Input**: Feature specification from `/specs/002-db-schema-migration/spec.md`

## Summary

Stand up versioned, automatically-applied database migrations (Flyway, per user direction) that
create the exchange rate, currency usage, and ShedLock coordination tables with correctness
constraints baked in (composite uniqueness, NUMERIC(19,6) precision, positive-rate check,
currency-code format check), plus JPA entities/repositories so later features (ingestion, rate
API, analytics) can read/write through the persistence layer instead of hand-written SQL.

## Technical Context

**Language/Version**: Java 17 (per current `backend/pom.xml`; CLAUDE.md targets 21 — pre-existing
gap, out of scope for this feature)

**Primary Dependencies**: Spring Boot 4.1.1 (`spring-boot-starter-data-jpa`), Flyway
(`flyway-core` + `flyway-database-postgresql`), ShedLock (`shedlock-spring` +
`shedlock-provider-jdbc-template`), PostgreSQL JDBC driver (already present)

**Storage**: PostgreSQL 17 (via `docker-compose.yml`, already provisioned)

**Testing**: JUnit 5 + `spring-boot-starter-test` (already present); `@DataJpaTest` /
`@SpringBootTest` against a real Postgres (Testcontainers not yet in the project — use the
docker-compose Postgres instance for integration tests, consistent with current setup)

**Target Platform**: Linux server (containerized/backend service)

**Project Type**: Web application — backend (`backend/`) + frontend (`frontend/`); this feature is
backend-only

**Performance Goals**: N/A (schema/migration feature; no request-path performance target)

**Constraints**: Migrations must be forward-only, idempotent, and safe under concurrent instance
startup (FR-001–FR-003, FR-013); no floating-point rate columns (FR-006, Constitution I)

**Scale/Scope**: Two application tables (`exchange_rates`, `currency_usage`) plus ShedLock's own
lock table; single-database-vendor (PostgreSQL) target only

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| Principle | Applies? | How this feature satisfies it |
|---|---|---|
| I. Monetary Precision | Yes | `ExchangeRate.rateToUsd` mapped as `BigDecimal`, column `NUMERIC(19,6)` |
| II. Accurate Rate Provenance | Yes (schema support) | `rate_date` column exists for the provider-reported date; population is the ingestion feature's job, out of scope here |
| III. Idempotent Data Collection | Yes (schema support) | Unique constraint on `(currency_code, rate_date)` enables upsert; upsert logic itself is the ingestion feature's job |
| IV. Multi-Instance Scheduler Safety | Yes | ShedLock lock table created via the same Flyway migration chain |
| V. Concurrency-Safe Usage Counters | Yes (schema support) | Unique constraint on `currency_code` in `currency_usage` enables atomic `ON CONFLICT` upsert; the atomic increment itself is the rate-API feature's job |
| VI. Layered Separation of Concerns | Yes | Entities + Spring Data repositories only; no service/controller logic added here |
| VII. Data-Driven Configuration | N/A | Spread table is a separate, later feature |
| VIII. Grounded AI Output | N/A | Not touched by this feature |
| IX. Environment-Configurable Frontend | N/A | Backend-only feature |

No violations. Nothing to record in Complexity Tracking.

## Project Structure

### Documentation (this feature)

```text
specs/[###-feature]/
├── plan.md              # This file (/speckit-plan command output)
├── research.md          # Phase 0 output (/speckit-plan command)
├── data-model.md        # Phase 1 output (/speckit-plan command)
├── quickstart.md        # Phase 1 output (/speckit-plan command)
├── contracts/           # Phase 1 output (/speckit-plan command)
└── tasks.md             # Phase 2 output (/speckit-tasks command - NOT created by /speckit-plan)
```

### Source Code (repository root)

```text
backend/
├── src/main/java/com/exchangerate/manager/
│   ├── entity/
│   │   ├── ExchangeRate.java
│   │   └── CurrencyUsage.java
│   └── repository/
│       ├── ExchangeRateRepository.java
│       └── CurrencyUsageRepository.java
├── src/main/resources/
│   ├── db/migration/
│   │   ├── V1__create_exchange_rates.sql
│   │   ├── V2__create_currency_usage.sql
│   │   └── V3__create_shedlock.sql
│   └── application.yml (Flyway + datasource config, already partially present)
└── src/test/java/com/exchangerate/manager/
    └── repository/
        ├── ExchangeRateRepositoryTest.java
        └── CurrencyUsageRepositoryTest.java

frontend/   # untouched by this feature
```

**Structure Decision**: Web application layout already established (`backend/` + `frontend/`
siblings, per CLAUDE.md). This feature only adds to `backend/src/main/java/com/exchangerate/manager/{entity,repository}`
and `backend/src/main/resources/db/migration`, following the existing controller → service →
repository package layout (no controller/service code needed for this feature).

## Complexity Tracking

*No violations — table not needed.*
