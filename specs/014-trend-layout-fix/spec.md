# Feature Specification: Historical Trends Full-Width Chart & AI Insights Layout

**Feature Branch**: `014-trend-layout-fix`

**Created**: 2026-08-23

**Status**: Draft

**Input**: User description: "Fix for 013-historical-rate-trends. Chart should be historical-rates container wide. Ai Insights should be under the chart but ontop of rates table, and also should be historical-rates container wide"

## User Scenarios & Testing *(mandatory)*

### User Story 1 - See the trend chart at full container width (Priority: P1)

A user viewing the Historical Exchange Rate Trends page sees the trend chart rendered across the
full width of the page's content container, matching the width of the currency selectors, period
controls, and metrics above it — not squeezed into a partial-width column beside another element.

**Why this priority**: The chart is the primary visual artifact of this view. A narrower chart
makes trends harder to read and looks inconsistent with the rest of the page, which already spans
the full container width.

**Independent Test**: Open the view with a pair/period that has historical data and verify the
chart's rendered width matches the width of the metrics row and the raw data table below it, at
both narrow and wide viewport sizes.

**Acceptance Scenarios**:

1. **Given** a currency pair and period with historical data, **When** the view renders, **Then**
   the trend chart spans the same width as the historical-rates container (matching the width of
   the metrics section and the table), rather than sharing a row with another element.
2. **Given** the browser viewport is resized from narrow to wide, **When** the chart re-renders,
   **Then** it continues to span the full container width at every size, not just on small
   viewports.

---

### User Story 2 - Find AI Insights stacked between the chart and the data table (Priority: P1)

A user scanning the page top to bottom finds the AI Insights section directly below the trend
chart and directly above the raw historical rates table, occupying the same full container width
as the chart and the table — instead of sitting beside the chart in a narrow side column.

**Why this priority**: The AI interpretation is meant to be read as commentary on the chart
immediately above it before the user drops into the raw numbers. A side-column placement breaks
that reading order and gives the interpretation too little width to display comfortably.

**Independent Test**: With a pair/period that has historical data, request an AI insight and
verify it renders full-width, positioned after the chart and before the table in the page's
vertical flow, at both narrow and wide viewport sizes.

**Acceptance Scenarios**:

1. **Given** the trend chart and raw data table are both visible, **When** the AI Insights section
   renders (whether idle, loading, showing a result, or showing an error), **Then** it appears
   directly below the chart and directly above the table.
2. **Given** the browser viewport is resized from narrow to wide, **When** the AI Insights section
   re-renders, **Then** it continues to span the full container width and remain stacked between
   the chart and the table at every size, never reverting to a side-by-side column layout.

---

### Edge Cases

- What happens when the AI Insights section is showing a long narrative, a loading state, or an
  error message? It still renders full width in its stacked position; its content does not cause
  it to shrink into a side column or push the table above it.
- What happens on very wide viewports? The chart and AI Insights section both continue to span the
  full historical-rates container width (the same width the page already caps its content at),
  rather than growing without bound or reverting to a multi-column split.
- What happens on very narrow (mobile) viewports? The chart and AI Insights section already stack
  full-width in the current layout at narrow sizes; this fix must not regress that existing
  behavior.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: The trend chart MUST render at the full width of the historical-rates container, the
  same width as the currency/period controls, the metrics row, and the raw data table, at every
  supported viewport width.
- **FR-002**: The AI Insights section MUST render at the full width of the historical-rates
  container, the same width as the trend chart and the raw data table, at every supported
  viewport width.
- **FR-003**: The AI Insights section MUST appear directly after the trend chart and directly
  before the raw historical rates table in the page's vertical layout, at every supported
  viewport width.
- **FR-004**: The layout MUST NOT place the trend chart and the AI Insights section side by side
  in a shared row at any supported viewport width.
- **FR-005**: The existing information order established for this view (currency pair, period,
  key metrics, trend chart, AI interpretation, raw historical data) MUST be preserved; this fix
  changes width and stacking only, not the order of sections.
- **FR-006**: This fix MUST NOT change the existing behavior of the chart, the AI Insights
  section, or the table (data shown, loading/error/empty states, the explicit "generate insight"
  action) — only their width and relative stacking change.

### Key Entities

- **Trend Chart**: The line-chart visualization of historical rate points for the selected pair
  and period; existing content and behavior unchanged by this fix.
- **AI Insights Section**: The panel showing the AI-generated interpretation (idle/loading/result/
  error states) for the selected pair and period; existing content and behavior unchanged by this
  fix.
- **Historical Rates Container**: The page-level content area that already bounds the width of the
  currency/period controls, metrics row, and raw data table; the reference width the chart and AI
  Insights section must now match.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: The trend chart's rendered width matches the historical-rates container's width
  (equivalently, the width of the metrics row and the table) 100% of the time, across viewport
  sizes.
- **SC-002**: The AI Insights section's rendered width matches the historical-rates container's
  width 100% of the time, across viewport sizes.
- **SC-003**: The AI Insights section appears between the trend chart and the raw data table, in
  that order, 100% of the time, across all of its states (idle, loading, result, error) and
  viewport sizes.
- **SC-004**: All previously passing behavior for the chart, AI Insights section, and table
  (existing automated tests for data display, loading/error/empty states, and the generate-insight
  action) continues to pass unchanged.

## Assumptions

- This is a presentation-only fix to the view delivered by 013-historical-rate-trends; no backend
  contract, API shape, or data model changes are needed.
- "historical-rates container wide" means the same content width already used by the page's other
  full-width sections (header, controls, metrics, table) — i.e., the page's existing max-width
  content area, not the full browser viewport.
- The current side-by-side (chart + AI Insights) column split only appears at wider viewport
  breakpoints; narrower viewports already stack these sections full-width. This fix makes the
  full-width stacked arrangement the only arrangement, at all viewport widths.
- No new user-facing states are introduced by this fix; all existing states (no-data, validation
  errors, AI unavailable, etc.) keep their current appearance, just at the new width/position.
