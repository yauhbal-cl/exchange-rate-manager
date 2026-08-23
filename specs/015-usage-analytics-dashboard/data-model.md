# Phase 1 Data Model: Usage Analytics Dashboard

**Feature**: `015-usage-analytics-dashboard` | **Date**: 2026-08-23

Frontend-only. **No persisted entity, no schema change, no new API type.** Everything below is
either the existing generated API type (source) or an in-memory, view-only derivation computed once
per page load from a single `GET /exchange/usage` response.

---

## 1. Source type (existing, unchanged)

### `CurrencyUsageEntry` — `frontend/src/app/api-client/model/currencyUsageEntry.ts`

| Field | Type | Notes |
|---|---|---|
| `currencyCode` | `string` | 3-letter uppercase code; unique across the response |
| `queryCount` | `number` | int64 in the contract; `0` for a never-queried currency |
| `lastQueriedAt` | `string \| null` | ISO-8601 instant; `null` iff never queried |

`UsageAnalyticsResponse = { currencies: CurrencyUsageEntry[] }`.

**Population**: one entry per currency known to the system (every currency ever ingested into
`exchange_rates`), including never-queried ones — see `research.md` §1. This is the sole data
source for all three page sections (spec Key Entities: "Currency Usage Record").

**Retrieved as**: `ExchangeRateUsageAnalyticsService.getUsageAnalytics()` with **no** `limit` and
**no** `recentDays`, exactly once per page load (FR-005a, spec Assumption "One retrieval per page
load").

**Treated as immutable**: derivations copy before sorting; the resource value is never mutated.

---

## 2. Derived view models (new, `usage-metrics.ts`)

### 2.1 `UsageSummary` — backs the three KPI cards (FR-003 … FR-005a)

| Field | Type | Derivation | Empty-data value |
|---|---|---|---|
| `totalQueries` | `number` | Σ `queryCount` over **all** entries | `0` |
| `queriedCurrencyCount` | `number` | count of entries with `queryCount > 0` | `0` |
| `mostQueried` | `{ currencyCode: string; queryCount: number } \| null` | first entry of the FR-006 ordering, restricted to `queryCount > 0` | `null` |

Rules:

- Computed from the complete, unlimited entry set. The 10-row / 8-entry display caps (§2.2, §2.3)
  MUST NOT be applied before this computation (FR-005a).
- `mostQueried` tie-break: highest `queryCount`, then alphabetically first `currencyCode`
  (FR-005, US1 scenario 3) — deterministic across reloads (SC-006).
- `mostQueried === null` when no currency has ever been queried; the card then renders an explicit
  empty indication, never a blank or a `0`-count currency (US1 scenario 4, FR-013).

### 2.2 `RankedUsageRow[]` + `neverQueriedCount` — back the breakdown panel (FR-006 … FR-009a)

`BreakdownView = { rows: RankedUsageRow[]; displayedCount: number; queriedTotal: number; neverQueriedCount: number }`

| Field | Type | Derivation |
|---|---|---|
| `rows[].currencyCode` | `string` | from source entry |
| `rows[].queryCount` | `number` | from source entry; **invariant: ≥ 1** |
| `rows[].proportionPercent` | `number` | `queryCount / max(rows[].queryCount) * 100`, rounded to 2 dp; `0`-length bars impossible since every row has count ≥ 1 |
| `displayedCount` | `number` | `rows.length` (0 … 10) |
| `queriedTotal` | `number` | total number of currencies with `queryCount > 0` (= `UsageSummary.queriedCurrencyCount`); drives the "top 10 of N" indication when `queriedTotal > displayedCount` (FR-009) |
| `neverQueriedCount` | `number` | count of **all** entries with `queryCount === 0` — counted across every entry, not only displayed rows (FR-009a) |

Rules:

- **Filter first**: entries with `queryCount === 0` are excluded from `rows` entirely (FR-006,
  US2 scenario 4).
- **Then order**: `queryCount` DESC, `currencyCode` ASC (FR-006).
- **Then cap**: first 10 (FR-009). Cap is display-only.
- `proportionPercent` denominator is the highest count **among displayed rows** (FR-008), so the
  top row is always 100%. All-tied counts ⇒ every bar 100% (spec edge case). A minimum visible bar
  width is a CSS floor on the fill element, not a change to `proportionPercent`.
- `rows.length === 0` ⇒ the panel renders its empty state, and the footnote still reports
  `neverQueriedCount` (FR-013).
- `neverQueriedCount === 0` ⇒ footnote omitted or explicitly states zero (FR-009a, US2 scenario 5).

### 2.3 `RecentActivityEntry[]` — backs the recent-activity panel (FR-010 … FR-012a, FR-025)

| Field | Type | Derivation |
|---|---|---|
| `currencyCode` | `string` | from source entry |
| `lastQueriedAt` | `string` | non-null ISO-8601 instant, verbatim from the API → the `datetime` attribute (FR-025) |
| `relativePhrase` | `string` | elapsed-time phrase vs. the load-time `now` (§3) |
| `absoluteLocal` | `string` | local-timezone date + time-of-day, for the inspect/hover path (FR-012a) |

Rules:

- Exclude entries with `lastQueriedAt === null` (FR-011) — this also covers the spec edge case
  "query count but no recorded last-queried time": such a currency still appears in the breakdown
  panel (§2.2) but never here.
- Order `lastQueriedAt` DESC, then `currencyCode` ASC for identical instants (SC-006
  determinism).
- Cap at the first 8 (FR-011). Display-only.
- Empty ⇒ explicit empty-state message, not an empty list (FR-013, US3 scenario 4).

---

## 3. Time derivation (`relative-time.ts`)

Pure functions of `(instant: string, now: Date)` — `now` is captured **once** when the component is
created and never advanced, so phrases are fixed at load time (spec edge case "phrases go stale";
SC-006).

| Age of `instant` relative to `now` | Phrase |
|---|---|
| future (clock skew) or `< 60 s` | `"just now"` |
| `< 60 min` | `"N minutes ago"` |
| `< 24 h` | `"N hours ago"` |
| `< 30 d` | `"N days ago"` |
| `< 12 mo` | `"N months ago"` |
| otherwise | `"N years ago"` |

- Phrases from `Intl.RelativeTimeFormat` with `numeric: 'auto'`; `"just now"` is the distinct
  under-a-minute literal FR-012 requires.
- Future instants clamp to `"just now"` — never negative, never future-tense (spec edge case).
- `absoluteLocal` from `Intl.DateTimeFormat(undefined, { dateStyle: 'medium', timeStyle: 'short' })`
  — viewer's own locale and timezone, date and time-of-day both present (FR-012a).
- An unparseable instant is treated as absent: the entry is dropped from the panel rather than
  rendered with a broken phrase.

---

## 4. Count formatting

Every displayed count (KPI values, row counts, footnote count) is formatted with a single shared
`Intl.NumberFormat()` instance — locale thousands separators, no rounding, no abbreviation,
no truncation (FR-019). Raw values are kept unformatted in the view models above; formatting
happens at render time only.

---

## 5. Page state machine (FR-013 … FR-015a, SC-004, SC-010)

Derived from `rxResource`, exactly one state active at a time:

| State | Condition | Rendered |
|---|---|---|
| Loading | `usage.isLoading()` | explicit loading indication; **no** zeros, no empty states (FR-015) |
| Error | `usage.error()` — HTTP failure **or** the 10 s `timeout` (FR-015a) | one clear error message in place of all three data sections; no zeroed/fabricated/partial values (FR-014) |
| Empty | resolved, `currencies.length === 0` | KPI cards `0` / `0` / explicit "none" + both panels' empty states; no never-queried footnote figure to report |
| Populated | resolved, non-empty | KPI row + breakdown + recent activity, each section independently able to show its own empty state (e.g. records exist but nothing queried yet) |

Transitions are one-way per page load (no refresh control, no polling — spec Assumption "No live
auto-refresh").

---

## 6. Invariants

- **INV-1** Read-only: rendering this page issues exactly one `GET /exchange/usage` and mutates no
  counter (FR-021, SC-007) — the endpoint is a read and the page has no other call.
- **INV-2** KPI values are computed from the unlimited entry set; caps never narrow their input
  (FR-005a).
- **INV-3** Every breakdown row has `queryCount ≥ 1`; no zero-length bar can render (FR-006).
- **INV-4** `neverQueriedCount + queriedCurrencyCount === currencies.length` — the footnote and the
  second KPI card partition the same population.
- **INV-5** Every value shown visually is also present as text; bars carry no information not in
  their row's text (FR-022, FR-023, SC-008).
- **INV-6** Same input ⇒ same output: all derivations are pure and totally ordered, so repeat loads
  of unchanged data render identically (SC-006).
