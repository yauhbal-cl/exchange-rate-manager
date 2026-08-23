# Feature Specification: Historical Exchange Rate Trends

**Feature Branch**: `013-historical-rate-trends`

**Created**: 2026-08-23

**Status**: Draft

**Input**: User description: "Design and implement a Historical Exchange Rates & Trends experience for a financial or currency platform. The goal is to help users understand how a selected currency pair has moved over a chosen period of time, while giving them access to both the underlying data and AI-generated interpretation. Users should be able to: select a base currency and quote currency (e.g. USD/EUR); quickly swap the selected currencies; select a custom date range or use presets (7D, 15D, 1M, 3M, 6M); see the historical exchange-rate movement as a line chart; review the corresponding raw historical rates in a table. The experience should also include an AI Insights capability. Layout order: Currency pair → selected period → key metrics → trend → AI interpretation → raw historical data."

## Clarifications

### Session 2026-08-23

- Q: Should the AI interpretation regenerate automatically whenever the user changes currency pair or period, or only on explicit user action? → A: Explicit action only (e.g. a "Generate insight" button) — avoids a slow AI call on every filter change.
- Q: Should the custom date range have a maximum span, and if so what? → A: Cap at 6 months, matching the longest preset (7D/15D/1M/3M/6M), keeping custom ranges consistent with presets.

## User Scenarios & Testing *(mandatory)*

### User Story 1 - View trend and key metrics for a currency pair (Priority: P1)

A user picks a base currency and a quote currency and sees, for the currently selected period, a
summary of key metrics (latest rate, how much it moved over the period, period high/low) followed
by a line chart of the historical rate movement.

**Why this priority**: This is the core value of the feature — turning stored historical rates
into an at-a-glance understanding of how a pair has moved. Without it there is no feature.

