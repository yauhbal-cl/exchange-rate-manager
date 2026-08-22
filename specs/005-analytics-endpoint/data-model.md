# Phase 1 Data Model: Analytics Endpoint

No new tables or columns. Both entities below are read models composed from the existing
`exchange_rates` and `currency_usage` tables (V1/V2 migrations); no new Flyway migration is
needed for this feature.

## Rate Trend Point

Transient (non-persisted) DTO — one entry in the historical trend response.

| Field | Type | Notes |
|---|---|---|
| `rateDate` | `LocalDate` | Date both currencies have stored data for; chronological ascending across the series (FR-005). |
| `rate` | `BigDecimal` | Spread-adjusted rate for that date, computed with the same formula/`MathContext` as `ExchangeRateService.lookup` (FR-002, FR-012). |

**Source**: joined rows from `exchange_rates` for `fromCurrency`/`toCurrency` sharing a
`rate_date` within `[startDate, endDate]`. Dates missing either currency's rate are never
materialized into this DTO (FR-003) — no null/interpolated `rate` ever exists.

**Validation (pre-query, service layer)**:
- `fromCurrency`, `toCurrency` must exist in `exchange_rates.currency_code` → else
  `UnknownCurrencyException` (400).
- `startDate <= endDate` when both supplied → else `InvalidDateRangeException` (400).

## Ranked Usage Entry

Reuses the existing `CurrencyUsageEntry` schema/`UsageAnalyticsMapper` output — no new shape.
"Ranked" and "recency-filtered" are sort/filter modes over the same
`CurrencyUsageRepository.CurrencyUsageProjection` rows already used by `GET /exchange/usage`.

| Field | Type | Notes |
|---|---|---|
| `currencyCode` | `String` | Unchanged. |
| `queryCount` | `Long` | Sort key, descending; ties broken by `currencyCode` ascending (FR-008). |
| `lastQueriedAt` | `Instant`, nullable | Filter key for `recentDays`; a currency with `lastQueriedAt == null` (never queried) is excluded whenever a `recentDays` filter is applied (FR-009). |

**Validation (pre-query, service layer)**:
- `limit`, when supplied, must be a positive integer → else `400` (FR-010).
- `recentDays`, when supplied, must be a positive integer → else `400` (FR-010).

## State / Lifecycle

Both entities are read-only projections computed per request; no state transitions, no writes.
Retrieving either MUST NOT mutate `currency_usage` counters (FR-006, SC-005) — the trend query
and the usage-analytics query path never call `CurrencyUsageRepository.incrementUsage`.
