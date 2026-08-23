# UI Contract: Usage Analytics Dashboard

This feature adds no backend endpoint, changes no schema in `contracts/openapi.yaml`, and
regenerates no client code. The contract below is the *view's* observable behavior — its backend
calls, DOM surface, and state rules — so implementation and tests share one reference.

## Backend calls (existing contract, unchanged)

- **`GET /exchange/usage`** (`contracts/openapi.yaml` → `operationId: getUsageAnalytics`), consumed
  only via `ExchangeRateUsageAnalyticsService.getUsageAnalytics()` — **both** `limit` and
  `recentDays` omitted, so the response is the complete, unranked-cap-free set including
  never-queried currencies (`queryCount: 0`, `lastQueriedAt: null`).
- Called **exactly once per page load** (no polling, no refresh control, no per-section calls).
- Wrapped in RxJS `timeout({ each: 10_000 })` (FR-015a).
- No other generated service or method may be called from this view. No `HttpClient` use directly.
- The call is a read: it changes no usage counter (FR-021, SC-007).

## Route and navigation

- Path `usage-analytics` in `frontend/src/app/app.routes.ts` — **address unchanged**; the existing
  lazy `loadComponent` entry keeps pointing at `UsageAnalytics` (FR-020).
- The existing "Usage Analytics" nav link in `frontend/src/app/shell/shell.html` is unchanged.
- The current placeholder implementation of `UsageAnalytics` is replaced in place.

## Layout order (traces to FR-001, FR-002, FR-016 … FR-018)

Top to bottom, matching DOM order (so the narrow-viewport stack needs no reordering):

1. `<h1>` page title + one-line subtitle.
2. KPI row — exactly three bordered cards, one row on wide viewports.
3. Two-column grid: breakdown panel (left, visibly wider — `minmax(0, 1.6fr)`) beside
   recent-activity panel (right — `minmax(0, 1fr)`); collapses to one column at the ≤900 px
   breakpoint, order preserved (FR-017).

Page content centered, `max-width: 1180px`, consistent with `rate-lookup` (FR-018).

## Component surface

`UsageAnalytics` — `frontend/src/app/features/usage-analytics/usage-analytics.ts`, standalone,
routed at `usage-analytics`. Child presentational components take signal inputs only and emit
nothing.

| Element | Selector contract | Behavior |
|---|---|---|
| Page header | `h1`, plus subtitle paragraph | Title identifies the page as query/usage analytics; subtitle states it is an overview of query activity (FR-001) |
| KPI section | `section[data-testid="kpi-row"]` with an `<h2>` referenced by `aria-labelledby` | Exactly three bordered cards (FR-002, FR-024) |
| Total queries card | `[data-testid="kpi-total-queries"]` | Σ `queryCount` over all entries, locale-formatted (FR-003, FR-019) |
| Unique currencies card | `[data-testid="kpi-queried-currencies"]` | count of entries with `queryCount > 0` (FR-004) |
| Most queried card | `[data-testid="kpi-most-queried"]` | top currency code + its count; ties resolved alphabetically; explicit empty indication (not blank, not `0`) when nothing was ever queried (FR-005, FR-013) |
| Breakdown panel | `app-usage-breakdown-panel`, `section` + `<h2>` "Activity breakdown" | Rows ordered `queryCount` DESC then code ASC, max 10, zero-count currencies excluded (FR-006, FR-009) |
| Breakdown row | `[data-testid="breakdown-row"]`, `[data-code="XXX"]` | Contains the currency code as text, a bar, and the locale-formatted count as text (FR-007, FR-022) |
| Breakdown bar | `[data-testid="breakdown-bar"][aria-hidden="true"]` with fill width bound via `[style.width.%]` | Length = `queryCount / max displayed count × 100`; hidden from assistive tech (FR-008, FR-023) |
| "Top N of M" indication | rendered only when `queriedTotal > displayedCount` | States the panel shows the top entries out of the larger total (FR-009) |
| Never-queried footnote | `[data-testid="never-queried-footnote"]` | "N currencies have never been queried", counted over all entries; visually subordinate; omitted or explicitly zero when none (FR-009a) |
| Breakdown empty state | `[data-testid="breakdown-empty"]` | Shown when zero rows; no rows, no zero-length bars; footnote still rendered (FR-013, US2 scenario 6) |
| Recent activity panel | `app-recent-activity-panel`, `section` + `<h2>` "Recent activity" | Entries ordered `lastQueriedAt` DESC then code ASC, max 8, null-timestamp currencies excluded (FR-010, FR-011) |
| Recent entry | `[data-testid="recent-entry"]`, `[data-code="XXX"]` | Currency code as text plus `<time [attr.datetime]="ISO instant" [title]="absolute local date-time">{{ relative phrase }}</time>` (FR-012, FR-012a, FR-022, FR-025) |
| Recent empty state | `[data-testid="recent-empty"]` | Shown when zero entries, instead of an empty list (FR-013, US3 scenario 4) |
| Loading state | `[data-testid="usage-loading"]` | Sole content of the data area while the request is in flight; no zeros, no empty states (FR-015) |
| Error state | `[data-testid="usage-error"]` | Single clear message replacing all three data sections, for HTTP failure **and** the 10 s timeout alike (FR-014, FR-015a) |

