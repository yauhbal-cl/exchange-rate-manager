# Data Model: Historical Exchange Rate Trends

This view has no persistent storage of its own — "entities" here are client-side signal shapes
and their mapping to/from `contracts/openapi.yaml`'s `/exchange/trend` and
`/exchange/trend/insight` schemas (unchanged by this feature).

## Currency Pair (signals)

| Signal | Type | Initial | Notes |
|---|---|---|---|
| `baseCurrency` | `signal<string>` | `'USD'` | one of `CURRENCIES` (reused from `rate-lookup`) |
| `quoteCurrency` | `signal<string>` | `'EUR'` | one of `CURRENCIES` |
| `pairError` | `computed<string \| null>` | derived | non-null when `baseCurrency() === quoteCurrency()` (FR-002) |

`swap()` method: reads both signals and writes them back transposed in one batch (single Angular
signal-write tick), then lets the AI-staleness effect (below) clear `aiInsight`.

## Period Selection (signal)

Discriminated union, matching `research.md` §4:

```ts
type PeriodSelection =
  | { kind: 'preset'; id: '7D' | '15D' | '1M' | '3M' | '6M' }
  | { kind: 'custom'; startDate: string; endDate: string }; // yyyy-MM-dd
```

| Signal | Type | Initial | Notes |
|---|---|---|---|
| `period` | `signal<PeriodSelection>` | `{ kind: 'preset', id: '1M' }` | default period per spec.md Assumptions |
| `periodError` | `computed<string \| null>` | derived | non-null when `kind === 'custom'` and (end < start) or (span > 6 months) — FR-006 |

`resolveRange(period(), today) -> { startDate, endDate }` (pure function, `period-presets.ts`)
turns either union member into concrete ISO date strings.

Preset definitions (`period-presets.ts`): `7D`/`15D` subtract calendar days; `1M`/`3M`/`6M`
subtract calendar months via a shared `subtractMonths(date, n)` helper. `periodError`'s "span >
6 months" check reuses `subtractMonths(endDate, 6)` (research.md §4, §8), so the `6M` preset and
the custom-range cap share one definition of "6 months" rather than two independently
approximated ones.

## Resolved Request (drives the trend `rxResource`)

| Signal | Type | Notes |
|---|---|---|
| `pairAndRange` | `computed<TrendRequest \| undefined>` | `undefined` (skipping the fetch) whenever `pairError()` or `periodError()` is non-null; otherwise `{ from, to, startDate, endDate }` |

**TrendRequest** (plain object):
- `from: string`, `to: string` — 3-letter codes
- `startDate: string`, `endDate: string` — `yyyy-MM-dd`, always both present (presets/custom both
  resolve to concrete dates; no "omit to use backend default" case here, unlike 012's optional
  date)

## Trend Result

Maps 1:1 from the generated `ExchangeRateTrendResponse` (`contracts/openapi.yaml` →
`components/schemas/ExchangeRateTrendResponse`):

| Field | Type | Source |
|---|---|---|
| `fromCurrency` | `string` | `ExchangeRateTrendResponse.fromCurrency` |
| `toCurrency` | `string` | `ExchangeRateTrendResponse.toCurrency` |
| `points` | `RateTrendPoint[]` | `ExchangeRateTrendResponse.points`, chronological (oldest→newest) per the endpoint's contract |

`RateTrendPoint`: `{ rateDate: string; rate: string }` — `rate` kept as a string end to end
(Constitution I); never parsed for the chart/table's own rendering of that exact value.

Exposed via `trend.value()` / `trend.isLoading()` / `trend.error()` (the `rxResource` result).

## Trend Metrics (derived, `trend-metrics.ts` pure functions)

Computed from `trend.value()?.points` (empty/absent → "no data" state, FR-015):

