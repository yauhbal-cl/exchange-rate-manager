# Phase 0 Research: Analytics Endpoint

No open `NEEDS CLARIFICATION` markers remain in the spec (the Clarifications and Assumptions
sections already resolved scope, default window, and tie-break rule). This file records the
implementation-pattern decisions needed before design.

## Decision: Historical trend query shape

- **Decision**: Add a native, set-based query to `ExchangeRateRepository` that joins the
  `exchange_rates` rows for `from` and `to` on `rate_date`, filtered to the requested
  `[startDate, endDate]` range, returning one row per date present for both currencies. Compute
  the spread-adjusted rate per row in the service layer using the exact same formula already in
  `ExchangeRateService.lookup` (ratio of `rate_to_usd` values, adjusted by `max(spreadFor(from),
  spreadFor(to))`), rather than a new formula.
- **Rationale**: FR-002 requires reusing the existing spread-adjustment formula; doing the
  arithmetic in the service layer (not SQL) keeps `BigDecimal`/`MathContext` semantics identical
  to the single-date lookup and keeps `SpreadLookup` the single source of truth for spread data
  (Constitution Principle VII). The join-and-filter query itself mirrors the existing
  `findLatestCommonDate` native-query pattern already in the repository.
- **Alternatives considered**:
  - Computing the ratio in SQL — rejected: duplicates precision-sensitive arithmetic in two
    places (SQL and Java) and risks drift from the single-date formula as spread rules evolve.
  - Fetching each date's rates with N+1 per-date queries reusing `findByCurrencyCodeAndRateDate`
    — rejected: turns a bounded date range into per-day round trips; a single set query scales
    correctly with range size.

## Decision: Default trend window

- **Decision**: When `startDate`/`endDate` are omitted, default to `[today - 29 days, today]`
  (30 calendar days inclusive), computed in the service layer, not the database.
- **Rationale**: Matches the spec's Assumptions section verbatim ("most recent 30 days"). "Most
  recent 30 days" is interpreted as a fixed calendar window ending today, not "the 30 most recent
  dates with data" — simpler to reason about, and consistent with FR-003 (dates without data are
  omitted, not backfilled from further back).
- **Alternatives considered**: Windowing by "last 30 dates that have data" — rejected as more
  complex and not what the Assumptions text describes; would also require an extra query to find
  those dates before the range query itself runs.

## Decision: Ranked/filtered usage analytics as query parameters on the existing endpoint

- **Decision**: Extend `GET /exchange/usage` with optional `limit` and `recentDays` query
  parameters rather than adding new endpoints. `limit` ranks by `queryCount DESC, currencyCode
  ASC` (FR-008) and truncates; `recentDays` filters to currencies whose `lastQueriedAt` falls
  within `now() - recentDays days`. Both are independently optional and composable.
- **Rationale**: US2 and US3 both act on the same underlying usage-analytics data as the existing
  endpoint (per Assumptions: "extend the existing usage analytics"); a single endpoint with
  optional filters/sort avoids duplicating the projection query and keeps the response shape
  (`UsageAnalyticsResponse`) consistent regardless of which parameters are supplied.
- **Alternatives considered**: Separate `/exchange/usage/ranked` and `/exchange/usage/recent`
  endpoints — rejected: the spec frames these as filters/views over one dataset, not distinct
  resources, and the acceptance scenarios never require them to be independently addressable
  URLs.

## Decision: Validation for new parameters

- **Decision**: Validate `from`/`to` currency existence and `startDate <= endDate` for the trend
  endpoint, and positivity of `limit`/`recentDays` for the usage endpoint, in the service layer
  before any query executes (FR-004, FR-010), raising the same typed exceptions the codebase
  already uses for validation failures (`UnknownCurrencyException`, and a new
  `InvalidDateRangeException` / reuse of Bean Validation `@Positive` on controller-bound query
  params), all resolved to `ProblemDetail` by the existing `@RestControllerAdvice` (FR-011).
- **Rationale**: Matches Constitution Principle VI (validation belongs in the service layer, not
  controllers) and the existing centralized-exception-handling pattern already in place for
  `UnknownCurrencyException`/`SameCurrencyException`/`RateDataNotFoundException`.
- **Alternatives considered**: Ad hoc `if`/`ResponseEntity.badRequest()` in the controller —
  rejected: violates the existing centralized `@RestControllerAdvice` pattern and Principle VI.
