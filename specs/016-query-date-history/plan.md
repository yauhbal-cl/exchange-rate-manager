# Implementation Plan: Query Timestamp History

**Branch**: `016-query-date-history` | **Date**: 2026-08-24 | **Spec**: [spec.md](spec.md)

**Input**: Feature specification from `/specs/016-query-date-history/spec.md`

**Note**: This template is filled in by the `/speckit-plan` command; its definition describes the execution workflow.

## Summary

Record the moment of every successful exchange-rate query for **both** participating currencies,
and serve that history in the existing usage-analytics response. A new append-only
`currency_query_event` table is written inside `ExchangeRateService.lookup`'s existing
transaction — using the same `now()` the counter upsert already uses, so counts and history
cannot drift. `GET /exchange/usage` gains a `queryTimestamps` array per currency, bounded by the
existing `recentDays` window (90-day default when omitted) with no count cap. History is retained
365 days and removed by a daily ShedLock-guarded batched purge; pre-existing usage rows are
seeded with exactly one event inside the create-table migration. Currency selection, ranking and
every existing response field are untouched — the change is purely additive on the wire.

## Technical Context

**Language/Version**: Java 21

**Primary Dependencies**: Spring Boot 4.1.1, Spring Data JPA/Hibernate, Flyway, ShedLock
(JDBC lock provider, already wired), Lombok, MapStruct, openapi-generator-maven-plugin (backend)
and openapi-generator-cli (frontend), both fed from `contracts/openapi.yaml`

**Storage**: PostgreSQL 17 — one new table (`currency_query_event`) via migration
`V4__create_currency_query_event.sql`; `currency_usage` and `exchange_rates` structurally
unchanged

**Testing**: JUnit 5 + Spring Boot Test, Testcontainers (Postgres 17) via the existing
`AbstractIntegrationTest` singleton container, per Constitution Principle X

**Target Platform**: Linux server (containerized), multiple instances

**Performance Goals**: p95 under 1 s for `GET /exchange/usage` with the default 90-day window,
against ~100,000 retained events across the full currency set (SC-005). Query recording adds one
multi-row insert to the rate-lookup path.

**Constraints**: No cap, truncation, or sampling of returned history — the window is the only
limit (FR-013). Recording must not lose events under concurrency or touch the counter's atomicity
(FR-003). The purge must not alter any counter (FR-023) or block live queries (FR-024). Existing
analytics clients must need zero changes (SC-006).

**Scale/Scope**: 1 endpoint extended, 1 new table, 1 new migration, 1 new scheduled job, 1 new
service (which also removes an existing controller-to-repository layering violation). Two rows
written per successful rate query; retained volume reaches steady state at
`2 × queries/day × 365`.

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| Principle | Applies? | Compliance approach |
|---|---|---|
| I. Monetary Precision | N/A | No monetary or rate value is added, stored, or computed; the feature deals only in timestamps. |
| II. Accurate Rate Provenance | N/A | No rate is written. `queried_at` is deliberately the *event* moment, which is what the spec asks for — it is not a rate date. |
| III. Idempotent Data Collection | Yes (as a bounded exception) | The upsert rule governs ingested rate data. An event log is the intended inverse: FR-002 and the spec's identical-timestamp Edge Case require duplicates to persist, so `currency_query_event` carries no unique constraint. Idempotency is preserved where the principle actually bites — the one-time seed (FR-021) is exactly-once by living inside its `CREATE TABLE` migration (R-007). Recorded in Complexity Tracking. |
| IV. Multi-Instance Scheduler Safety | Yes | The retention purge reuses the wired ShedLock setup, with `@SchedulerLock` on the service method exactly as `RateCollectionService.collect()` already does — no new coordination mechanism (R-006). |
| V. Concurrency-Safe Usage Counters | Yes | The counter path is untouched: `incrementUsage` stays the single atomic upsert. Recording is a plain append with no read-modify-write anywhere, which is also why the history was *not* modelled as an array column on `currency_usage` (R-001). |
| VI. Layered Separation of Concerns | Yes — and improved | New `UsageAnalyticsService` owns window resolution and result assembly; `ExchangeController` stops injecting `CurrencyUsageRepository` directly, correcting a pre-existing violation that this feature's logic would otherwise deepen (R-004). |
| VII. Data-Driven Configuration Over Conditionals | N/A | No new lookup-keyed business rule. The 90-day default and 365-day retention are single global constants, not per-key variation, and the existing `SpreadLookup` is untouched. |
| VIII. Grounded AI Output, Honest Degradation | N/A | No AI path involved. |
| IX. Environment-Configurable Frontend | Yes (unaffected) | Backend-only feature; the frontend client is regenerated from the contract, and `apiBaseUrl` configuration is untouched. |
| X. Test Isolation via Testcontainers | Yes | Every new DB-touching test extends `AbstractIntegrationTest`; no test targets a shared or real database. |

