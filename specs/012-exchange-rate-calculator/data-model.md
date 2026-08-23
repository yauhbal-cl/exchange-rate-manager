# Data Model: Exchange Rate Calculator View

This view has no persistent storage of its own — "entities" here are the client-side signal
shapes and their mapping to/from the existing `contracts/openapi.yaml` `/exchange` schemas.

## Rate Lookup Form State (signals)

| Signal | Type | Initial | Notes |
|---|---|---|---|
| `fromCurrency` | `string` | `''` | one of `CURRENCY_CODES` once selected |
| `toCurrency` | `string` | `''` | one of `CURRENCY_CODES` once selected |
| `date` | `string` | `''` | `yyyy-MM-dd` or `''` (blank = omit from request) |
| `validationError` | `computed<string \| null>` | derived | see Validation Rules below; recomputes from the three signals above |

## Submitted Request (drives the API call)

| Field | Type | Notes |
|---|---|---|
| `submittedRequest` | `signal<RateLookupRequest \| undefined>` | set only by the submit handler after `validationError()` is `null`; this is the sole input `rxResource` reacts to |

**RateLookupRequest** (plain object, not a class):

- `from: string` — 3-letter code
- `to: string` — 3-letter code
- `date: string | undefined` — `undefined` when the user left the date blank (never sent as an
  empty-string query param)

## Rate Lookup Result

Maps 1:1 from the generated `ExchangeRateResponse` model (`contracts/openapi.yaml` →
`components/schemas/ExchangeRateResponse`) — no transformation, no re-typing of `rate`:

| Field | Type | Source |
|---|---|---|
| `fromCurrency` | `string` | `ExchangeRateResponse.fromCurrency` |
| `toCurrency` | `string` | `ExchangeRateResponse.toCurrency` |
| `rate` | `string` | `ExchangeRateResponse.rate` — kept as `string`, never parsed to `number` (Constitution I, frontend analogue) |
| `rateDate` | `string` | `ExchangeRateResponse.rateDate` |
| `fromCurrencyUsageCount` | `number` | `ExchangeRateResponse.fromCurrencyUsageCount` |
| `toCurrencyUsageCount` | `number` | `ExchangeRateResponse.toCurrencyUsageCount` |

Exposed via `rate.value()` (the `rxResource` result signal, named `rate` in the component,
matching the existing placeholder's naming).

## Lookup Error (derived, not a stored signal)

A `computed<LookupError | null>` over `rate.error()`:

| Field | Type | Notes |
|---|---|---|
| `category` | `'invalid' \| 'no-data' \| 'unreachable'` | derived from `HttpErrorResponse.status`: `400` → `invalid`, `404` → `no-data`, anything else (`0`, network error, other 4xx/5xx) → `unreachable` |
| `message` | `string` | `HttpErrorResponse.error?.detail` (the `ProblemDetail.detail` body) when present, else a fixed fallback string for `unreachable` |

Source schema for the 400/404 bodies: `contracts/openapi.yaml` →
`components/schemas/ProblemDetail` (RFC 9457; `detail` is the field surfaced to the user).

## Validation Rules (client-side, before `submittedRequest` is ever set)

1. `fromCurrency` and `toCurrency` must both be non-empty (a real selection was made).
2. `fromCurrency !== toCurrency`.
3. If `date` is non-empty, it must not be lexicographically after today's `yyyy-MM-dd` string
   (see `research.md` §6 for why string comparison, not `Date` parsing).

`validationError()` returns the first failing rule's message, or `null` when the form is
submittable. The submit control is disabled whenever `validationError() !== null` **or**
`rate.isLoading()` is `true` (FR-004, FR-005).

## State Diagram (informal)

```
idle (submittedRequest = undefined)
  --submit (valid)--> loading (rxResource fetching)
loading --success--> success (rate.value() set, rate.error() = undefined)
loading --failure--> error (rate.error() set, categorized)
success | error --submit (valid, new/changed inputs)--> loading
```

There is no "retry" as a distinct state — retry is just another submit with the same (still
visible) form values, per the spec's Assumptions.
