# Feature Specification: Usage Analytics Dashboard

**Feature Branch**: `015-usage-analytics-dashboard`

**Created**: 2026-08-23

**Status**: Draft

**Input**: User description: "create A clean analytics dashboard with a clear title and short subtitle at the top, explaining that the page provides an overview of query activity. The main content should include: Summary metrics: A row of three KPI cards showing the total number of queries, the number of unique categories or items queried, and the most frequently queried category together with its query count. Activity breakdown: A larger left-hand panel containing a horizontal bar chart that ranks the most queried currencies. Each row should include a category label, a proportional bar, and the corresponding number of queries. Recent activity: A narrower right-hand panel showing the latest query events in a simple list. Each entry should contain a category or item code and the time when the query occurred. The layout should use a clear visual hierarchy, bordered cards and sections, generous spacing, and a balanced two-column grid. The most important totals should be immediately visible at the top, while the lower section provides both a comparative view of query volume and a quick snapshot of the latest activity."

## Clarifications

### Session 2026-08-23

- Q: Should the three KPI totals be computed from all currencies in the system, or only from the top 10 currencies shown in the breakdown panel? → A: Retrieve the full unlimited usage list once; compute all three KPIs from every currency; the top-10 and 8-entry caps apply to panel display only.
- Q: How should the bar chart and the recent-activity list be made usable for someone using a screen reader or keyboard only? → A: Each row exposes currency code and count as readable text; bars are decorative and hidden from assistive technology; both panels have accessible section headings; timestamps carry a machine-readable date-time value alongside the human-readable text.
- Q: Should currencies with a query count of zero ever appear as rows in the breakdown panel? → A: No — exclude them from the rows, but show a footnote in the panel stating how many known currencies have never been queried.
- Q: In the recent-activity list, how should the time of each query be written? → A: Relative ("3 minutes ago", "2 days ago"), with the absolute local date-time available on hover/inspection.
- Q: How fast should the page show its complete content after the operator opens it, and what should happen if the data takes unusually long to arrive? → A: Under 2s typical; timeout 10s → error.

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Grasp overall query activity at a glance (Priority: P1)

An operator opens the Usage Analytics page and immediately sees a clear page title and a one-line
subtitle stating that the page gives an overview of currency query activity. Directly below, a row
of three bordered KPI cards shows the total number of queries recorded across all currencies, how
many distinct currencies have been queried at least once, and which currency has been queried most
often together with that currency's query count.

**Why this priority**: The three totals are the headline answer to "how much is this system being
used, and for what?". They are readable without scrolling, without interpreting a chart, and they
deliver standalone value even if no other section of the page exists.

**Independent Test**: With known usage data recorded, open the page and verify the title, subtitle,
and the three KPI cards, checking each displayed number against the underlying usage data (sum of
all query counts, count of currencies with at least one query, and the top currency with its
count).

**Acceptance Scenarios**:

1. **Given** usage data exists for several currencies with differing query counts, **When** the
   page loads, **Then** a title and a short subtitle describing the page as an overview of query
   activity appear at the top, above all other content.
2. **Given** usage data exists for several currencies, **When** the page loads, **Then** three KPI
   cards are shown in a single row displaying, respectively: the total number of queries summed
   across all currencies, the number of distinct currencies queried at least once, and the most
   frequently queried currency together with its query count.
3. **Given** two or more currencies are tied for the highest query count, **When** the "most
   queried" card renders, **Then** exactly one currency is shown, chosen deterministically (the
   alphabetically first of the tied currency codes), so repeated loads of the same data show the
   same result.
4. **Given** no currency has ever been queried, **When** the page loads, **Then** the KPI cards
   render with a total of 0, a unique-currency count of 0, and an explicit empty indication in the
   "most queried" card instead of a blank or misleading value.

---

### User Story 2 - Compare query volume across currencies (Priority: P1)

Below the KPI cards, in a wider left-hand panel, the operator sees an "Activity breakdown" section:
a horizontal bar chart ranking currencies by query count from highest to lowest. Each row shows the
currency code as a label, a bar whose length is proportional to that currency's share of the highest
count in the chart, and the numeric query count for that currency. Only currencies that have actually
been queried appear as rows; a footnote below the rows tells the operator how many known currencies
have never been queried at all.

