# Phase 0 Research: Query Timestamp History

**Feature**: `016-query-date-history` | **Date**: 2026-08-24 |
**Spec**: [spec.md](spec.md) | **Plan**: [plan.md](plan.md)

The spec arrived with all `NEEDS CLARIFICATION` markers resolved (six clarification rounds
recorded in the spec's Clarifications section), so this phase carries no open-question research.
What follows are the technical decisions the plan depends on, each recorded with its rationale
and the alternatives that were weighed and rejected.

---

## R-001: Storage shape for the query history

**Decision**: A new append-only table `currency_query_event (id BIGSERIAL PK, currency_code
CHAR(3) NOT NULL, queried_at TIMESTAMPTZ NOT NULL)`, with a supporting index on
`(currency_code, queried_at)`. No unique constraint of any kind, no foreign key to
`currency_usage`.

**Rationale**:

- FR-002 requires one row per participating currency per successful query, with no per-day
  de-duplication, and the Edge Cases explicitly require that two events sharing an identical
  timestamp are both retained. Any unique constraint touching `(currency_code, queried_at)`
  would violate that outright by rejecting or collapsing the second row, so the table must have
  none. This is the deliberate inverse of the `exchange_rates` upsert rule (Constitution
  Principle III), which applies to *ingested rate data*, not to an event log.
- `TIMESTAMPTZ` matches the existing `currency_usage.last_queried_at` and
  `exchange_rates.created_at` columns and gives FR-005's fixed reference offset (UTC) without
  depending on the host's local zone.
- The `(currency_code, queried_at)` composite index is exactly the access path the analytics
  read needs — range-scan a window per currency, already ordered — and is also the path the
  retention purge uses.
- The same `CHECK (currency_code ~ '^[A-Z]{3}$')` constraint used by the other two tables keeps
  the code column validated without a foreign key.

**Alternatives considered**:

- *A `DATE` column with a per-day unique constraint.* Rejected: this is the "de-duplicated
  calendar date" reading the spec's second clarification explicitly ruled out. It cannot answer
  the time-of-day and burst questions User Story 1 exists to answer.
- *A `TIMESTAMPTZ[]` array column on `currency_usage`.* Rejected: appending to an array is a
  read-modify-write of the usage row, which is precisely the concurrency hazard Constitution
  Principle V forbids for this table — FR-003 requires that concurrent queries never lose a
  record. It also makes the retention purge (FR-022) an array rewrite and defeats indexing.
- *A foreign key `currency_query_event.currency_code -> currency_usage.currency_code`.*
  Rejected: an orphan event is not reachable — every insert happens in the same transaction as
  the usage upsert that guarantees the parent row exists — so the FK buys no real integrity,
  while adding a `FOR KEY SHARE` row lock on the hot parent row to every insert and coupling the
  purge to the usage table. The `CHECK` constraint covers the actual validation need.

---

## R-002: Recording an event in the same unit of work as the counter

**Decision**: Record both currencies' events with a single native multi-row insert issued from
`ExchangeRateService.lookup` inside its existing `@Transactional` boundary, after the two
`incrementUsage` calls:

```sql
INSERT INTO currency_query_event (currency_code, queried_at)
VALUES (:firstCurrency, now()), (:secondCurrency, now())
```

**Rationale**:

- The spec's Assumptions require that counts and history cannot drift apart. `lookup` is already
  `@Transactional`, and every failure path (`SameCurrencyException`,
  `UnknownCurrencyException`, `RateDataNotFoundException`) is thrown *before* the counter
  updates, so placing the insert alongside them gives FR-007 (no event for a failed query) for
  free, with no new conditional logic.
- In PostgreSQL `now()` is `transaction_timestamp()` — constant for the whole transaction. The
  existing `incrementUsage` upsert already sets `last_queried_at = now()`. Using `now()` here
  too means each recorded event carries *byte-identical* the value written to
  `last_queried_at`, which is what makes User Story 1 Acceptance Scenario 6 (newest timestamp
  agrees with last-queried value) hold exactly rather than approximately.
- A single two-row insert is one round trip and takes no locks that conflict with anything: a
  plain insert into a table with no unique index cannot deadlock against a concurrent insert.
  The existing sorted-order discipline for `incrementUsage` (commit `fe15ef9`, which orders the
  two counter updates to avoid deadlocks) is therefore not needed for the event insert, though
  the insert stays after both counter updates so the counter ordering is untouched.
- FR-006 falls out of placement: `RateCollectionService.collect()` and the manual refresh path
  never enter `lookup`, so they record nothing, exactly as they already increment nothing.

**Alternatives considered**:

- *An asynchronous / event-listener write (`@TransactionalEventListener`, `@Async`).* Rejected:
  it breaks the spec's "same unit of work" assumption — a crash between commit and listener
  execution silently drifts the count away from the history, which FR-004 forbids.
- *Passing a Java-side `Instant.now()` as a bind parameter.* Rejected: it would differ from the
  `now()` the counter upsert writes by however long the two statements are apart, so the newest
  event and `last_queried_at` would disagree by a few milliseconds and Acceptance Scenario 6
  would only hold to within a tolerance.
- *Two single-row inserts.* Rejected: same semantics, one extra round trip on the request's hot
  path, no benefit.

---

## R-003: Serving the history — two queries, grouped in the service

**Decision**: Keep the existing ranked/filtered currency-selection query untouched, and add a
second repository query that fetches the events for exactly the selected currency codes inside
the applied window:

```sql
SELECT currency_code AS currencyCode, queried_at AS queriedAt
FROM currency_query_event
WHERE currency_code IN (:currencyCodes)
  AND queried_at >= now() - (:windowDays || ' days')::interval
ORDER BY currency_code ASC, queried_at ASC, id ASC
```

Both statements run inside one read-only transaction; the service groups the flat rows into a
`Map<String, List<Instant>>` keyed by currency code.

**Rationale**:

- FR-015 requires that trimming the history must not change *which* currencies appear. Leaving
  the selection query byte-for-byte unchanged is the strongest possible guarantee of that, and
  of FR-017 (ranking, limit and tie-break behaviour unchanged) — the currently passing
  `CurrencyUsageRepositoryTest` cases keep covering it verbatim.
- One shared transaction is load-bearing, not incidental: `now()` is the transaction timestamp,
  so the boundary used to *select* currencies and the boundary used to *trim* history are the
  same instant. That is what makes User Story 2 Acceptance Scenario 4 (a currency admitted by
  the window always has a non-empty list) hold — see R-008 for its one documented exception.
- `ORDER BY currency_code, queried_at, id` gives FR-009's chronological order *and* its
  stability requirement. `ORDER BY queried_at` alone is **not** deterministic here, because the
  spec explicitly permits two events with an identical timestamp; the monotonic `id` is the
  tie-break that makes repeated identical requests byte-identical.
- Grouping a flat result in Java is O(n) over rows the query already returns sorted, so the
  service does no sorting of its own.

**Alternatives considered**:

- *A single query using a `LATERAL` join with `array_agg(queried_at)`.* Rejected: it saves one
  round trip but returns a PostgreSQL array that has to be unpacked through a Hibernate
  projection into `java.sql.Timestamp[]`, adding type-plumbing to the one place in this feature
  where a bug is hardest to see. The second query is index-covered and cheap.
- *One query per selected currency (N+1).* Rejected: ~35 tracked currencies means ~35 round
  trips per analytics request, against a 1-second p95 budget (SC-005).
- *Filtering the window in Java after fetching all events.* Rejected: it transfers the entire
  365-day retained history over the wire to serve a 90-day window, and cannot use the index.

**Implementation note**: an empty `IN` list renders as invalid SQL (`IN ()`). When the selection
query returns no currencies, the service must skip the second query entirely and return an empty
response.

---

## R-004: Where the window default lives, and the layering fix it forces

**Decision**: Introduce `UsageAnalyticsService`, annotated `@Transactional(readOnly = true)`,
holding the `DEFAULT_HISTORY_WINDOW_DAYS = 90` constant and the effective-window resolution
(`recentDays != null ? recentDays : 90`). The controller depends on it instead of injecting
`CurrencyUsageRepository` directly.

**Rationale**:

- Constitution Principle VI requires controller → service → repository. `ExchangeController`
  currently injects `CurrencyUsageRepository` and calls `findCurrencyUsage` straight from
  `getUsageAnalytics` — pre-existing debt from when the endpoint was a pure passthrough with no
  logic to house. This feature adds real logic (window resolution, two-query assembly,
  grouping), so the service layer is now required rather than optional; adding it is a
  correction, not scope creep.
- The transaction annotation is functional, not decorative: R-003 depends on both statements
  observing the same `now()`.
- FR-012 and FR-014 reduce to one line in the service — an explicitly supplied `recentDays`
  always wins, including when it is wider than 90 days, because the default is only consulted
  when the parameter is null. There is no `min()`/`max()` anywhere, which is what guarantees the
  default can never silently narrow an explicit request.

**Alternatives considered**:

- *A configurable property (`exchange-rates.analytics.default-history-days`).* Rejected: the
  90-day default is written into the published API contract's parameter description, so changing
  it by configuration would make a deployment's behaviour disagree with its own documented
  contract. If it ever needs to vary, that is a contract change, not a config change.
- *Resolving the default in the controller.* Rejected: it is a business rule, which Principle VI
  places in the service layer.
- *Adding the methods to `ExchangeRateService`.* Rejected: that class owns rate lookup and
  spread computation and shares nothing with usage analytics; the two only coexist behind the
  same controller.

---

## R-005: Contract change and regeneration

**Decision**: Add a `queryTimestamps` array to `CurrencyUsageEntry` in
`contracts/openapi.yaml`, listed in the schema's `required` array, items typed
`string`/`date-time`; and extend the `recentDays` parameter description plus the operation
summary to document the dual role and the 90-day default. Then regenerate both sides:
`./mvnw verify` in `backend/` (the `openapi-generator-maven-plugin` runs at `generate-sources`)
and `npm run generate:api` in `frontend/`.

**Rationale**:

- FR-018 requires the contract *and every generated client* to reflect the field. The repo's
  stated workflow ([[CLAUDE.md]] Monorepo Layout) is contract-first: edit `openapi.yaml`, then
  regenerate — never hand-edit generated code on either side.
- `required` + non-nullable array is what produces FR-010's "empty collection, never null and
  never absent". A `nullable: true` field would generate `JsonNullable<List<...>>` on the server
  (the pattern `lastQueriedAt` already uses) and an optional property on the client, both of
  which invite exactly the null-vs-empty ambiguity FR-010 rules out.
- Adding a field to a response schema is additive for existing consumers (User Story 3): the
  existing three properties keep their names, types and meaning, and no existing client fails on
  an unknown extra field.
- `frontend/src/app/api-client/` is committed to the repository (32 tracked files), so the
  frontend regeneration produces a real reviewable diff and must be part of the change, even
  though this feature ships no frontend UI (spec Assumptions place the dashboard change out of
  scope).

**Alternatives considered**:

- *A separate endpoint for the history.* Rejected: the spec's whole framing is "serve it in the
  Analytics Endpoint as part of the response", and Story 1's Independent Test requires one
  request (SC-004).
- *Making the field optional to be "safer".* Rejected: it weakens FR-010 without protecting any
  real consumer, and pushes an unnecessary null check into every client.
- *Hand-editing the generated clients to avoid a regeneration step.* Rejected outright by
  [[CLAUDE.md]].

---

## R-006: Retention purge

**Decision**: A daily scheduled purge, following the exact shape the repo already uses for
ingestion — a thin `@Scheduled` component delegating to a service method carrying
`@SchedulerLock`. The delete runs as a bounded loop of batched statements, each batch in its own
transaction, until a batch affects zero rows:

```sql
DELETE FROM currency_query_event
WHERE ctid IN (
    SELECT ctid FROM currency_query_event
    WHERE queried_at < now() - INTERVAL '365 days'
    LIMIT 10000
)
```

**Rationale**:

- Constitution Principle IV and FR-024 require correct multi-instance behaviour. `ShedLock` is
  already wired (`SchedulerLockConfig`, `@EnableSchedulerLock`, the `shedlock` table from
  migration V3), and `RateCollectionService.collect()` already demonstrates `@SchedulerLock` on
  a *service* method rather than the `@Scheduled` method — reusing that mechanism is what the
  spec's Assumptions ask for ("reusing the platform's existing approach ... rather than
  introducing a new mechanism").
- Daily is sufficient by the spec's own Assumptions: retention is a boundary in days, not an
  exact-to-the-second cutoff.
- Batching is what delivers FR-024's "must not block queries from succeeding". A single
  unbounded `DELETE` on the first run after a long backlog would hold row locks and accumulate
  WAL for the whole run; 10,000-row batches in separate transactions keep each lock window
  short. Deletes never block concurrent inserts of *new* rows regardless, so newly recorded
  timestamps are unaffected (FR-024) — the batching protects throughput and replication lag, not
  correctness.
- FR-023 falls out of the statement's shape: it touches only `currency_query_event`. Nothing in
  the purge path reads or writes `currency_usage`, so counts, last-queried values, and which
  currencies analytics reports on are structurally untouchable by it.
- The schedule is offset well away from the 00:05 GMT ingestion run so the two never contend.

**Alternatives considered**:

- *Native PostgreSQL partitioning by month with `DROP PARTITION`.* Rejected: materially cheaper
  at very large scale, but the reference volume is ~100,000 rows (SC-005) and partitioning adds
  a partition-maintenance job, a more complex migration, and JPA mapping caveats for a table
  this size. Worth revisiting only if retained volume grows by orders of magnitude.
- *A single unbounded `DELETE`.* Rejected: see the FR-024 reasoning above.
- *`@Scheduled` + `@SchedulerLock` on the same scheduler method.* Workable and equally correct,
  but rejected for consistency — the repo's one existing scheduled job puts the lock on the
  service method, and matching it keeps the two jobs' structures comparable.
- *Deleting by `id` range instead of `ctid`.* Rejected: `ctid` needs no extra index and the
  subquery is already driven by the `(currency_code, queried_at)` index; an id-based batch
  would need its own scan.

---

## R-007: One-time seeding of pre-existing usage records

**Decision**: Seed inside the same Flyway versioned migration that creates the table:

```sql
INSERT INTO currency_query_event (currency_code, queried_at)
SELECT currency_code, last_queried_at FROM currency_usage;
```

**Rationale**:

- FR-020 requires exactly one seeded entry per pre-existing usage record, taken from its current
  last-queried value, inventing nothing further. A plain `SELECT` over `currency_usage` produces
  precisely that: one row in, one row out. `currency_usage.last_queried_at` is `NOT NULL`, and
  rows are only ever created by the `incrementUsage` upsert (which inserts with count 1), so
  there is no null case and no zero-count row to reason about.
- FR-021 (repeat application must not accumulate duplicates) is satisfied structurally rather
  than by a guard clause: Flyway's `flyway_schema_history` applies a versioned migration exactly
  once per database, and because the seed shares a migration with `CREATE TABLE`, a replayed
  deployment fails on the table already existing rather than double-seeding. There is no
  reachable state in which the table exists but is un-seeded.
- The migration touches no counter column, so FR-019 and SC-013 hold by construction.
- Seeded entries are ordinary rows with no marker column, so the next purge treats one older
  than 365 days exactly like any other expired event — which is the Edge Case the spec calls out
  as wanting "no special case".

**Alternatives considered**:

- *A repeatable migration (`R__`) or an application-startup seeding bean.* Rejected: both re-run
  by design, so each would need its own explicit anti-duplication guard to meet FR-021 —
  strictly more machinery for a strictly worse guarantee.
- *Back-filling `query_count` synthetic events so history matches the count.* Rejected
  explicitly by FR-020 and the spec Assumptions: those moments were never recorded and inventing
  them would fabricate data.
- *Seeding nothing (history starts empty).* Rejected: the sixth clarification chose seeding.

---

## R-008: Known, intended divergence between count and history

**Decision**: Document — and assert in tests rather than "fix" — that a currency's lifetime
count can exceed its returned history in exactly two cases: activity trimmed by the applied
window, and activity purged past the 365-day retention boundary.

**Rationale**:

- FR-004 states the equality holds only "while those timestamps are still retained", and FR-023
  forbids the purge from reducing the count. So a currency last queried more than 365 days ago
  legitimately reports a positive count, a non-null last-queried value, and an empty timestamp
  list. The spec lists this as an Edge Case and SC-009 phrases the criterion as "any shortfall
  is attributable only to purged history or the applied window".
- This also bounds User Story 2 Acceptance Scenario 4 ("an admitted currency's list is never
  empty"): it holds for every `recentDays <= 365`, and is intentionally allowed to break for a
  wider window, which is the case FR-025 covers by requiring the request to still succeed.
  Recording it here prevents a future reader from "correcting" the behaviour into a contract
  violation.

**Alternatives considered**:

- *Rewriting the count down when history is purged.* Rejected: directly forbidden by FR-023 and
  it would destroy the lifetime-total meaning existing clients depend on (FR-016).
- *Retaining history forever so the equality always holds.* Rejected: SC-012 requires retained
  volume to stop growing once the platform outlives the retention period.

---

## R-009: Meeting the p95 latency target

**Decision**: Rely on the `(currency_code, queried_at)` index for the window scan and validate
SC-005 with a seeded-volume test at roughly 100,000 events across the full currency set. Impose
no count cap, no truncation, and no pagination.

**Rationale**:

- FR-013 forbids capping, truncating, or sampling, so the only levers on response size are the
  index and the window — the client's lever is the window itself, which the spec Assumptions
  accept as a deliberate tradeoff.
- The second query is a bounded index range scan per selected currency and returns rows already
  ordered, so no sort node and no full-table scan. At the reference volume the dominant cost is
  JSON serialization of the timestamps (~100,000 ISO-8601 values ≈ a few MB), not the query.
- SC-005 is a per-request latency expectation, not a throughput commitment (spec Assumptions),
  so the validation is a measured p95 over repeated single requests against a seeded dataset —
  described as a runnable scenario in [quickstart.md](quickstart.md), not asserted as a
  wall-clock unit test (which would be flaky in CI).

**Alternatives considered**:

- *A per-currency cap or pagination.* Rejected: forbidden by FR-013 and SC-010. The spec
  Assumptions name it as a possible deliberate follow-up if traffic ever makes the current
  choice untenable — but not something this feature does quietly.
- *A materialized view or pre-aggregated rollup.* Rejected: it would collapse individual
  timestamps, which is the one thing this feature exists to expose.

---

## Summary of decisions

| ID | Decision | Primary requirements served |
|---|---|---|
| R-001 | Append-only `currency_query_event` table, no unique constraint, no FK | FR-002, FR-003, FR-005 |
| R-002 | Single two-row native insert inside `lookup`'s existing transaction, using `now()` | FR-001, FR-004, FR-006, FR-007 |
| R-003 | Second window-scoped query + service-side grouping, one read-only transaction | FR-008, FR-009, FR-011, FR-015, FR-017 |
| R-004 | New `UsageAnalyticsService` owning the 90-day default; controller stops calling the repository | FR-012, FR-014, Principle VI |
| R-005 | `queryTimestamps` added to `CurrencyUsageEntry` as a required array; both clients regenerated | FR-010, FR-016, FR-018 |
| R-006 | Daily ShedLock-guarded batched purge at 365 days | FR-022, FR-023, FR-024, Principle IV |
| R-007 | One seeded event per usage row, inside the create-table migration | FR-019, FR-020, FR-021 |
| R-008 | Count-exceeds-history divergence documented and asserted, not "fixed" | FR-004, FR-023, FR-025 |
| R-009 | Index-backed window scan, no cap; p95 validated against a seeded dataset | FR-013, SC-005, SC-010 |

No `NEEDS CLARIFICATION` items remain.