Additional standards checks:

- **Centralized exception handling**: no new exception type and no new failure mode — recording
  is inside an existing transaction whose failures already route through
  `GlobalExceptionHandler`. No try/catch is added to any controller or service.
- **Contract-first workflow**: `contracts/openapi.yaml` is edited first and both sides are
  regenerated; no generated code is hand-edited ([[CLAUDE.md]] Monorepo Layout).
- **MapStruct for mapping**: the `CurrencyUsageSummary → CurrencyUsageEntry` mapping stays in
  `UsageAnalyticsMapper`; the service does aggregation (grouping), not field mapping.

Gate result: **pass**. One principle interaction requires justification (III) — recorded in
Complexity Tracking rather than waved through.

## Project Structure

### Documentation (this feature)

```text
specs/016-query-date-history/
├── plan.md              # This file (/speckit-plan command output)
├── research.md          # Phase 0 output (/speckit-plan command)
├── data-model.md        # Phase 1 output (/speckit-plan command)
├── quickstart.md        # Phase 1 output (/speckit-plan command)
├── contracts/           # Phase 1 output (/speckit-plan command)
│   └── usage-analytics-history.yaml
├── checklists/
│   └── requirements.md
└── tasks.md             # Phase 2 output (/speckit-tasks command - NOT created by /speckit-plan)
```

### Source Code (repository root)

```text
contracts/
└── openapi.yaml                                 # FIRST: add CurrencyUsageEntry.queryTimestamps,
                                                 # document recentDays' dual role + 90-day default

backend/
├── src/main/java/com/exchangerate/manager/
│   ├── controller/
│   │   └── ExchangeController.java              # getUsageAnalytics delegates to the new service;
│   │                                            # drops the direct CurrencyUsageRepository injection
│   ├── service/
│   │   ├── ExchangeRateService.java             # lookup(): record both currencies' events in the
│   │   │                                        # existing transaction, after the counter updates
│   │   ├── UsageAnalyticsService.java           # NEW — window default, two-query assembly, grouping
│   │   ├── CurrencyUsageSummary.java            # NEW — record: code, count, lastQueriedAt, timestamps
│   │   └── QueryEventPurgeService.java          # NEW — @SchedulerLock batched retention delete
│   ├── scheduler/
│   │   └── QueryEventPurgeScheduler.java        # NEW — thin daily @Scheduled delegate
│   ├── repository/
│   │   ├── CurrencyUsageRepository.java         # UNCHANGED (selection query must stay byte-identical)
│   │   └── CurrencyQueryEventRepository.java    # NEW — insert, window query, batched purge
│   ├── entity/
│   │   └── CurrencyQueryEvent.java              # NEW
│   └── mapper/
│       └── UsageAnalyticsMapper.java            # maps CurrencyUsageSummary -> CurrencyUsageEntry
├── src/main/resources/db/migration/
│   └── V4__create_currency_query_event.sql      # NEW — create + index + one-time seed
└── src/test/java/com/exchangerate/manager/
    ├── repository/CurrencyQueryEventRepositoryTest.java   # NEW
    ├── service/UsageAnalyticsServiceTest.java             # NEW
    ├── service/QueryEventPurgeServiceTest.java            # NEW
    ├── service/ExchangeRateServiceTest.java               # extend: recording on success/failure paths
    ├── service/ExchangeRateServiceConcurrencyIT.java      # extend: SC-003
    └── controller/ExchangeControllerIT.java               # extend: response shape, windowing

frontend/
└── src/app/api-client/                          # regenerated via `npm run generate:api`
                                                 # (committed — 32 tracked files; never hand-edited)
```