No element in this view is focusable or interactive: no buttons, no inputs, no pointer-only
disclosure of any value (FR-026).

## Behavioral contract (traces to spec FRs / scenarios)

1. Page load fires exactly one `getUsageAnalytics()` with no arguments; all three sections derive
   from that single response, so no two sections can disagree (FR-005a, SC-003, spec Assumption
   "One retrieval per page load").
2. While in flight → loading state only (FR-015). Never a flash of zeros or empty states.
3. Success → KPI values computed over the **entire** response, before any display cap; the 10-row
   and 8-entry caps affect only the panels (FR-005a, US1 Independent Test).
4. Tie for highest count → exactly one currency in the "most queried" card, alphabetically first of
   the tied codes; identical across reloads (FR-005, US1 scenario 3, SC-006).
5. No currency ever queried → KPI cards read `0`, `0`, explicit empty indication; both panels show
   their empty states; the footnote reports every known currency as never queried (FR-013, spec
   Edge Cases).
6. Zero-count currencies never appear as breakdown rows, and are the sole input to the footnote
   count (FR-006, FR-009a, US2 scenarios 4–5).
7. More than 10 queried currencies → exactly 10 rows plus the "top 10 of M" indication (FR-009,
   US2 scenario 3).
8. Highest-count row's bar is full length; every other bar is proportional to it; all-tied counts →
   all bars full length; single queried currency → one full-length row (FR-008, spec Edge Cases).
9. Currency with `queryCount > 0` but `lastQueriedAt === null` → appears in the breakdown panel,
   absent from the recent-activity panel (FR-011, spec Edge Cases).
10. Relative phrases are computed against a `now` captured once at component creation; they do not
    tick while the page is open, and a reload recomputes them (FR-012, spec Edge Cases).
11. A `lastQueriedAt` in the future relative to that `now` (clock skew) renders the just-now phrase,
    never a negative or future-tense phrase (spec Edge Cases).
12. HTTP failure → error state only; no zeros, no partial data, no fabricated values (FR-014,
    US-level "Data unavailable" edge case).
13. Response not received within 10 s → the wait is abandoned and the same error state renders; the
    loading indication never persists indefinitely (FR-015a, SC-010).
14. All displayed counts carry locale thousands separators and are never rounded, abbreviated, or
    truncated (FR-019, spec "Very large query counts" edge case).
15. Repeat loads of unchanged data produce identical DOM — same ordering, same most-queried
    currency, same entries, same phrases (SC-006).
16. Screen-reader traversal yields each breakdown row once as code + count, each recent entry once
    as code + time, and every KPI value, with nothing conveyed by bar length alone (FR-022, FR-023,
    SC-008).
17. Viewing the page any number of times leaves every currency's `queryCount` unchanged (FR-021,
    SC-007).
