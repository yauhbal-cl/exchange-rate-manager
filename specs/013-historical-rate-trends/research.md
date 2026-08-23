# Research: Historical Exchange Rate Trends

No Technical Context fields were marked `NEEDS CLARIFICATION` — the stack, both backend
endpoints, and the response schemas are all already fixed by the existing scaffold
(`/exchange/trend`, `/exchange/trend/insight`, both live since specs 005/006). Research below
resolves the open *design* decisions needed to build the view correctly, given `spec.md`'s two
resolved clarifications (explicit-only AI generation; 6-month custom-range cap) and the detailed
UI brief supplied to this planning command.

## 1. Charting library

**Decision**: Chart.js (~4.5.x), used directly (no `ng2-charts` wrapper), wrapped in a small
`RateTrendChart` standalone component that owns a `<canvas>`, creates the `Chart` instance in an
`effect()`/`afterRenderEffect` keyed on the input data signal, and calls `chart.update()` on
change / `chart.destroy()` on component destroy.

**Rationale**:
- It's the smallest well-maintained library that gives tooltips, light grid lines, and a
  non-zero-forced y-axis out of the box — exactly the brief's requirements — without hand-rolling
  SVG/canvas drawing.
- Canvas-based and framework-agnostic: no `NgZone`/change-detection coupling, so it composes
  cleanly with Angular 21's zoneless default (CLAUDE.md: "zoneless by default") — the component
  just needs to imperatively call `update()`/`destroy()` from a signal-driven effect.
- Easy to restrain visually (thin lines, muted grid, no default "dashboard" chrome) — the brief
  explicitly warns against a "high-frequency trading terminal" look.

**Alternatives considered**:
- `ng2-charts` (Chart.js Angular wrapper) — rejected: adds an extra abstraction layer and its own
  change-detection assumptions for no benefit over calling Chart.js directly from a signal effect,
  which is the more idiomatic Angular-21-signals approach CLAUDE.md points at.
- `lightweight-charts` (TradingView) — rejected: purpose-built for candlestick/trading-terminal
  visuals, which is precisely the aesthetic the brief asks to avoid; fighting its defaults costs
  more than it saves for a single line series.
- `ngx-charts` — rejected: D3-based, heavier, and its declarative-template API is a worse fit for
  imperative tooltip/annotation control than Chart.js's plugin/options object.
- Raw D3 / hand-rolled SVG — rejected: reimplements tooltip, grid, and axis-label logic Chart.js
  already provides; more code to maintain for no requirement Chart.js can't satisfy.

## 2. Client-side arithmetic for derived metrics vs. Constitution I

**Decision**: `trend-metrics.ts` exposes pure functions that take the raw `RateTrendPoint[]`
(each `rate` a decimal string) and perform all comparison/arithmetic (max/min, subtraction,
percent-change division) via `Decimal` from `decimal.js` (new dependency), never `parseFloat`/JS
`number`. Each derived figure is returned as `{ display: string, value: Decimal }` — latest rate,
period change (absolute + %), period high/low (value + date), daily change % per row. `display`
is always either the verbatim source string (for latest/high/low headline values) or a
`Decimal`-formatted string derived from `value` purely for presentation (percentages, rounded to
a fixed number of decimals). Neither `value` nor any reformatted number is ever sent back to the
API or persisted.

**Rationale**: Constitution I ("Monetary Precision") requires BigDecimal-equivalent precision
"wherever a rate is stored, **computed**, or serialized," with no display-only carve-out in its
text. Unlike 012 (zero arithmetic on rate values — pure passthrough render), 013 performs real
computation on rate values (subtraction, division, min/max), so it falls squarely under this
MUST rather than being a "frontend analogue" exception. `decimal.js` gives the same
arbitrary-precision guarantee here as `BigDecimal` does server-side, so the principle is upheld
unamended rather than diluted.

**Alternatives considered**:
- Hand-rolled BigInt-scaled integer arithmetic — rejected: reimplements what `decimal.js` already
  provides correctly, more surface area to get wrong for no dependency savings that matters here.