**Why this priority**: Ranking is the core analytical value of the page — it turns a flat list of
counters into an immediate visual comparison of which currencies drive demand. It is independently
useful even without the recent-activity panel.

**Independent Test**: With usage data covering several currencies at different volumes plus some
never queried, open the page and verify the rows are ordered by descending query count, that no
never-queried currency appears as a row, that each row shows code, bar, and count, that bar lengths
are proportional to the counts (the top row's bar is full width, a currency with half the top count
has a bar roughly half that width), and that the footnote reports the never-queried count correctly.

**Acceptance Scenarios**:

1. **Given** several currencies with different query counts, **When** the breakdown panel renders,
   **Then** rows appear ordered by query count descending, each row containing the currency code,
   a proportional horizontal bar, and the query count as a number.
2. **Given** the currency with the highest query count, **When** the breakdown panel renders,
   **Then** its bar is the longest in the panel, and every other bar's length is proportional to
   that currency's count relative to the highest count.
3. **Given** more queried currencies exist than the panel displays, **When** the panel renders,
   **Then** only the highest-ranked currencies are shown, up to the panel's display limit, and the
   panel makes clear that it shows the top N of the total.
4. **Given** some known currencies have never been queried, **When** the panel renders, **Then**
   none of them appears as a row, and a footnote below the rows states how many currencies have
   never been queried.
5. **Given** every known currency has been queried at least once, **When** the panel renders,
   **Then** the never-queried footnote is absent or explicitly states zero, rather than showing a
   blank or misleading figure.
6. **Given** no currency has ever been queried, **When** the breakdown panel renders, **Then** it
   shows an explicit empty-state message with no rows and no zero-length bars, while the footnote
   still reports the never-queried count.

---

### User Story 3 - Check the latest query activity (Priority: P2)

Beside the breakdown panel, in a narrower right-hand panel, the operator sees a "Recent activity"
list: the most recently queried currencies, newest first. Each entry shows the currency code and how
long ago that currency was last queried ("3 minutes ago", "2 days ago"), with the exact local
date-time available on inspecting the entry.

**Why this priority**: This answers "is the system being used right now, and for what?" — valuable
for spotting live activity or a stall, but secondary to the totals and the ranking, and useless on
its own without them for context.

**Independent Test**: With usage data whose last-queried timestamps differ (some minutes old, some
days old), open the page and verify entries are ordered newest first, each showing a currency code
and a plausible elapsed-time phrase for its timestamp, that the absolute local date-time is
retrievable per entry, and that currencies never queried do not appear.

**Acceptance Scenarios**:

1. **Given** several currencies with different last-queried times, **When** the recent-activity
   panel renders, **Then** entries appear ordered by last-queried time descending (most recent
   first), each showing the currency code and how long ago that query happened as an elapsed-time
   phrase, with the absolute local date-time available on inspecting the entry.
2. **Given** some currencies have never been queried, **When** the recent-activity panel renders,
   **Then** those currencies are omitted from the list entirely.
3. **Given** more currencies have recent activity than the panel displays, **When** the panel
   renders, **Then** only the most recent entries are shown, up to the panel's display limit.
4. **Given** no currency has ever been queried, **When** the recent-activity panel renders,
   **Then** it shows an explicit empty-state message rather than an empty list.

---

### User Story 4 - Read the page comfortably on any screen (Priority: P3)

The page presents a clear visual hierarchy: title and subtitle first, then the KPI row, then a
balanced two-column section with the wider breakdown panel on the left and the narrower recent-
activity panel on the right. Cards and sections are visibly bordered and separated by generous
spacing. On narrow screens the columns stack vertically, in reading order (breakdown first, recent
activity second), without horizontal scrolling or overlapping content.

**Why this priority**: Layout quality determines whether the data is actually scannable, but the
page still delivers its information without the refined arrangement, so this ranks below the data
sections themselves.

**Independent Test**: Open the page at a wide viewport and confirm the two-column arrangement with
the left panel wider than the right, then narrow the viewport and confirm the panels stack in
order, remain fully readable, and no horizontal scrollbar appears.

**Acceptance Scenarios**:

1. **Given** a wide viewport, **When** the page renders, **Then** the KPI cards occupy one row
   above a two-column section whose left (breakdown) column is visibly wider than the right
   (recent activity) column, with visible borders around each card/panel and clear spacing between
   them.
