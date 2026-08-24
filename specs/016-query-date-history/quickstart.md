# Quickstart & Validation: Query Timestamp History

**Feature**: `016-query-date-history` | **Date**: 2026-08-24 |
**Spec**: [spec.md](spec.md) | **Plan**: [plan.md](plan.md) |
**Data model**: [data-model.md](data-model.md) |
**Contract delta**: [contracts/usage-analytics-history.yaml](contracts/usage-analytics-history.yaml)

Runnable scenarios that prove the feature works end to end. Field shapes live in the contract
delta and table shapes in the data model — this file does not repeat them.

## Prerequisites

- Java 21, Maven wrapper (`backend/mvnw`), Node 22 LTS, Docker (Testcontainers + local Postgres).
- `FIXER_API_KEY` set for `spring-boot:run` only. None of the scenarios below call Fixer.io; the
  test suite supplies a dummy key via `AbstractIntegrationTest`.

```bash
docker compose up -d                 # PostgreSQL 17
cd backend && ./mvnw spring-boot:run # applies Flyway V4 on startup
```

## Step 0 — regenerate the contract (do this first)

The contract is the source of truth for both sides; every scenario below assumes it has already
been updated and regenerated. Nothing else in the feature compiles until this is done.

```bash
# 1. Apply the delta in contracts/usage-analytics-history.yaml to contracts/openapi.yaml
# 2. Backend: openapi-generator-maven-plugin runs at generate-sources
cd backend && ./mvnw verify
# 3. Frontend: regenerates the committed client (32 tracked files under src/app/api-client/)
cd ../frontend && npm run generate:api
```

**Expected**: `CurrencyUsageEntry` gains a non-null `List<OffsetDateTime> queryTimestamps` on the
backend and a required `Array<string>` on the frontend client. `git status` shows a diff under
`frontend/src/app/api-client/` — commit it; never hand-edit it.

**Fails if**: the field was declared `nullable: true` (you'd get `JsonNullable<List<...>>` and an
optional client property, contradicting FR-010), or `queryTimestamps` was omitted from the
schema's `required` array.

---

## Scenario 1 — every query moment is recorded, for both currencies (User Story 1)

Covers FR-001, FR-002, SC-001, SC-002; Acceptance Scenarios 1–3.

```bash
curl -s "http://localhost:8080/api/v1/exchange?from=USD&to=EUR"
curl -s "http://localhost:8080/api/v1/exchange?from=USD&to=EUR"
curl -s "http://localhost:8080/api/v1/exchange?from=USD&to=GBP"
curl -s "http://localhost:8080/api/v1/exchange/usage" | jq '.currencies[]
  | select(.currencyCode|IN("USD","EUR","GBP"))
  | {currencyCode, queryCount, n: (.queryTimestamps|length)}'
```

**Expected**: `USD` shows 3 timestamps, `EUR` 2, `GBP` 1 — each currency's `queryTimestamps`
length equal to its `queryCount`, and the two same-day `USD`/`EUR` queries listed separately
rather than collapsed into one entry per day.

**Fails if**: only the `from` currency accumulated timestamps (the insert must cover both), or
same-day events were de-duplicated (a unique constraint slipped into the migration).

## Scenario 2 — history agrees with the existing fields and is stably ordered

Covers FR-009, Acceptance Scenarios 5–6.

```bash
curl -s "http://localhost:8080/api/v1/exchange/usage" \
  | jq '.currencies[] | select(.currencyCode=="USD")
        | {sorted: (.queryTimestamps == (.queryTimestamps|sort)),
           newestMatchesLastQueried: (.queryTimestamps[-1] == .lastQueriedAt)}'
# run twice and diff the raw responses
```

**Expected**: both booleans `true`, and two consecutive responses byte-identical.

**Fails if**: `newestMatchesLastQueried` is false — the event insert used a Java-side `Instant`
instead of the transaction's `now()` (R-002). If ordering flickers between runs, the `id`
tie-break is missing from the `ORDER BY` (R-003).

## Scenario 3 — the recency window trims history, not the counters (User Story 2)

Covers FR-011 through FR-015, SC-008, SC-010.

```bash
# Seed events spanning ~200 days for one currency, then:
curl -s "http://localhost:8080/api/v1/exchange/usage?recentDays=30" | jq '.currencies[0]'
curl -s "http://localhost:8080/api/v1/exchange/usage?recentDays=180" | jq '.currencies[0]'
curl -s "http://localhost:8080/api/v1/exchange/usage" | jq '.currencies[0]'
```

**Expected**: the 30-day call returns only timestamps inside 30 days; the 180-day call returns
strictly more (the explicit value is honoured in full even though it exceeds the 90-day default);
the no-parameter call trims to 90 days while still listing every currency, including
never-queried ones with `queryTimestamps: []`. `queryCount` is the same lifetime total in all
three responses.

**Fails if**: the 180-day call returns only 90 days of history — the default is narrowing an
explicit request, which FR-014 forbids (there must be no `min()` in the window resolution).

