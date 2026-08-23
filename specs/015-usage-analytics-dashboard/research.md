# Phase 0 Research: Usage Analytics Dashboard

**Feature**: `015-usage-analytics-dashboard` | **Date**: 2026-08-23

All Technical Context unknowns for this feature are resolved below. This is a frontend-only
presentation feature over the already-shipped `GET /exchange/usage` endpoint; no backend, schema,
or contract research was required beyond confirming what that endpoint already returns (§1).

---

## 1. What `GET /exchange/usage` actually returns (data-source confirmation)

**Decision**: Call `ExchangeRateUsageAnalyticsService.getUsageAnalytics()` **once per page load
with no parameters** (`limit` and `recentDays` both omitted) and derive all three page sections
from that single response.

**Rationale**: Verified against the live contract and implementation:

- `contracts/openapi.yaml` → `UsageAnalyticsResponse { currencies: CurrencyUsageEntry[] }`,
  `CurrencyUsageEntry { currencyCode: string, queryCount: int64, lastQueriedAt: date-time|null }`.
- `CurrencyUsageRepository.findCurrencyUsage(limit, recentDays)` selects
  `SELECT DISTINCT currency_code FROM exchange_rates` **LEFT JOIN** `currency_usage`, so:
  - the row set is "every currency the system knows about" (every currency ever ingested), which
    is exactly the population FR-003/FR-004/FR-005/FR-009a require ("all currencies known to the
    system");
  - never-queried currencies come back present, with `queryCount = 0` and `lastQueriedAt = null`
    — they are **not** omitted. So the FR-009a never-queried footnote is computable client-side as
    "entries with `queryCount === 0`"; no extra call, no separate currency list needed.
  - with `limit` omitted, Postgres `LIMIT NULL` means no cap → the complete set, as FR-005a
    requires for the KPIs.
- Omitting `recentDays` is required: supplying it would exclude never-queried currencies and
  break the FR-009a footnote and the FR-003/FR-004 system-wide KPIs.

**Alternatives considered**:

- *Two calls (`limit=10` for the breakdown, unlimited for KPIs)* — rejected: two round trips, two
  snapshots that can disagree (violates SC-003/SC-006 internal consistency), and the unlimited
  call already contains everything the limited one would.
- *`recentDays` for the recent-activity panel* — rejected: it is a day-window filter, not an
  "N most recent" selector, and it drops the zero-count rows the footnote needs. Recency ordering
  and the 8-entry cap are display concerns the page does itself (FR-011).
- *A new backend endpoint / new query params* — rejected: spec Assumptions fix this feature as
  presentation-only ("existing usage analytics data source is reused as-is"), and nothing the page
  needs is missing from the current payload.

## 2. Client-side ordering and tie-breaks (determinism)

**Decision**: The page sorts the response itself: breakdown rows by `queryCount` DESC then
`currencyCode` ASC (FR-006); the "most queried" KPI is the first element of that same ordering
(FR-005); recent-activity entries by `lastQueriedAt` DESC then `currencyCode` ASC (FR-010).
Sorting is done on a copy, never in place on the resource value.

**Rationale**: `findCurrencyUsage` does happen to `ORDER BY COALESCE(query_count,0) DESC,
currency_code ASC` today, but `openapi.yaml` documents ordering only as an effect of `limit`
("ranked by queryCount descending") and says nothing about ties or about ordering by
`lastQueriedAt`. SC-006 demands byte-identical repeat renders, so the page owns its ordering
rather than depending on an unspecified server guarantee. Sorting ≤ a few hundred rows is free.
Copy-before-sort keeps `Array.prototype.sort`'s in-place mutation off the resource's own array.

**Alternatives considered**: trusting server order (rejected: undocumented, and gives no
`lastQueriedAt` ordering at all); asking the backend to guarantee the ordering in the contract
(rejected: contract change, out of scope per spec Assumptions).

## 3. 10-second retrieval timeout (FR-015a)

**Decision**: Wrap the service call in RxJS `timeout({ each: 10_000 })` inside the `rxResource`
`stream`, and render the single FR-014 error state for **any** resource error — `TimeoutError` and
HTTP failure alike.

```ts
protected readonly usage = rxResource({
  stream: () => this.service.getUsageAnalytics().pipe(timeout({ each: 10_000 })),
});
```

**Rationale**: `rxResource` already exposes `isLoading()` / `error()` / `value()`, which map 1:1
onto the FR-015 loading, FR-014 error, and data states. `timeout` converts a stalled request into
an error notification, so `error()` becomes truthy and the loading indication is replaced — which
is precisely FR-015a ("stop waiting and show the FR-014 error state"). RxJS 7.8 is already a
dependency; `timeout` needs no new package and no `HttpInterceptor`. The spec asks for one clear
error message (FR-014), so the page deliberately does **not** distinguish timeout from failure in
its copy.

**Alternatives considered**:

- *A global `HttpInterceptor` timeout* — rejected: changes every request in the app (including the
  AI-insight call, which is legitimately slower) for one page's requirement.
- *`setTimeout` + manual signal flag* — rejected: leaves the HTTP request running, duplicates
  state that `rxResource.error()` already models, and needs manual teardown on destroy.
- *`timeout({ first: ... })`* — rejected: `each` also covers a response that begins and then
  stalls; for a single-emission HTTP observable the two coincide, and `each` is the stricter read.

## 4. Elapsed-time phrasing and machine-readable instants (FR-012, FR-012a, FR-025)

**Decision**: One pure helper module. Elapsed phrases come from `Intl.RelativeTimeFormat` with a
fixed unit ladder against a **load-time `now` captured once** in the component; the absolute
instant is rendered as a `<time [attr.datetime]="entry.lastQueriedAt" [title]="absoluteLocal">`
element.

- Unit ladder (coarsening as age grows, per FR-012): `< 60 s` → the distinct literal phrase
  `"just now"`; `< 60 min` → minutes; `< 24 h` → hours; `< 30 d` → days; `< 12 mo` → months;
  else years. `Intl.RelativeTimeFormat('en', { numeric: 'auto' })` renders "3 minutes ago",
  "2 days ago".
- Clock skew (spec edge case): a `lastQueriedAt` in the future relative to load-time `now` clamps
  to `"just now"` — never a negative or future-tense phrase.
- `datetime` attribute carries the raw ISO-8601 instant string from the API verbatim (FR-025);
  the `title` carries `Intl.DateTimeFormat(undefined, { dateStyle, timeStyle })` in the viewer's
  own locale/timezone (FR-012a: date *and* time-of-day, local).

**Rationale**: `Intl.RelativeTimeFormat` is built into every browser in the supported range —
no `date-fns`/`dayjs` dependency for one formatting concern. Capturing `now` once at load matches
the spec exactly ("phrases reflect the moment of page load and do not tick forward", SC-006
identical repeat renders) and keeps the helper a pure function of `(instant, now)`, so every
threshold and the skew clamp are unit-testable without fake timers. `<time datetime>` is the
standard machine-readable carrier, satisfying FR-025 for assistive tech and tooling in the same
element that shows the human phrase; `title` gives the FR-012a hover/inspect path without adding
an interactive control (FR-026 forbids pointer-only or focus-trapping affordances).

**Alternatives considered**:

- *A live-ticking `interval` refresh of the phrases* — rejected: spec explicitly says no
  auto-refresh and that phrases go stale by design.
- *`date-fns` `formatDistanceToNow`* — rejected: new dependency, no locale story better than
  `Intl`, and it hides the skew case behind "in 3 minutes".
- *Absolute-only or relative-only display* — rejected: the clarification session settled on
  relative text plus absolute on inspection.

## 5. Count formatting (FR-019)

**Decision**: `new Intl.NumberFormat()` (viewer default locale, no options) for every displayed
count — KPI totals, per-row counts, footnote count.

**Rationale**: FR-019 requires locale-appropriate thousands separators with no rounding,
abbreviation, or truncation; the default `Intl.NumberFormat` integer path does exactly that.
`queryCount` arrives as an OpenAPI `int64` typed `number` in the generated client — well inside
`Number.MAX_SAFE_INTEGER` for a query counter, so no `BigInt`/`Decimal` handling is warranted
here. Note Constitution I (`BigDecimal`/`Decimal` precision) governs **monetary and rate values**;
these are integer event counters, not money, and this page displays no rate at all — so
`decimal.js` is deliberately not used, unlike feature 013.

**Alternatives considered**: `toLocaleString()` on the number (equivalent, but a fresh formatter
per call in a loop); compact notation "1.2K" (explicitly forbidden by FR-019); `Decimal` from
`decimal.js` (no precision problem to solve for integers — would be cargo-cult).

## 6. Proportional bars, made non-announcing (FR-008, FR-022, FR-023)

**Decision**: Each breakdown row renders `code` and formatted `count` as real text nodes, plus a
purely visual bar: a track `div` with an inner fill whose width comes from
`[style.width.%]="row.proportionPercent"`, and the whole bar wrapper carries `aria-hidden="true"`.
`proportionPercent` is computed as `count / maxDisplayedCount * 100` (rounded to 2 dp), where
`maxDisplayedCount` is the **highest count among the displayed rows** (FR-008), with a minimum
rendered width floor so a tiny-but-nonzero count is still visible.

**Rationale**: FR-023 wants the bar heard exactly zero times and FR-022 wants every value
available as text — `aria-hidden` on the graphic plus text siblings is the standard pairing, and
it needs no ARIA role, no `progressbar`, no duplicated `aria-label`. Percentage width from a style
binding is the one place inline style is right: it is data, not styling. Rounding to 2 dp keeps
repeat renders identical (SC-006). All-tied counts and the single-row case then both fall out
naturally as 100%-width bars (spec edge cases).

**Alternatives considered**:

- *`role="progressbar"` + `aria-valuenow`* — rejected: announces the value a second time, exactly
  what FR-023 forbids, and misrepresents a static statistic as a progress indicator.
- *Inline SVG chart / Chart.js* — rejected: Chart.js is a canvas renderer whose content is
  invisible to assistive tech and would need a parallel text table anyway; the spec asks for
  ranked rows (label + bar + number), which is a CSS-width list, not a chart engine. Keeps the
  page dependency-free.
- *CSS `width: calc()` from a CSS custom property* — equivalent; a direct `[style.width.%]`
  binding is fewer moving parts and reads better in tests.

## 7. Section headings and heading order (FR-024, FR-026)

**Decision**: `<h1>` page title once, then `<h2>` for each of the three sections — "Summary",
"Activity breakdown", "Recent activity" — with each section a `<section>` element referencing its
heading via `aria-labelledby`. The KPI row's `<h2>` is visually present (small eyebrow-style
label) rather than visually hidden. The page ships no interactive controls at all, so nothing can
trap focus (FR-026).