2. **Given** a narrow viewport, **When** the page renders, **Then** the KPI cards and the two
   panels stack vertically in reading order with no horizontal scrolling and no clipped or
   overlapping content.
3. **Given** any viewport width, **When** the page renders, **Then** the page content is
   horizontally centered within a maximum content width consistent with the application's other
   pages.

---

### Edge Cases

- **No usage records exist at all**: every section renders its own explicit empty state (zero
  totals, empty-state text in both panels, no never-queried footnote figure to report); the page
  never shows a broken layout, a blank panel, or a fabricated value.
- **Records exist but nothing has been queried yet**: the KPI cards show 0, 0, and an explicit empty
  indication for "most queried"; the breakdown panel shows its empty state with the footnote
  reporting every known currency as never queried; the recent-activity panel shows its empty state.
- **A single currency queried**: the ranking panel shows exactly one row with a full-width bar, the
  footnote accounts for all the others, and the "most queried" card shows that currency; the layout
  does not collapse or leave a stray gap.
- **All queried currencies tied on count**: their bars all render at full length and ordering falls
  back to a deterministic tiebreak (alphabetical by currency code) so the display is stable across
  loads.
- **Very large query counts**: numbers remain fully readable inside their cards and rows (grouped
  thousands separators, no truncation or overflow of the card).
- **Currencies with a query count but no recorded last-queried time**: they still appear in the
  ranking panel but are omitted from the recent-activity list.
- **Elapsed-time phrases go stale on a page left open**: because the page does not auto-refresh, the
  phrases reflect the moment of page load and do not tick forward; the exact instant behind each one
  stays available on inspection, and reloading recomputes them.
- **A last-queried time that is not in the past** (clock skew between the viewer and the server):
  the entry MUST render a sane phrase for a just-now query rather than a negative or future-tense
  elapsed time.
- **Data unavailable**: when usage data cannot be retrieved, the page shows a single clear error
  message in place of the data sections rather than rendering zeros, partial data, or an empty
  dashboard that reads as "no activity".
- **Retrieval hangs**: if the data has not arrived within 10 seconds, the page abandons the wait and
  shows the same clear error state, so a stalled backend never presents as a permanent spinner.
- **Data still loading**: the page shows an explicit loading indication rather than briefly
  flashing empty states or zeros.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: The page MUST display a page title identifying it as usage/query analytics and a
  short subtitle stating that the page provides an overview of query activity, both positioned
  above all data sections.
- **FR-002**: The page MUST display a summary row of exactly three bordered KPI cards, positioned
  directly below the title/subtitle and above all other data sections.
- **FR-003**: The first KPI card MUST show the total number of queries, computed as the sum of
  every currency's recorded query count across all currencies known to the system — not only the
  currencies displayed in the breakdown panel.
- **FR-004**: The second KPI card MUST show the number of distinct currencies that have been
  queried at least once, counted across all currencies known to the system (currencies with a
  query count of zero MUST NOT be counted).
- **FR-005**: The third KPI card MUST show the currency with the highest query count across all
  currencies known to the system, together with that currency's query count; when several
  currencies tie for highest, it MUST show exactly one, selected by a deterministic tiebreak
  (alphabetically first currency code).
- **FR-005a**: All three KPI values MUST be derived from the complete, unlimited set of usage
  records; display limits (FR-009, FR-011) MUST NOT reduce the data the KPIs are computed from,
  and KPI labels MUST therefore describe system-wide totals with no "top N" qualifier.
- **FR-006**: The page MUST display an activity-breakdown panel containing a horizontal bar chart of
  queried currencies ordered by query count descending, with ties ordered alphabetically by currency
  code. Currencies with a query count of zero MUST NOT appear as rows.
- **FR-007**: Each breakdown row MUST show the currency code as a label, a horizontal bar, and the
  numeric query count.
- **FR-008**: Each breakdown bar's length MUST be proportional to that currency's query count
  relative to the highest query count displayed in the panel (the highest count renders as a
  full-length bar).
- **FR-009**: The breakdown panel MUST limit itself to the top 10 queried currencies by the FR-006
  ordering; the panel therefore shows between 0 and 10 rows, every row having a query count of at
  least 1. When more queried currencies exist than are shown, the panel MUST indicate that it is
  showing the top entries out of the larger total.
