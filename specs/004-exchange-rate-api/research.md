# Phase 0 Research: Exchange Rate API

## Spread reference storage: DB table vs. static keyed lookup

**Decision**: Model Appendix B as an in-code, immutable `Map<String, BigDecimal>` (keyed by
3-letter currency code, plus a `"default"` sentinel key), wrapped in a small `SpreadLookup`
component with one method `BigDecimal spreadFor(String currencyCode)`.

**Rationale**: Spec Assumptions state the spread table "is seeded as static reference data and is
not user-editable through this feature." Constitution Principle VII requires a keyed lookup
instead of conditionals, but does not require a database table — a `Map` literal satisfies "data
change, not a code change" for the only change that's actually expected (adjusting a tier value or
adding a currency requires touching one map entry, not a branch). Skipping a table also avoids an
unnecessary migration + repository + cache-invalidation surface for data that has no admin/write
path in this feature.

**Alternatives considered**:
- New `spread_tiers` DB table, seeded via migration. Rejected for this feature: adds a migration,
  entity, repository, and read-through-DB cost for a value that is read on every single lookup and
  never written outside a migration. Revisit only if a future feature needs runtime editability.
- Hard-coded currency-code `if/else` or `switch`. Rejected: directly violates Constitution
  Principle VII / FR-006.

## Base currency (0% spread) identification

**Decision**: The base currency is the platform's USD anchor (`rate_to_usd = 1` conceptually);
`SpreadLookup` keys `"USD"` (or whatever the collection feature treats as base — verified against
`RateCollectionService`, which computes `rateToUsd` for every currency including `USD` itself via
`eurToUsd`) to `BigDecimal.ZERO`. No change needed in `RateCollectionService`; this feature only
reads existing `rate_to_usd` values.

**Rationale**: Spec Acceptance Scenario 3 requires 0% spread on the base-currency side. Since
`ExchangeRate.rateToUsd` already stores every currency (including USD) relative to USD, the
formula `(toRateUsd / fromRateUsd) × ((100 − MAX(...)) / 100)` needs no special-casing beyond the
spread lookup itself returning 0 for `"USD"`.

**Alternatives considered**: A separate `isBaseCurrency` boolean/config. Rejected: redundant with
just giving `"USD"` a 0% entry in the same map used for every other tier — one lookup mechanism,
no branch.

## Usage counter atomic upsert

**Decision**: Add one native query to `CurrencyUsageRepository`:

```sql
INSERT INTO currency_usage (currency_code, query_count, last_queried_at)
VALUES (:currencyCode, 1, now())
ON CONFLICT (currency_code)
DO UPDATE SET query_count = currency_usage.query_count + 1, last_queried_at = now()
```

Called once per currency (source, target) inside the same `@Transactional` service method that
already validated the lookup succeeded, after the rate has been computed — never before.

**Rationale**: Matches the exact pattern mandated by the task description and Constitution
Principle V; `ON CONFLICT ... DO UPDATE SET x = x + 1` is a single atomic statement at the
database level, so concurrent transactions serialize on the row lock rather than racing on a
Java-side read-modify-write. This mirrors the existing `ExchangeRateRepository.upsert` pattern
already in the codebase (same `@Modifying @Query(nativeQuery = true)` shape).

**Alternatives considered**:
- `@Version`-based optimistic locking with a retry loop. Rejected: explicitly disallowed by
  Constitution Principle V ("Read-modify-write increments in application code MUST NOT be used"),
  and adds retry complexity for no benefit over a single atomic statement.
- Separate `SELECT ... FOR UPDATE` then `UPDATE`. Rejected: same read-modify-write problem, just
  with pessimistic locking instead of optimistic.

## Most-recent-common-date resolution

**Decision**: When no date is supplied, resolve the effective date via one query:

```sql
SELECT MAX(rate_date) FROM exchange_rates a
WHERE currency_code = :from
  AND EXISTS (
    SELECT 1 FROM exchange_rates b
    WHERE b.currency_code = :to AND b.rate_date = a.rate_date
  )
```

exposed as `ExchangeRateRepository.findLatestCommonDate(from, to)` returning `Optional<LocalDate>`.
When a date *is* supplied, skip this query and instead directly attempt
`findByCurrencyCodeAndRateDate` for both currencies; absence of either row is the
"no data for date" error path (FR-004, Edge Cases).

**Rationale**: Spec Assumptions explicitly define "most recent available" as the latest date both
currencies share, which may differ from either currency's individual latest date if collection
has gaps — a plain `MAX(rate_date) per currency` then intersect-in-Java approach would be more
code for the same DB-provable answer; a single indexed correlated-EXISTS query is simpler and
pushes the "gap" edge case to the database.

**Alternatives considered**: Fetch each currency's latest N rows and intersect dates in the
service layer. Rejected: more code, and correctness depends on picking a large-enough N to cover
arbitrary collection gaps — the SQL approach has no such window limit.

## Unknown currency detection

**Decision**: "Unknown currency" = no `exchange_rates` row exists for that code at all (any date).
Detected via `ExchangeRateRepository.existsByCurrencyCode(String)` (new derived query), checked
for both `from` and `to` before attempting any date-specific lookup, so an unknown-currency error
is distinguishable from a valid-currency/no-data-for-this-date error.

**Rationale**: Spec Assumption: "the set of valid currency codes is exactly the set for which the
system has ever stored a rate record." This also lets FR-004 (no data for *date*) and FR-007
(unknown currency) produce genuinely distinct error messages, per User Story 2's two separate
acceptance scenarios.

**Alternatives considered**: A separate static/config allow-list of currency codes. Rejected:
spec explicitly rules this out ("no separate manually maintained allow-list").

## Testing approach

**Decision**: Follow the existing convention seen in `ExchangeRateRepositoryTest` /
`CurrencyUsageRepositoryTest` — tests run against the real docker-compose PostgreSQL (no
H2/Testcontainers substitution), wrapped in `@Transactional` for rollback-per-test where that's
safe. The concurrency requirement (FR-009/SC-003) needs a dedicated non-`@Transactional` test that
fires N concurrent lookups (e.g., `ExecutorService` + `CountDownLatch`) against the same currency
pair and asserts the final `query_count` equals N — a single transaction wrapper would hide the
row-lock contention being tested.

**Rationale**: Consistent with the codebase's already-established choice (constraint/precision
behavior is Postgres-specific) and avoids introducing a second test-database strategy.

**Alternatives considered**: Testcontainers Postgres. Not introduced — no existing dependency or
prior use in this repo; would be a net-new tooling decision out of scope for this feature.
