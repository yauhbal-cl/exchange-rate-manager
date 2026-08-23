# Implementation Plan: EUR Base Currency Spread Correction

**Branch**: `feat/008-eur-base-spread-correction` | **Date**: 2026-08-23 | **Spec**: [spec.md](./spec.md)

**Input**: Feature specification from `/specs/008-eur-base-spread-correction/spec.md`

## Summary

`SpreadLookup` currently hardcodes USD as the 0%-spread currency, but Fixer.io's base currency is
always EUR — every quote involving USD is silently mispriced. This plan externalizes the base
currency and spread table into a validated `application.yml`-bound `@ConfigurationProperties`
class, refactors `SpreadLookup` to read from it (EUR → 0%, Appendix B groups → their percentages,
everything else → the 2.75% default), and adds a startup-time-validated, ingestion-time base
currency check on the Fixer response so a provider payload that isn't EUR-based is rejected before
any rate is written. The existing USD-normalization arithmetic in `RateCollectionService` and the
database schema are untouched — USD stays the internal normalization anchor, but is no longer
conflated with "the spread-free currency."

## Technical Context

**Language/Version**: Java 21

**Primary Dependencies**: Spring Boot 4.1.1 (`spring-boot-starter-validation` for
`@ConfigurationProperties` + jakarta.validation, already on the classpath), Spring Data JPA,
Lombok — no new dependencies required

**Storage**: PostgreSQL 17 — unchanged; no schema/migration change (spreads and base currency are
in-memory config, not persisted rows, per explicit scope constraint)

**Testing**: JUnit 5 + Spring Boot Test; Testcontainers for any test that touches a real
`ApplicationContext`/DB per constitution Principle X — plain unit tests (no Spring context) for
`SpreadLookup` and the `@ConfigurationProperties` validation, matching existing test style

**Target Platform**: Linux server (existing Spring Boot backend deployment)

**Project Type**: Web application (backend-only change; no frontend or contract impact)

**Performance Goals**: N/A beyond existing — spread lookup is an O(1) map read, same cost profile
as the hardcoded map it replaces

**Constraints**: No new database table for spreads or base currency; no runtime/admin editing
capability; config must fail fast (at startup) on invalid values; existing API request/response
contracts, historical rate storage, and USD-normalization math must not change

**Scale/Scope**: One new immutable config class, one refactored service (`SpreadLookup`), one
ingestion-time validation added to `RateCollectionService`, ~10 explicitly configured currencies
plus a default, associated unit/integration tests, and documentation updates

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

- **I. Monetary Precision** — PASS. All spread values are typed `BigDecimal` end-to-end (YAML →
  `@ConfigurationProperties` → `SpreadLookup`), per explicit requirement 3.
- **II. Accurate Rate Provenance** — PASS (unaffected). `RateCollectionService` still persists
  `response.getDate()`, not fetch time.
- **III. Idempotent Data Collection** — PASS (unaffected). Upsert-on-`(currency, rateDate)` is
  untouched; the new base-currency check runs *before* any upsert, so it can only prevent writes,
  never duplicate them.
- **IV. Multi-Instance Scheduler Safety** — PASS (unaffected). `@SchedulerLock` on `collect()` is
  unchanged.
- **V. Concurrency-Safe Usage Counters** — PASS (unaffected). No change to `CurrencyUsageRepository`
  increment logic.
- **VI. Layered Separation of Concerns** — PASS. Base-currency validation and spread lookup stay in
  the service layer (`RateCollectionService`, `SpreadLookup`); the config class is a passive,
  validated data holder, not business logic; controllers are untouched.
- **VII. Data-Driven Configuration Over Conditionals** — PASS, and this feature is a direct
  correction toward this principle: the spread table moves from a hardcoded static map to an
  externalized, keyed `application.yml` structure. Adding a new spread group is now a config edit.
- **VIII. Grounded AI Output** — N/A. Feature does not touch the AI insight path.
- **IX. Environment-Configurable Frontend** — N/A. Backend-only change.
- **X. Test Isolation via Testcontainers** — PASS. New tests for `SpreadLookup`/config validation
  are plain unit tests needing no database; any test exercising `RateCollectionService` against a
  real persistence layer follows the existing Testcontainers-backed integration test pattern already
  used in this codebase.

No violations. Complexity Tracking table is not needed.

## Project Structure

### Documentation (this feature)

```text
specs/008-eur-base-spread-correction/
├── plan.md              # This file
├── research.md          # Phase 0 output
├── data-model.md         # Phase 1 output
├── quickstart.md        # Phase 1 output
└── tasks.md             # Phase 2 output (/speckit-tasks — not created here)
```

No `contracts/` directory: this feature changes internal calculation/validation logic only. It adds
no new endpoint, and no existing `contracts/openapi.yaml` request/response shape changes (FR-012).
The only "contract" surface it touches is the `exchange-rates.*` block of `application.yml`, which
`data-model.md` documents as the config schema.

### Source Code (repository root)

```text
backend/
├── src/main/java/com/exchangerate/manager/
│   ├── config/
│   │   └── ExchangeRateProperties.java      # NEW — @ConfigurationProperties("exchange-rates")
│   ├── client/
│   │   ├── FixerLatestResponse.java         # unchanged (already exposes `base`)
│   │   └── FixerApiException.java           # unchanged (reused for base-currency mismatch)
│   └── service/
│       ├── SpreadLookup.java                # REFACTORED — reads ExchangeRateProperties
│       └── RateCollectionService.java       # UPDATED — validates response.getBase() == configured base
├── src/main/resources/
│   └── application.yml                      # UPDATED — new `exchange-rates:` block
└── src/test/java/com/exchangerate/manager/
    ├── config/
    │   └── ExchangeRatePropertiesTest.java  # NEW — startup validation rules
    ├── service/
    │   ├── SpreadLookupTest.java            # NEW — Appendix B + EUR + default coverage
    │   └── RateCollectionServiceTest.java   # UPDATED — base-currency accept/reject cases
    └── controller/
        └── ExchangeControllerIT.java        # UPDATED if EUR/USD spread assumptions were baked in
```

**Structure Decision**: Existing single-backend-module web application layout (`backend/` +
`frontend/`, per `CLAUDE.md`). This feature is entirely within `backend/`; no frontend or contract
changes. Config lives under the existing `config` package alongside `AiConfig`/`JacksonConfig`,
following that package's existing convention for `@Configuration`-adjacent classes.

## Complexity Tracking

Not applicable — no constitution violations to justify.
