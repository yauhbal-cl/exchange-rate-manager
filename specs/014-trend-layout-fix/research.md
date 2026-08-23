# Phase 0 Research: Historical Trends Full-Width Chart & AI Insights Layout

No `NEEDS CLARIFICATION` markers remain in the Technical Context — this fix is small and
self-contained enough that no open unknowns needed research. This document records the one real
design decision so the rationale is not lost between planning and implementation.

## Decision: Replace the 3-column grid split with a single-column stack

**Decision**: In `historical-rates.ts`, remove the `grid grid-cols-1 gap-4 lg:grid-cols-3` wrapper
(with its `lg:col-span-2` chart cell and `lg:col-span-1` AI Insights cell) and replace it with
three sequential full-width blocks in this order: trend chart, AI Insights panel, raw data table —
matching the block that already renders the table below (a plain `<div class="mt-6">` with no
column constraint).

**Rationale**:
- The spec (FR-001–FR-004) requires the chart and AI Insights panel to each span the full
  `historical-rates` container width and to never sit side by side, at any viewport width — the
  existing `lg:` breakpoint override is exactly the thing being removed, not adjusted.
- The raw data table already renders full-width with no column wrapper; matching that same
  pattern for the chart and AI Insights panel keeps the fix minimal and consistent with the page's
  existing convention, rather than inventing a new layout primitive.
- A CSS/template-only change (no new inputs/outputs on `RateTrendChart`, `AiInsightsPanel`, or
  `HistoricalRatesTable`) keeps the fix isolated to one file, satisfying the "presentation-only"
  assumption in spec.md and avoiding any risk to the components' existing behavior/tests.

**Alternatives considered**:
- *Keep the grid but change `lg:col-span-2`/`lg:col-span-1` to both be `lg:col-span-3`*: would
  still leave the two elements as separate grid items in the same row conceptually and is more
  fragile than simply removing the grid — rejected in favor of the simpler stacked-`div` approach
  already used for the table.
- *Introduce a shared "full-width section" wrapper component*: unnecessary abstraction for three
  call sites in one template; rejected per the project's no-premature-abstraction convention.
- *Only fix widths at the `lg` breakpoint and leave smaller breakpoints as-is*: rejected because
  the spec explicitly requires the full-width stacked arrangement at every viewport width (FR-001–
  FR-004), and the current smaller-breakpoint behavior already happens to satisfy that, so no
  breakpoint-conditional logic is needed at all — one unconditional stack covers every width.
