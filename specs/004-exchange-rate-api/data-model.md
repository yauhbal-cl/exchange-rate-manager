# Phase 1 Data Model: Exchange Rate API

No new tables or entities. This feature reads the existing `ExchangeRate` and `CurrencyUsage`
entities (unchanged) and introduces one non-persisted reference structure and API-facing DTOs
(generated from `contracts/openapi.yaml`, not hand-written).

## Existing entities (read/incremented, not changed)

### ExchangeRate (`exchange_rates`)
Already defined in `backend/src/main/java/com/exchangerate/manager/entity/ExchangeRate.java`.
Relevant fields for this feature: `currencyCode` (CHAR(3)), `rateToUsd` (`BigDecimal`, precision
19 scale 6), `rateDate` (`LocalDate`). Read-only from this feature's perspective.

### CurrencyUsage (`currency_usage`)
Already defined in `backend/src/main/java/com/exchangerate/manager/entity/CurrencyUsage.java`.
Fields: `currencyCode` (unique), `queryCount` (`Long`), `lastQueriedAt` (`Instant`, DB-generated).
This feature adds the only write path to this table beyond its initial migration default: an
atomic upsert-increment (see research.md).

## New non-persisted reference: Spread Lookup

Not a JPA entity — a static, in-memory keyed lookup (Constitution Principle VII: data-driven, not
conditional; spec Assumptions: static, not user-editable in this feature).

| Key | Spread % | Source |
|---|---|---|
| `USD` (base) | 0.00 | Appendix B |
| tier 1 currencies | 3.25 | Appendix B |
| tier 2 currencies | 4.50 | Appendix B |
| tier 3 currencies | 6.00 | Appendix B |
| `default` (any other known currency) | 2.75 | Appendix B |

**Validation rule**: every currency code that has ever been persisted to `exchange_rates` MUST
resolve to a spread value — falls through to `default` if not explicitly tiered. Never throws for
a currency that passed the "known currency" check.

**Field/shape**: `Map<String, BigDecimal>` plus a `BigDecimal spreadFor(String currencyCode)`
accessor that upper-cases the key and falls back to the `default` entry.

## Service-layer result shape (not persisted)

`ExchangeRateLookupResult` — internal record produced by `ExchangeRateService`, mapped by
MapStruct into the generated `ExchangeRateResponse` API model:

| Field | Type | Notes |
|---|---|---|
| fromCurrency | String | echoes validated request input |
| toCurrency | String | echoes validated request input |
| rate | BigDecimal | spread-adjusted, full precision (FR-014) |
| rateDate | LocalDate | the resolved date the underlying rates apply to |
| fromCurrencyUsageCount | Long | post-increment count for `fromCurrency` |
| toCurrencyUsageCount | Long | post-increment count for `toCurrency` |

## New usage-analytics response shape (not persisted)

Per clarification (spec.md Clarifications, 2026-08-22): the response covers every currency the
system has ever stored a rate record for (distinct `currency_code` in `exchange_rates`), left-
joined against `currency_usage` — not just currencies that already have a `currency_usage` row.
A currency with no `currency_usage` row maps to `queryCount = 0` and `lastQueriedAt = null`.
Maps to the generated `CurrencyUsageEntry` / `UsageAnalyticsResponse` API models (see contracts/):
`currencyCode`, `queryCount`, `lastQueriedAt`.

**Query shape**: `SELECT er.currency_code, COALESCE(cu.query_count, 0), cu.last_queried_at`
`FROM (SELECT DISTINCT currency_code FROM exchange_rates) er`
`LEFT JOIN currency_usage cu ON cu.currency_code = er.currency_code` — a native, cross-table query
exposed as `CurrencyUsageRepository.findAllCurrencyUsage()` (per tasks.md T007), not a plain
`CurrencyUsageRepository.findAll()`.

## Validation rules (FR-007, Edge Cases)

1. `from` and `to` MUST each match `^[A-Z]{3}$` (mirrors entity `@Pattern`) — malformed input
   rejected before any DB access, via `@Valid`/generated API parameter constraints where the
   openapi-generator supports it, else an explicit service-layer check.
2. `from` MUST NOT equal `to` — rejected as invalid input (`SameCurrencyException` → 400), never
   computed as a trivial 1.0 rate.
3. `from` and `to` MUST each have at least one `exchange_rates` row ever
   (`existsByCurrencyCode`) — otherwise `UnknownCurrencyException` → 400 Bad Request (an unknown
   code is a malformed request parameter, same class of error as an invalid pair — FR-007 groups
   both under "descriptive error... without performing any calculation").
4. If a `date` is supplied and either currency lacks a row for that exact date, or no `date` is
   supplied and no common date exists across both currencies at all →
   `RateDataNotFoundException` → 404.

## State transitions

None — this feature has no entity lifecycle; it performs reads plus one atomic counter increment
per successful lookup. No entity moves between states.
