# Phase 1 Data Model: Backend Spring AI Slice (Trend Insight Endpoint)

No new database table, entity, or migration. This slice reads the existing `exchange_rates` table
(via the existing `ExchangeRateRepository.findTrend` query, unchanged) and never persists its
output — per spec.md's Assumptions, the insight is generated on demand and not stored for later
retrieval.

Two transient (request/response-only) shapes are introduced, corresponding to spec.md's Key
Entities:

## Trend Insight Request (transient — query parameters, not a persisted or serialized class)

| Field | Type | Notes |
|---|---|---|
| `from` | `String` (3-letter currency code) | Required; pattern `^[A-Z]{3}$`, matching `/exchange/trend`'s existing `from` param |
| `to` | `String` (3-letter currency code) | Required; same pattern as `from` |
| `startDate` | `LocalDate`, optional | Defaults to 29 days before today when omitted (shared resolution logic with `/exchange/trend`, see research.md) |
| `endDate` | `LocalDate`, optional | Defaults to today when omitted |

Validation rules (enforced in `TrendInsightService`, in this order):
1. `from`/`to` must be known currency codes (existing `existsByCurrencyCode` check, reused) → 400 `UnknownCurrencyException` on failure
2. Resolved `startDate` must not be after resolved `endDate` → 400 `InvalidDateRangeException` (existing, reused)
3. Resolved range must not span more than ~365 daily points → 400 `TrendRangeTooLargeException` (new)
4. The resulting trend series (from `ExchangeRateService.getTrend`) must be non-empty → 404 `RateDataNotFoundException` (existing, reused)

## Trend Insight Result (transient — new Java `record` in `service` package)

| Field | Type | Notes |
|---|---|---|
| `fromCurrency` | `String` | Echoed from the request |
| `toCurrency` | `String` | Echoed from the request |
| `startDate` | `LocalDate` | The *resolved* (default-applied) start date actually summarized |
| `endDate` | `LocalDate` | The *resolved* end date actually summarized |
| `narrative` | `String` | The model-generated plain-language narrative, grounded in the supplied `RateTrendPoint` list |

This record only exists for a successful generation — all failure paths (no data, AI unavailable,
range too large, unknown currency, invalid range) are represented as thrown exceptions mapped to
`ProblemDetail` responses by `GlobalExceptionHandler`, not as a variant/status field on this
record. This keeps the "explicit unavailable outcome, never invented commentary" requirement
(FR-005) expressed through the existing HTTP error-response convention rather than a new success/
failure union type.

## Relationship to existing entities

`TrendInsightResult` is derived entirely from `List<RateTrendPoint>` (existing record:
`rateDate` + spread-adjusted `BigDecimal` rate), itself derived from `ExchangeRate` rows via the
existing `ExchangeRateRepository.findTrend` native query. No new relationship, foreign key, or
index is introduced.