| Field | Type | Notes |
|---|---|---|
| `latest` | `{ display: string; value: Decimal } \| null` | last point's `rate` (verbatim string) + `Decimal`-parsed value |
| `periodChange` | `{ absolute: string; percent: string; value: Decimal } \| null` | `(last.value - first.value)` via `Decimal`; `percent` formatted `+2.40%`/`-1.10%` style; `null` if fewer than 2 points |
| `periodHigh` | `{ display: string; value: Decimal; date: string } \| null` | max by `Decimal`-compared value; `date` is that point's `rateDate` |
| `periodLow` | `{ display: string; value: Decimal; date: string } \| null` | min by `Decimal`-compared value; `date` is that point's `rateDate` |
| `dailyChanges` | `Array<{ rateDate: string; percent: string \| null }>` | per-row % vs. previous row via `Decimal`; `null` for the first (oldest) row, which has no prior value |

Per `research.md` §2: `value` fields (`Decimal` instances, from `decimal.js`) are
display/comparison-only and never re-serialized; `display` strings for `latest`/`periodHigh`/
`periodLow` are always the original API string, not a reformatted number. All arithmetic
(subtraction, division, min/max comparison) uses `Decimal`, never JS `number`/`parseFloat`, per
Constitution I.

## AI Insight Request (drives the AI `rxResource`, explicit-trigger only)

| Signal | Type | Notes |
|---|---|---|
| `aiRequest` | `signal<TrendRequest \| undefined>` | `undefined` until "Generate insight" is clicked; set to the *current* `pairAndRange()` value at click time |

An `effect()` watches `pairAndRange()` and sets `aiRequest.set(undefined)` on every change
(including a swap), implementing FR-014's staleness rule structurally.

## AI Insight Result

Maps 1:1 from `TrendInsightResponse` (`contracts/openapi.yaml` →
`components/schemas/TrendInsightResponse`):

| Field | Type | Source |
|---|---|---|
| `fromCurrency` / `toCurrency` | `string` | as returned |
| `startDate` / `endDate` | `string` | resolved range echoed back by the backend |
| `narrative` | `string` | rendered verbatim (no markdown/HTML interpretation) |

Exposed via `aiInsight.value()` / `aiInsight.isLoading()` / `aiInsight.error()`.

## AI Insight Error (derived, not a stored signal)

A `computed<AiInsightError | null>` over `aiInsight.error()`:

| Field | Type | Notes |
|---|---|---|
| `category` | `'no-data' \| 'unavailable'` | `404` → `no-data`; anything else (`503`, network failure, timeout) → `unavailable` (FR-013) |
| `message` | `string` | `ProblemDetail.detail` when present, else a fixed fallback for `unavailable` |

The "Generate insight" control itself is disabled (not merely erroring after the fact) whenever
`trend.value()?.points` is empty/absent, per FR-013's "no underlying data" case and User Story 5
Acceptance Scenario 3 (don't call the AI with empty data).

## Historical Rates Table (derived view, no separate signal)

Renders `trend.value()?.points` reversed (most-recent-first, per spec.md Assumptions/table
requirements) paired with `dailyChanges` by `rateDate`, plus each row's verbatim `rate` string.
Table rows are always exactly the chart's points (FR-009, SC-003) — both read from the same
`trend.value()`, so there is no separate fetch/state to drift out of sync.

## State Diagram (informal)

```
Trend/metrics/chart/table (auto):
  idle (pairAndRange = undefined, e.g. pairError/periodError set)
  --pair & period valid--> loading (rxResource fetching)
  loading --success (points = [])--> "no data" state (FR-015)
  loading --success (points.length >= 1)--> populated
  loading --failure--> error state (backend unreachable/4xx)
  any state --pair or period changes (still valid)--> loading (new request)

AI Insight (explicit):
  empty (aiRequest = undefined) -- enabled only if trend has points
  --click "Generate insight"--> loading
  loading --success--> narrative shown
  loading --failure--> error shown (categorized)
  narrative | error | loading --pair/period changes or swap--> empty (cleared, FR-014)
```