**Independent Test**: Pick a currency pair known to have historical data for the default period,
and verify the key metrics and line chart render and are consistent with each other (e.g. the
chart's endpoints match the reported latest rate and period change).

**Acceptance Scenarios**:

1. **Given** a base and quote currency with historical data for the default period, **When** the
   user opens the view, **Then** the view shows key metrics (latest rate, period change, period
   high, period low) and a line chart of the rate over that period, in that order.
2. **Given** a currency pair with no historical data for the selected period, **When** the view
   loads, **Then** the view shows an explicit "no data" state for metrics and chart instead of a
   blank screen, an error page, or fabricated values.
3. **Given** the view is already showing a pair/period, **When** the user changes the base or
   quote currency, **Then** the metrics and chart update to reflect the newly selected pair for
   the same period.

---

### User Story 2 - Choose the period via presets or a custom range (Priority: P2)

A user narrows or widens the window being analyzed by picking one of the preset periods (7D, 15D,
1M, 3M, 6M) or specifying a custom start/end date range.

**Why this priority**: The trend view is only useful if the user can control the time window; this
directly extends User Story 1's value across different timeframes.

**Independent Test**: With historical data spanning several months for a pair, select each preset
in turn and verify the chart/table/metrics window changes accordingly; then pick a custom range
and verify the same.

**Acceptance Scenarios**:

1. **Given** a currency pair is selected, **When** the user picks a preset (7D, 15D, 1M, 3M, or
   6M), **Then** the metrics, chart, and table all reflect exactly that trailing window ending on
   the most recent available date.
2. **Given** a currency pair is selected, **When** the user enters a custom start and end date no
   more than 6 months apart, **Then** the metrics, chart, and table reflect that exact range.
3. **Given** a currency pair is selected, **When** the user enters a custom range spanning more
   than 6 months, **Then** the system shows a clear validation message and does not submit the
   request.
4. **Given** a custom range where the start date is after the end date, **When** the user submits
   it, **Then** the system shows a clear validation message and does not submit the request.

---

### User Story 3 - Quickly swap base and quote currencies (Priority: P2)

A user viewing USD/EUR wants to see EUR/USD instead, without re-picking both currencies from
scratch.

**Why this priority**: A small but frequently-used convenience for comparing a pair from either
direction; independent of, but complements, User Stories 1 and 2.

**Independent Test**: With a pair selected and a trend already showing, trigger the swap action
and verify the base and quote currencies are exchanged and the view refreshes for the new pair,
keeping the currently selected period.

**Acceptance Scenarios**:

1. **Given** a base and quote currency are selected, **When** the user triggers the swap action,
   **Then** the former quote currency becomes the base and the former base becomes the quote, and
   the metrics/chart/table refresh for the swapped pair using the same period.
2. **Given** an AI interpretation was showing for the pre-swap pair, **When** the user swaps
   currencies, **Then** the stale interpretation is cleared rather than left displayed against the
   new pair.

---

### User Story 4 - Review raw historical rates in a table (Priority: P3)

A user wants to see the exact underlying values behind the chart — one row per date with its
rate — rather than only the visual trend.

**Why this priority**: Supports users who need precise figures (e.g. for record-keeping) in
addition to the visual summary; the view is still useful without it via the chart alone.

**Independent Test**: With a pair/period selected that has historical data, verify the table lists
one row per date present in the chart, each showing the date and its rate.

**Acceptance Scenarios**:

1. **Given** a pair/period with historical data, **When** the view loads, **Then** the table shows
   one row per date with stored data in the selected range, each with its date and rate value.
2. **Given** a pair/period with no historical data, **When** the view loads, **Then** the table
   shows an explicit empty state consistent with the chart's "no data" state.
3. **Given** the chart is showing N points for the current pair/period, **When** the table is
   inspected, **Then** it shows exactly those same N dates and rates.

---

### User Story 5 - Request an AI-generated interpretation of the trend (Priority: P3)

A user viewing a trend wants a short, plain-language interpretation of what the chart shows
(direction, notable moves) without having to read the raw numbers themselves, and requests it
explicitly.

**Why this priority**: Adds interpretive value on top of the raw chart/table, but the view already
delivers its core value (Stories 1-4) without it.

**Independent Test**: With a pair/period that has historical data, trigger the "generate insight"
action and verify a short narrative appears that is grounded in the currently displayed data.

**Acceptance Scenarios**:

1. **Given** a pair/period with historical data is displayed, **When** the user requests an AI
   interpretation, **Then** the system shows a short narrative describing the trend grounded in
   the data currently on screen.
2. **Given** the AI service is unavailable or times out, **When** the user requests an
   interpretation, **Then** the system shows a clear "interpretation unavailable" message rather
   than a fabricated narrative or a raw technical error.
3. **Given** a pair/period with no historical data, **When** the user attempts to request an
   interpretation, **Then** the action is unavailable or clearly indicates there is nothing to
   interpret, rather than calling the AI with empty data.
4. **Given** an interpretation is currently displayed, **When** the user changes the currency pair
   or period, **Then** the previous interpretation is cleared/marked stale and a new one is only
   shown after the user explicitly requests it again for the new selection.

---

### Edge Cases

- What happens when the selected currency pair has only one or two data points in the period
  (e.g. a very recently added currency)? Chart and table still render with the available point(s);
  metrics that require a range (e.g. high/low) are computed from whatever points exist, with no
  interpolation or fabricated points.
- How does the system handle a user selecting the same currency for both base and quote? The
  selection is prevented/rejected with a clear message, consistent with existing currency-pair
  selection behavior elsewhere in the platform.
- How does the system handle a custom range where neither date has any stored data at all (e.g. a
  future date range)? Treated the same as the "no data" state in User Stories 1 and 4.
- What happens if the user swaps currencies while an AI interpretation request is in flight? The
  in-flight request's result is discarded/ignored when it returns, since it no longer matches the
  now-selected pair.
- What happens when a preset's trailing window includes dates before the platform has any rate
  history at all? Only the sub-range with actual stored data is shown; missing leading dates are
  simply absent from chart/table, not zero-filled.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: Users MUST be able to select a base currency and a quote currency from the
  platform's supported currencies to form a currency pair.
- **FR-002**: System MUST prevent selecting the same currency for both base and quote, with a
  clear validation message.
- **FR-003**: Users MUST be able to swap the base and quote currency selections in a single
  action, which refreshes all displayed data (metrics, chart, table) for the swapped pair using
  the currently selected period.
- **FR-004**: Users MUST be able to select the analysis period via presets: 7D, 15D, 1M, 3M, or
  6M, each meaning a trailing window of that length ending on the most recent available date.
- **FR-005**: Users MUST be able to select the analysis period via a custom start and end date, as
  an alternative to presets.
- **FR-006**: System MUST reject a custom date range longer than 6 months or with a start date
  after the end date, with a clear validation message, and MUST NOT submit such a request.
- **FR-007**: System MUST display key summary metrics for the currently selected pair and period:
  latest rate, change over the period (absolute and percentage), period high, and period low.
- **FR-008**: System MUST display a line chart of the historical rate movement for the currently
  selected pair and period, ordered chronologically from oldest to newest.
- **FR-009**: System MUST display the raw historical rates for the currently selected pair and
  period in a table, with each row showing a date and its corresponding rate, covering the exact
  same set of dates shown in the chart.
- **FR-010**: System MUST present the view's information in this order: currency pair selection,
  then period selection, then key metrics, then the trend chart, then the AI interpretation
  (control and result), then the raw historical data table.
- **FR-011**: Users MUST be able to explicitly request an AI-generated interpretation of the
  currently displayed trend; the system MUST NOT generate or refresh an interpretation
  automatically when the pair or period changes.
- **FR-012**: The AI-generated interpretation MUST be grounded in the exact historical data
  currently displayed for the selected pair and period (the real dates/values), not a generic or
  fabricated summary.
- **FR-013**: When the AI interpretation cannot be generated (service unavailable, timeout, or no
  underlying data for the selection), the system MUST show a clear, explicit message stating that,
  rather than fabricating a narrative or showing a raw technical error.
- **FR-014**: When the currency pair or period changes, or a currency swap occurs, any previously
  displayed AI interpretation MUST be cleared or marked stale, requiring a new explicit request to
  regenerate it for the new selection.
- **FR-015**: When there is no historical data for the selected pair and period, the system MUST
  show an explicit "no data" state for the metrics, chart, and table, rather than a blank view, a
  crash, or fabricated values.

### Key Entities

- **Currency Pair**: A base currency and a quote currency selected by the user; defines which
  historical rates are being analyzed. Swappable as a unit.
- **Period Selection**: Either a named preset (7D, 15D, 1M, 3M, 6M) or a custom start/end date
  range (capped at 6 months), defining the window of historical data shown.
- **Historical Rate Point**: A single date and its exchange rate for the selected currency pair;
  the building block of both the chart and the raw data table.
- **Trend Metrics**: Derived summary values computed from the set of historical rate points in the
  selected period — latest rate, period change (absolute and percentage), period high, period low.
- **AI Interpretation**: A short, plain-language narrative describing the trend, generated on
  explicit user request and grounded in the historical rate points currently displayed; may be
  absent/stale/unavailable depending on user action and AI service state.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: A user can go from choosing a currency pair to seeing its key metrics and trend
  chart for any preset period in a single interaction per selection (no more than one action to
  pick base currency, one to pick quote currency, one to pick a preset).
- **SC-002**: Swapping the currency pair is a single action and the metrics, chart, and table
  reflect the swapped pair without the user needing to reselect the period.
- **SC-003**: 100% of the dates/values shown in the raw historical data table for a given
  pair/period exactly match the points plotted on the chart for that same pair/period.
- **SC-004**: Users attempting a custom range longer than 6 months are stopped with a clear
  explanation before any data request is made, 100% of the time.
- **SC-005**: When the AI interpretation cannot be produced (service down, timeout, or no data),
  100% of such attempts result in a clear explanatory message rather than an indefinite wait, a
  crash, or a fabricated narrative.
- **SC-006**: Changing the currency pair or period after an AI interpretation was shown never
  leaves a stale interpretation visibly attached to the new selection — it is cleared or clearly
  marked outdated 100% of the time.

## Assumptions

- The set of selectable currencies is the platform's existing list of supported currencies (the
  same list used elsewhere in the platform for currency-pair selection), not a new/separate list
  introduced by this feature.
- The default period shown when the view is first opened is the 1M preset, and a commonly-used
  default currency pair is pre-selected if the user has not chosen one yet.
- The raw historical data table lists rows in the same chronological order as the chart (oldest to
  newest); no separate sort/filter controls are required for an initial version.
- AI interpretation requests reuse the same "no fabrication, honest unavailability" behavior
  already established for AI-generated trend commentary elsewhere on the platform.
- This feature does not introduce new authentication/authorization requirements; it inherits
  whatever access model the rest of the platform currently uses.
- "Period high/period low" metrics refer to the highest and lowest rate values among the
  historical rate points within the selected period, not intraday extremes (the platform stores
  one rate per currency per date).