**Structure Decision**: Extends the existing single-module Spring Boot backend in place — no new
module, no new package root. The one structural addition is `UsageAnalyticsService`, which exists
because this feature introduces genuine business logic (window resolution, two-query assembly)
where the endpoint previously had none; putting it in the controller would violate Principle VI,
and putting it in `ExchangeRateService` would merge two domains that share nothing but a
controller. `CurrencyUsageRepository` is deliberately left untouched so the existing
selection/ranking tests keep covering FR-015 and FR-017 verbatim. Per the spec's Assumptions, no
frontend UI work is in scope — the frontend appears here only because its generated API client is
committed and must be regenerated from the changed contract (FR-018).

## Contract & Regeneration (explicit, per user input)

Contract work is the **first** implementation step, not a follow-up — nothing else compiles until
the generated types exist.

1. Apply [contracts/usage-analytics-history.yaml](contracts/usage-analytics-history.yaml) to
   `contracts/openapi.yaml`: add `queryTimestamps` to `CurrencyUsageEntry` (in `required` and in
   `properties`, `array` of `string`/`date-time`, **not** `nullable`), and update the `recentDays`
   parameter description and operation summary.
2. `cd backend && ./mvnw verify` — `openapi-generator-maven-plugin` regenerates the server
   interfaces and DTOs at `generate-sources`.
3. `cd frontend && npm run generate:api` — regenerates the committed
   `frontend/src/app/api-client/`; review and commit that diff.

Declaring the field `required` and non-nullable is deliberate: it is what makes the generated
types `List<OffsetDateTime>` / `Array<string>` rather than the `JsonNullable`/optional shapes
`lastQueriedAt` uses, which is how FR-010's "empty array, never null, never absent" is enforced by
the contract itself rather than by convention (R-005).

## Phase 0 — Research

Complete. See [research.md](research.md). The spec entered planning with zero open
`NEEDS CLARIFICATION` markers (six clarification rounds already recorded in the spec), so Phase 0
covers the nine technical decisions the design rests on — storage shape, transactional recording,
the two-query read, the service-layer addition, contract shape, purge mechanism, seeding,
the intended count-vs-history divergence, and the latency approach — each with rationale and
rejected alternatives.

## Phase 1 — Design & Contracts

Complete. [data-model.md](data-model.md) (new entity, unchanged entities, migration, derived
types, validation rules, volume), [contracts/usage-analytics-history.yaml](contracts/usage-analytics-history.yaml)
(the exact `openapi.yaml` delta), and [quickstart.md](quickstart.md) (nine runnable validation
scenarios mapped to requirements, plus the test-suite matrix).

**Post-design Constitution re-check**: unchanged from the pre-Phase-0 result — **pass**, with the
single Principle III interaction justified below. The design added no new endpoint, no new
exception type, no new external call, no new configuration surface, and no new coordination
mechanism; it removed one pre-existing layering violation.

## Complexity Tracking

| Violation | Why Needed | Simpler Alternative Rejected Because |
|-----------|------------|-------------------------------------|
| `currency_query_event` has no unique constraint, unlike every other table in the schema (tension with Principle III, Idempotent Data Collection) | FR-002 requires one row per participating currency per query with no de-duplication, and the spec's Edge Cases explicitly require two events sharing an identical timestamp to both persist. Any unique constraint covering `(currency_code, queried_at)` would reject or collapse the second row and break the feature's core promise (SC-001, SC-003). | A unique constraint on `(currency_code, queried_at)` was rejected because it silently loses events under concurrency — exactly the failure SC-003 tests for. Principle III governs *ingested rate data*, where duplicates mean a repeated fetch; here duplicates mean genuinely distinct user activity. Idempotency is still enforced where it applies: the one-time seed is exactly-once via Flyway's schema history (R-007), and the purge is naturally idempotent (deleting already-deleted rows is a no-op). |
| A second query per analytics request instead of extending the existing single query | The selection query must stay byte-identical to guarantee FR-015/FR-017 (currency selection, ranking and tie-breaks unchanged) and to keep the existing test suite valid as backward-compatibility evidence for User Story 3. | Folding an `array_agg` LATERAL join into the existing query was rejected: it mutates the one query whose behaviour must be provably unchanged, and returns a PostgreSQL array needing `Timestamp[]` projection plumbing. Both statements share one read-only transaction, so the extra round trip costs a round trip, not correctness — and the shared transaction timestamp is what makes the selection window and the trimming window provably identical (R-003). |
