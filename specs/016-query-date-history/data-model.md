# Phase 1 Data Model: Query Timestamp History

**Feature**: `016-query-date-history` | **Date**: 2026-08-24 |
**Spec**: [spec.md](spec.md) | **Plan**: [plan.md](plan.md) |
**Research**: [research.md](research.md)

One new table, one new JPA entity, one new migration. `currency_usage` and `exchange_rates` are
structurally unchanged — `currency_usage` gains a conceptual one-to-many relationship to the new
event log, but no column and no constraint of its own is altered (FR-019).

---

## Entity: `CurrencyQueryEvent` (new)

Maps the spec's **Currency Query Event** entity: "this currency was queried at this moment".
One row per participating currency per successful query. Carries no requester identity — the
spec classifies this as operational analytics data, not personal data.

**Table**: `currency_query_event`

| Column | Type | Constraints | Notes |
|---|---|---|---|
| `id` | `BIGSERIAL` | `PRIMARY KEY` | Monotonic; doubles as the deterministic tie-break for events sharing a timestamp (FR-009) |
| `currency_code` | `CHAR(3)` | `NOT NULL`, `CHECK (currency_code ~ '^[A-Z]{3}$')` | Same shape and check as `currency_usage.currency_code` and `exchange_rates.currency_code`; no foreign key (R-001) |
| `queried_at` | `TIMESTAMPTZ` | `NOT NULL` | The moment of the query. Written as `now()` (= transaction timestamp) so it is identical to the `last_queried_at` the same transaction writes (R-002) |

**Indexes**:

| Index | Definition | Serves |
|---|---|---|
| `idx_currency_query_event_code_queried_at` | `(currency_code, queried_at)` | Per-currency window range scan for the analytics read (R-003, SC-005) and the retention purge's expiry predicate (R-006) |

**Deliberately absent**:

- **No unique constraint of any kind.** Two events for the same currency at the same instant are
  valid and must both persist (FR-002, and the spec Edge Case on identical timestamps). This is
  the intended inverse of the `exchange_rates` upsert rule — Constitution Principle III governs
  ingested rate data, not an event log.
- **No foreign key to `currency_usage`.** Orphans are unreachable (every insert shares a
  transaction with the usage upsert), so the FK would add a hot-row lock for no integrity gain
  (R-001).
- **No soft-delete / seeded marker column.** Seeded rows are ordinary rows, so the purge treats
  them identically with no special case (R-007).

**JPA entity**: `com.exchangerate.manager.entity.CurrencyQueryEvent`, following the existing
`CurrencyUsage`/`ExchangeRate` conventions — Lombok `@Getter`/`@Setter`/`@NoArgsConstructor`/
`@AllArgsConstructor`, `@Id` with `GenerationType.IDENTITY`, `@JdbcTypeCode(SqlTypes.CHAR)` on
the code column, and Jakarta validation (`@NotNull`, `@Pattern(regexp = "^[A-Z]{3}$")`) mirroring
the DB constraints. The entity exists so `CurrencyQueryEventRepository` has a domain type and so
`ddl-auto: validate` checks the mapping; writes and reads both go through native queries, not
entity persistence.

**Lifecycle / state transitions**:

```text
(successful rate lookup)        (daily purge, queried_at < now() - 365 days)
          │                                        │
          ▼                                        ▼
      [created] ───────── retained, immutable ──────► [deleted]
```

Rows are never updated. There is no other transition — no state column, no revision.

---

## Entity: `CurrencyUsage` (unchanged)

Existing table and entity, listed for completeness because the feature adds a conceptual
relationship and depends on invariants it already holds.

| Column | Type | Feature impact |
|---|---|---|
| `id` | `BIGSERIAL PK` | none |
| `currency_code` | `CHAR(3) NOT NULL UNIQUE` | none — the join key for the history lookup |
| `query_count` | `BIGINT NOT NULL DEFAULT 0 CHECK (>= 0)` | **never** written by this feature; the purge must not change it (FR-023) and seeding must not change it (FR-020) |
| `last_queried_at` | `TIMESTAMPTZ NOT NULL DEFAULT now()` | read once, by the seeding migration (R-007); still returned in the response unchanged (FR-016) |