- **FR-009a**: The breakdown panel MUST display a footnote stating how many currencies known to the
  system have never been queried. The footnote MUST be counted across all currencies (not only the
  rows displayed), MUST be visually subordinate to the rows, and MUST be omitted or state zero when
  every known currency has been queried at least once.
- **FR-010**: The page MUST display a recent-activity panel listing recently queried currencies
  ordered by last-queried time descending, each entry showing the currency code and the time of
  that query.
- **FR-011**: The recent-activity panel MUST exclude currencies that have never been queried, and
  MUST limit itself to the 8 most recent entries.
- **FR-012**: Times in the recent-activity panel MUST be displayed as an elapsed-time phrase
  relative to the moment the page loaded (for example "3 minutes ago", "2 days ago"), with coarser
  units as the age grows and a distinct phrase for times under a minute old.
- **FR-012a**: The absolute date-time of each entry, in the viewer's local timezone and including
  both date and time-of-day, MUST remain available on inspection of that entry (for example as a
  hover/long-press tooltip), so the exact instant is never lost behind the relative phrase.
- **FR-013**: Each of the three data sections (KPI row, breakdown panel, recent-activity panel)
  MUST render an explicit empty state when it has no rows to show, distinct from an error state. For
  the breakdown panel this covers both no usage records at all and records existing with nothing
  queried yet; in the latter case the FR-009a footnote MUST still report the never-queried count.
- **FR-014**: When usage data cannot be retrieved, the page MUST show a single clear error message
  in place of the data sections and MUST NOT display fabricated, stale-as-current, or zeroed
  values as if they were real.
- **FR-015**: While usage data is being retrieved, the page MUST show an explicit loading
  indication instead of empty states or zero values.
- **FR-015a**: The retrieval MUST be bounded by a 10-second timeout; on timeout the page MUST stop
  waiting and show the FR-014 error state rather than leaving the loading indication in place
  indefinitely.
- **FR-016**: The page MUST arrange the breakdown panel and the recent-activity panel as a
  two-column grid on wide viewports, with the breakdown column visibly wider than the
  recent-activity column.
- **FR-017**: On narrow viewports the page MUST stack all cards and panels vertically in reading
  order (KPI cards, then breakdown, then recent activity) with no horizontal scrolling and no
  clipped or overlapping content.
- **FR-018**: All KPI cards and section panels MUST be visually delineated with borders and
  separated by consistent, generous spacing, and page content MUST be centered within a maximum
  content width consistent with the application's other pages.
- **FR-019**: Query counts displayed anywhere on the page MUST be formatted with thousands
  separators appropriate to the viewer's locale and MUST NOT be rounded, abbreviated, or truncated.
- **FR-020**: The page MUST be reachable from the application's existing navigation at the route
  already assigned to usage analytics, replacing the current placeholder view without changing
  that route's address.
- **FR-021**: Rendering this page MUST NOT alter usage counters — viewing analytics is not a rate
  query and MUST NOT be recorded as one.
- **FR-022**: Every value the page conveys MUST be available as readable text, not by visual
  proportion alone: each breakdown row MUST expose its currency code and query count as text, and
  each recent-activity entry MUST expose its currency code and query time as text.
- **FR-023**: The proportional bars MUST be treated as decorative reinforcement of the numbers
  beside them and MUST be hidden from assistive technology, so that a screen-reader user receives
  each row exactly once, as code plus count, with no duplicated or meaningless bar announcement.
- **FR-024**: The KPI row, the breakdown panel, and the recent-activity panel MUST each carry a
  section heading that is exposed to assistive technology and identifies that section's content,
  so the page can be navigated by heading.
- **FR-025**: Each displayed query time MUST be accompanied by a machine-readable date-time value
  for the same instant, so the exact instant is available to assistive technology and tooling
  regardless of the human-readable formatting shown.
- **FR-026**: The page MUST be fully readable and navigable using the keyboard alone; because it
  presents no interactive controls, no element MUST trap focus or require pointer interaction to
  reveal a value.

### Key Entities *(include if feature involves data)*

- **Currency Usage Record**: One record per currency the system knows about, holding the currency
  code, the total number of rate queries recorded against that currency, and the time that currency
  was last queried (absent when it has never been queried). This is the sole data source for every
  section of the page.
- **Usage Summary**: A derived, page-level roll-up of all Currency Usage Records: the total query
  count across all currencies, the number of currencies with at least one query, and the
  highest-count currency with its count.