- Plain JS `number`/`parseFloat` (the previous decision) — rejected: conflicts with Constitution
  I's unqualified MUST; "display-only, ephemeral" is not a stated exception in the constitution,
  and diluting a MUST via plan-level reasoning rather than a constitution amendment is out of
  scope for a feature plan.

## 3. Reactive trend/table/metrics vs. explicit-submit AI insight — two independent resources

**Decision**: Two separate `rxResource`s, not one:
- `trend = rxResource({ params: () => pairAndRangeSignal(), stream: ({params}) => ... })` — fires
  automatically whenever the resolved `{from, to, startDate, endDate}` changes (base/quote
  selection, preset pick, custom range, or swap). `pairAndRangeSignal()` returns `undefined`
  (skipping the request) when `from === to` or either is unselected, per FR-002.
- `aiInsight = rxResource({ params: () => aiRequestSignal(), stream: ({params}) => ... })` where
  `aiRequestSignal` is a plain `signal<{from,to,startDate,endDate} | undefined>(undefined)`,
  written only by the "Generate insight" click handler (mirrors 012's `submittedRequest` gating
  pattern) — and reset to `undefined` by an `effect()` that watches `pairAndRangeSignal()` and
  clears it on every change, including a swap.

**Rationale**: The two have opposite triggering rules by spec — trend/metrics/chart/table must
update automatically on any filter change (User Stories 1–3's acceptance scenarios), while AI
insight must *never* auto-fire and must go stale on the same changes (FR-011, FR-014). Modeling
them as one resource would force one of the two behaviors to be faked with extra state; two
independently-keyed resources make each rule structural rather than conventional, the same
justification 012 used for isolating `submittedRequest` from raw form signals.

**Alternatives considered**: One resource with a "mode" flag distinguishing auto vs. manual
triggers — rejected, `rxResource` has no such mode and building one manually reinvents what two
resources already give for free (independent loading/error/value signals, independent
stale-response discarding).

## 4. Preset vs. custom range resolution

**Decision**: `period-presets.ts` defines the five presets as `{ id, label, unit, amount }`:
`7D` = `{unit:'days', amount:7}`, `15D` = `{unit:'days', amount:15}`, `1M` = `{unit:'months',
amount:1}`, `3M` = `{unit:'months', amount:3}`, `6M` = `{unit:'months', amount:6}`. Day-based
presets subtract calendar days from today; month-based presets subtract calendar months via a
shared `subtractMonths(date, n)` helper (same day-of-month, clamped to the shorter month where
needed, e.g. Aug 31 − 1 month = Jul 31). That same `subtractMonths` helper also backs the FR-006
6-month custom-range cap check (§8) — the `6M` preset and the custom-range cap boundary are
computed by the same function, never two independently-approximated "6 months." `resolveRange
(selection, today) -> { startDate, endDate }` computes `startDate`/`endDate` as `yyyy-MM-dd`
strings using this shared helper (matching `rate-lookup`'s existing `todayIso()` string-based
approach, not `Date`-object timezone-sensitive math). A `PeriodSelection` signal holds either
`{ kind: 'preset', id }` or `{ kind: 'custom', startDate, endDate }`; picking a preset overwrites
it wholesale (no partial custom/preset mixing), and vice versa.

**Rationale**: Presets are literally "N days/months back from today," which the backend's own
default (`/exchange/trend`'s `startDate` defaults to 29 days before today) confirms is the
expected client-side shape — resolving them client-side to concrete dates keeps the request shape
uniform (`from,to,startDate,endDate`) whether the source was a preset or a custom range, so
`trend`'s `stream` function has exactly one call shape to support.

**Alternatives considered**:
- Sending the preset id to the backend and letting it resolve the range — rejected,
  `/exchange/trend` has no such parameter today and adding one would be a contract change out of
  scope for a frontend-only feature.
- A fixed day-count for the month-based presets (e.g. `1M` = 30 days) — rejected: would silently
  diverge from the FR-006 cap's calendar-month definition and from user expectation that "1M
  back" means "this date last month," not an arbitrary 30-day approximation.

## 5. Reusing `rate-lookup`'s currency list/combobox

**Decision**: Import `CURRENCIES`/`Currency` from `../rate-lookup/currencies` and the
`CurrencyCombobox` component from `../rate-lookup/currency-combobox` directly via relative path,
rather than moving either into a new `shared/` folder.

**Rationale**: There is exactly one other consumer today (`rate-lookup` itself); introducing a
new top-level `shared/` structural convention for a single reused widget is exactly the kind of
premature abstraction CLAUDE.md and repo convention warn against ("three similar lines is better
than a premature abstraction"). A direct cross-feature import is a smaller, fully reversible
change — if a third feature needs the same list/component later, that's the point to extract a
shared module, not before.

**Alternatives considered**: Duplicating a second `currencies.ts`/combobox inside
`historical-rates/` — rejected, direct duplication of a ~170-line list and a stateful combobox
component for no behavioral difference. Extracting `shared/currency/` now — rejected as premature
per above; revisit if a third consumer appears.

## 6. Chart x-axis: category scale, not a time scale

**Decision**: Use Chart.js's default `category` x-axis (point index → date-string label), not the
`time` scale (which would require adding `chartjs-adapter-date-fns` or similar). Label density is
controlled via `ticks.autoSkip` + `ticks.maxRotation`/`maxTicksLimit`, tuned by point count (e.g.
show every label for ≤15 points, thin to ~8 evenly-spaced labels for longer ranges), satisfying
the brief's "adapt date-label granularity based on the selected period."

**Rationale**: `spec.md`'s Edge Cases explicitly say missing dates (weekends, pre-history gaps)
are "simply absent from chart/table, not zero-filled" — i.e., the x-axis is semantically an
ordered sequence of *observed* dates, not a continuous calendar timeline where gaps should
visually stretch. A category scale matches that semantics exactly and avoids a new dependency; a
time scale would need explicit gap-handling configuration to avoid implying data exists on
skipped calendar days.

**Alternatives considered**: `time` scale with `chartjs-adapter-date-fns` — rejected, adds a
dependency to solve a problem (true calendar-time spacing) the spec's edge cases say is not
wanted.

## 7. Chart annotations — scoped down from the UI brief's examples

**Decision**: Implement only **period-high** and **period-low** markers on the chart (a styled
point + label at the two dates already computed for the summary row). The brief's other listed
examples — "largest daily move," "increased-volatility period," "significant trend reversal" —
are explicitly framed as "examples of possible annotations," and `spec.md` has no functional
requirement or success criterion for any heuristic trend/volatility detection. Building those
would be new, unscoped analysis logic invented beyond both documents.

**Rationale**: High/low annotations reuse metrics FR-007 already requires computing and displayed
in the summary row — near-zero incremental cost, directly traceable to a requirement. The other
three require inventing thresholds/heuristics (what counts as "largest," "volatility," or
"reversal") with no acceptance criteria to validate them against — exactly the
overbuilding/hypothetical-requirement risk CLAUDE.md's conventions call out.

**Alternatives considered**: Implementing all five annotation types — rejected as unscoped scope
creep; can be proposed as a follow-up feature with its own spec/acceptance criteria if wanted.

## 8. Date input validation (custom range)

**Decision**: Compare `yyyy-MM-dd` strings lexicographically for the start-after-end check; for
the span check, reuse §4's `subtractMonths(endDate, 6)` helper and compare `startDate` against it
lexicographically — not an independent day-count approximation — rather than relying on
`<input type="date">`'s native `min`/`max` alone, since both bounds here are dynamic (end can't
precede start; span can't exceed 6 months) rather than a single fixed "not in the future" bound.
Sharing `subtractMonths` with the `6M` preset (§4) guarantees the custom-range cap and the `6M`
preset agree on exactly the same "6 months" boundary for any given `today`.

**Rationale**: Native `min`/`max` attributes only constrain one static bound each; the 6-month
span check inherently needs both values compared together, which has to happen in a `computed()`
regardless, so validation is centralized there rather than split across native attributes and
script.

**Alternatives considered**: Relying solely on native date-input constraints — rejected,
insufficient for a cross-field (span) rule.