**Relationship**: `CurrencyUsage 1 ── 0..* CurrencyQueryEvent`, by `currency_code` value, *not*
by a mapped JPA association and not by a foreign key. Modelled as a value join because the
analytics read fetches events for an already-selected set of codes (R-003), and a mapped
`@OneToMany` would invite lazy-loading an unbounded collection on the request path.

**Invariant this feature relies on**: `currency_usage.last_queried_at` is written by the same
`now()` as the event insert in the same transaction, so for any currency whose newest event is
still retained, `MAX(queried_at) = last_queried_at` exactly (User Story 1 Acceptance Scenario 6).

---

## Migration: `V4__create_currency_query_event.sql` (new)

A single versioned migration performing three steps in order. Keeping the seed in the same
migration as the `CREATE TABLE` is what makes FR-021 structural rather than guarded (R-007).

1. **Create** `currency_query_event` with the columns and check constraint above.
2. **Index** `(currency_code, queried_at)`.
3. **Seed** exactly one event per pre-existing usage record:
   `INSERT INTO currency_query_event (currency_code, queried_at) SELECT currency_code,
   last_queried_at FROM currency_usage;`

Applies cleanly to an empty database (the seed selects zero rows) and to a populated one. No
`ALTER` against existing tables, so it cannot affect existing counts or rate data.

---

## Derived / transport types

These carry the data between layers; none is persisted.

| Type | Location | Shape | Purpose |
|---|---|---|---|
| `CurrencyUsageProjection` | `CurrencyUsageRepository` (existing) | `currencyCode`, `queryCount`, `lastQueriedAt` | Unchanged — the selection query is not modified (FR-015, FR-017) |
| `CurrencyQueryEventProjection` | `CurrencyQueryEventRepository` (new) | `currencyCode: String`, `queriedAt: Instant` | Flat rows from the window query, pre-sorted by `(currency_code, queried_at, id)` |
| `CurrencyUsageSummary` | `service` package (new record) | `currencyCode: String`, `queryCount: long`, `lastQueriedAt: Instant`, `queryTimestamps: List<Instant>` | Assembled by `UsageAnalyticsService`; the mapper's single input, matching the existing `RateTrendPoint`/`ExchangeRateLookupResult` pattern |
| `CurrencyUsageEntry` | generated from `contracts/openapi.yaml` | adds `queryTimestamps: List<OffsetDateTime>` | Wire shape — see [contracts/usage-analytics-history.yaml](contracts/usage-analytics-history.yaml) |

`queryTimestamps` is never null at any layer: the service seeds every selected currency with an
empty list before grouping, so a currency with no events in the window carries `[]` end to end
(FR-010).

---

## Validation rules

| Rule | Source | Enforced where |
|---|---|---|
| `currency_code` matches `^[A-Z]{3}$` | consistency with existing tables | DB `CHECK` + entity `@Pattern` |
| `queried_at` is not null | FR-001 | DB `NOT NULL` + entity `@NotNull` |
| Events are recorded only for successful lookups | FR-006, FR-007 | Placement inside `ExchangeRateService.lookup` after all validation and after the counter updates (R-002) — no runtime check needed |
| Duplicate `(currency_code, queried_at)` pairs are permitted | FR-002, Edge Cases | Absence of any unique constraint (R-001) |
| Returned history is chronological and stable | FR-009 | `ORDER BY currency_code, queried_at, id` in the window query (R-003) |
| Returned history never exceeds the applied window | FR-011, FR-012, SC-008 | `queried_at >= now() - windowDays` predicate, window resolved in `UsageAnalyticsService` (R-004) |
| Retained history never exceeds 365 days | FR-022, SC-011 | Daily batched purge (R-006) |

---

## Volume and growth

Two rows per successful rate query. Retained volume reaches steady state at
`2 × (queries per day) × 365` rows and stops growing (SC-012). The SC-005 reference dataset of
~100,000 retained events across the full currency set corresponds to roughly 137 queries/day
sustained over the full retention period. Each row is ~24 bytes of payload plus tuple overhead,
so the reference dataset is a few MB of heap plus its index — no partitioning needed at this
scale (R-006 alternatives).