**Rationale**: Real headings in DOM order give heading-based navigation for free and keep the
visual hierarchy the spec asks for (US4) identical to the accessibility tree — no `sr-only`
divergence to maintain. Reading order in the DOM is KPI → breakdown → recent activity, which is
also the required narrow-viewport stack order (FR-017), so the responsive layout needs no
`order:` overrides that would desync visual and DOM order.

**Alternatives considered**: `aria-label` on the sections instead of headings (rejected: not
reachable by heading navigation, which FR-024 explicitly asks for); a visually hidden KPI heading
(rejected: the row benefits from a visible label anyway, one less hidden-text mechanism).

## 8. Layout technique and visual language (FR-016, FR-017, FR-018)

**Decision**: A scoped component stylesheet per component (`usage-analytics.css` plus one small
stylesheet each for the two panel components), reusing the same design-token block
(`--surface`, `--border`, `--muted`, `--accent`, …) already established in
`features/historical-rates/historical-rates.css` and `features/rate-lookup/rate-lookup.css`.
Layout: CSS Grid — `repeat(3, 1fr)` for the KPI row, and `grid-template-columns: minmax(0, 1.6fr)
minmax(0, 1fr)` for the breakdown/recent split (left visibly wider, FR-016), collapsing to a
single column at the same `900px`/`640px` breakpoints those existing pages already use (FR-017).
Page container `max-width: 1180px; margin: 0 auto` — the same value `rate-lookup.css` uses
(FR-018 "consistent with the application's other pages").