## Scenario 4 — nothing that isn't a query records anything

Covers FR-006, FR-007, SC-007; the invalid-query and admin edge cases.

```bash
curl -s "http://localhost:8080/api/v1/exchange?from=USD&to=USD"       # 400, same currency
curl -s "http://localhost:8080/api/v1/exchange?from=USD&to=XXX"       # 400, unknown currency
curl -s -X POST "http://localhost:8080/api/v1/exchange/refresh"       # admin refresh
curl -s "http://localhost:8080/api/v1/exchange/usage" | jq '[.currencies[].queryTimestamps|length]|add'
```

**Expected**: the total timestamp count is identical before and after all four calls, and no
`queryCount` moved.

## Scenario 5 — backward compatibility (User Story 3)

Covers FR-016, FR-017, SC-006.

```bash
cd backend && ./mvnw verify   # existing ExchangeControllerIT / CurrencyUsageRepositoryTest
```

**Expected**: every pre-existing analytics test passes **unmodified**. Ranking by `queryCount`
descending, the `currencyCode` ascending tie-break, and `limit` behave exactly as before, because
the selection query is untouched (R-003).

**Fails if**: an existing test needed editing to pass — that means currency selection changed,
which FR-015/FR-017 forbid.

## Scenario 6 — rollout seeding (verify against a populated database)

Covers FR-019, FR-020, FR-021, SC-013.

```sql
-- before: snapshot counts
SELECT currency_code, query_count, last_queried_at FROM currency_usage ORDER BY currency_code;
-- after V4 applies:
SELECT cu.currency_code, cu.query_count, COUNT(e.id) AS seeded
FROM currency_usage cu LEFT JOIN currency_query_event e USING (currency_code)
GROUP BY 1, 2 ORDER BY 1;
```

**Expected**: exactly one seeded event per pre-existing usage row, equal to that row's
`last_queried_at`; every `query_count` unchanged. A currency with a count of 40 and one seeded
entry is correct, not a defect (spec Assumptions).

**Idempotency**: re-running Flyway is a no-op — the seed shares its migration with `CREATE
TABLE`, so `flyway_schema_history` prevents a second application (R-007). Verify by restarting
the app and re-running the query above: `seeded` must not increase.

## Scenario 7 — retention purge

Covers FR-022, FR-023, FR-024, SC-011, SC-012.

Insert events older than 365 days, snapshot `currency_usage`, trigger the purge (invoke the
service method directly in an integration test rather than waiting for the cron), then:

**Expected**: zero rows with `queried_at < now() - INTERVAL '365 days'` remain; every
`query_count` and `last_queried_at` is byte-identical to the snapshot; rate lookups issued
concurrently with the purge all succeed and their new events survive.

**Fails if**: any counter moved — the purge must touch only `currency_query_event` (R-006).

## Scenario 8 — concurrency

Covers FR-003, SC-003. Extends the existing `ExchangeRateServiceConcurrencyIT`.

Fire 1,000 concurrent lookups involving one currency.

**Expected**: that currency ends with exactly 1,000 new events and a `query_count` exactly 1,000
higher — no losses, no de-duplication, no deadlocks.

## Scenario 9 — latency at reference volume

Covers SC-005. Not a CI assertion (wall-clock tests are flaky); run it deliberately.

Seed ~100,000 events spread across the full currency set, then measure p95 over repeated
requests to `/exchange/usage` with no parameters (the default 90-day window).

```bash
for i in $(seq 1 100); do
  curl -s -o /dev/null -w '%{time_total}\n' "http://localhost:8080/api/v1/exchange/usage"
done | sort -n | awk 'NR==95'
```

**Expected**: under 1 second.

**Fails if**: well over budget — check that `idx_currency_query_event_code_queried_at` exists and
that `EXPLAIN ANALYZE` shows an index range scan rather than a sequential scan plus a sort node.

---

## Test suites this feature adds or extends

| Suite | Type | Covers |
|---|---|---|
| `CurrencyQueryEventRepositoryTest` | Testcontainers | Window query bounds, ordering with duplicate timestamps, empty-code-list guard, batched purge |
| `CurrencyUsageRepositoryTest` | Testcontainers (extend) | Unchanged selection behaviour — existing cases must pass untouched |
| `UsageAnalyticsServiceTest` | Testcontainers | 90-day default, explicit wider window honoured, empty list for never-queried currencies, count/history divergence (R-008) |
| `ExchangeRateServiceTest` | unit (extend) | An event is recorded for both currencies; none on any failure path |
| `ExchangeRateServiceConcurrencyIT` | Testcontainers (extend) | SC-003 |
| `ExchangeControllerIT` | Testcontainers (extend) | Response shape, `[]` not null, `recentDays` end to end |
| `QueryEventPurgeServiceTest` | Testcontainers | Retention boundary, counters untouched, concurrent-insert safety |

All DB-touching tests use the existing `AbstractIntegrationTest` singleton Postgres container,
per Constitution Principle X.