- **Ranked Usage Row**: A derived, display-level item for the breakdown panel: currency code, query
  count (always at least 1), and that count's proportion of the highest displayed count. Alongside
  the rows the panel carries one derived figure: the number of known currencies never queried.
- **Recent Activity Entry**: A derived, display-level item for the recent-activity panel: currency
  code and the last-queried time, restricted to currencies that have been queried.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: A first-time viewer can state the total number of queries, the number of distinct
  currencies queried, and the single most-queried currency within 10 seconds of the page appearing,
  without scrolling or interacting with the page.
- **SC-002**: A viewer can name the top three most-queried currencies in rank order within 15
  seconds of the page appearing, in 100% of attempts with at least three queried currencies.
- **SC-003**: Every number shown on the page matches the underlying usage data exactly — 100%
  agreement between displayed totals/counts and the recorded per-currency counts, with no rounding
  or approximation.
- **SC-004**: The page presents its complete content (or an explicit loading/error/empty state) with
  no blank or half-rendered sections, in 100% of loads across the tested data conditions: populated
  data, single-currency data, no data, and data-unavailable.
- **SC-005**: The page is fully readable with no horizontal scrolling and no overlapping or clipped
  content at every viewport width from 320 px to 2560 px.
- **SC-006**: Repeated loads of the same unchanged usage data produce an identical page — same
  ordering, same "most queried" currency, same entries — in 100% of loads.
- **SC-007**: Viewing the page any number of times leaves every currency's query count unchanged.
- **SC-008**: A screen-reader user can obtain every value a sighted user can — all three KPI values,
  every displayed currency code with its query count, and every recent entry with its time — with
  no value conveyed only by bar length, in 100% of the tested data conditions.
- **SC-009**: With the backend reachable on a local network, the page shows its complete content
  within 2 seconds of being opened, in at least 95% of loads.
- **SC-010**: When the data cannot be retrieved, the operator sees a clear error state within 10
  seconds of opening the page, in 100% of attempts — never an indefinite loading indication.

## Assumptions

- **Recent activity is derived from per-currency last-queried times, not a per-query event log.**
  The system records one "last queried" time per currency rather than a history of individual query
  events, so the "latest query events" list is realized as "most recently queried currencies,
  newest first, one entry per currency". Building a true per-event query history is out of scope
  here; it would require new data capture beyond this page.
- **The "categories/items" in the request are currencies.** The user description's generic wording
  ("categories or items") maps onto currency codes in this system; no other groupable dimension of
  query activity is recorded.
- **The existing usage analytics data source is reused as-is.** This feature is a presentation
  change only: no new counters, no new data capture, and no change to the existing usage-analytics
  contract. The page reads the unfiltered, unlimited usage data and does its own ranking, recency
  ordering, and limiting for display.
- **Display limits are presentation-only.** The breakdown panel shows the top 10 currencies and the
  recent-activity panel the 8 latest entries — enough to be informative in one screen without
  paging. The breakdown panel lists only queried currencies, so it can render fewer than 10 rows
  when activity is sparse; the never-queried footnote accounts for the remainder rather than padding
  the panel with empty bars. Both limits are display caps applied to the complete usage data the
  page already holds;
  they never narrow what the KPI cards are computed from (see FR-005a). The limits are fixed; no
  user-facing controls (paging, sorting, filtering, date range, refresh button) are in scope.
- **One retrieval per page load.** The page retrieves the complete, unlimited usage data set once
  and derives all three sections from it, rather than issuing separate narrowed retrievals per
  section. The number of currencies is small enough (under a few hundred) that this is cheap and
  keeps every section internally consistent with the same snapshot.
- **This page replaces the existing usage-analytics placeholder view** at its current route, and
  keeps that route's address and its navigation entry unchanged.
- **No authentication or per-user scoping**: the page shows system-wide usage activity, consistent
  with the rest of the application, which has no user accounts.
- **No live auto-refresh**: data is loaded when the page is opened; a viewer sees updated numbers by
  revisiting or reloading the page. Elapsed-time phrases are likewise computed at load time and do
  not tick while the page sits open.
- **Viewers are internal operators/stakeholders** on a modern desktop or mobile browser with
  connectivity to the backend; absolute times behind the elapsed-time phrases are rendered in the
  viewer's own local timezone.