**Rationale**: This is a deliberate, documented departure from `CLAUDE.md`'s "Frontend styling:
Tailwind CSS" default, and it follows the precedent set by the two most recently shipped views
(012 rate calculator, 013/014 historical rates), both of which are scoped-CSS with this token set.
Matching them is what makes this page read as the same product rather than a third visual dialect;
mixing a Tailwind-utility dashboard beside two token-CSS dashboards would guarantee drift in
border color, radius, spacing scale, and numeric font treatment. `minmax(0, …)` fractions prevent
the long-content overflow that plain `1.6fr 1fr` allows, which is what SC-005 (no horizontal
scroll, 320–2560 px) actually turns on. Tailwind stays available and is still used by the app
shell; nothing is removed.

**Alternatives considered**:

- *Pure Tailwind utilities* — closer to `CLAUDE.md`'s letter, rejected for the visual-drift reason
  above; recorded in plan.md → Complexity Tracking so the deviation is explicit rather than
  accidental.
- *Flexbox two-column* — rejected: needs `min-width: 0` plus `flex-basis` arithmetic to get the
  same asymmetric, overflow-safe split Grid expresses in one declaration.
- *Restyling the other pages to Tailwind first* — rejected: out of scope for this feature.

## 9. Component decomposition and test strategy

**Decision**: Derivations live in a dependency-free module (`usage-metrics.ts`) exporting pure
functions over `readonly CurrencyUsageEntry[]`; presentation splits into the page component
(`usage-analytics.ts`: header, KPI row, grid, load/error/empty orchestration) and two dumb,
signal-input panels (`usage-breakdown-panel.ts`, `recent-activity-panel.ts`). Tests: Vitest unit
specs for `usage-metrics.ts` and the relative-time helper (every FR threshold, tie-break, skew,
empty and single-row cases), plus a component spec for `usage-analytics.ts` following the
established pattern in `features/historical-rates/historical-rates.spec.ts` — `TestBed` with the
generated service replaced by `{ getUsageAnalytics: vi.fn() }` returning `of(...)`,
`throwError(...)`, or a never-completing `Subject` for the timeout path (driven with Vitest fake
timers).

**Rationale**: The interesting logic here is entirely data derivation (ranking, tie-breaks, caps,
never-queried count, elapsed phrasing) — pure functions make each acceptance scenario a one-line
assertion with no DOM or HTTP setup, which is how 013's `trend-metrics.spec.ts` /
`period-presets.spec.ts` are already organized. Panels taking plain `input()` values keep the
component spec focused on the four page-level states (loading / error / empty / populated) that
SC-004 enumerates. Stubbing the generated service (rather than `HttpTestingController`) matches
the existing specs in this repo and keeps the tests independent of the generated client's
internals.

**Alternatives considered**: one monolithic component with derivations inline (rejected: every
acceptance scenario would need a rendered fixture); `HttpTestingController` (rejected: inconsistent
with the repo's existing frontend specs, and it tests the generated client, not this feature);
Testcontainers/backend integration tests (not applicable — Constitution X binds DB-dependent
tests; this feature touches no database).
