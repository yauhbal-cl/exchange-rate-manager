---

description: "Task list template for feature implementation"
---

# Tasks: Historical Exchange Rate Trends

**Input**: Design documents from `/specs/013-historical-rate-trends/`

**Prerequisites**: plan.md, spec.md, research.md, data-model.md, contracts/ui-contract.md, quickstart.md

**Tests**: `historical-rates.spec.ts`, `period-presets.spec.ts`, `trend-metrics.spec.ts` are
explicitly named as deliverables in plan.md's Project Structure and quickstart.md's Automated
check — included below as first-class tasks per user story.

**Organization**: Tasks are grouped by user story (spec.md P1–P3) to enable independent
implementation and testing of each story. This is a frontend-only feature — no backend changes.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Can run in parallel (different files, no dependencies)
- **[Story]**: Which user story this task belongs to (US1–US5)
- Paths are relative to `frontend/src/app/features/historical-rates/` unless stated otherwise

## Path Conventions

Web app (existing monorepo): `frontend/src/app/...`. This feature adds one new folder,
`frontend/src/app/features/historical-rates/`, and touches `app.routes.ts`, `shell/shell.html`,
and `package.json`. No backend files are touched (contracts/openapi.yaml is unchanged).

---

## Phase 1: Setup (Shared Infrastructure)

**Purpose**: Add the new charting dependency and scaffold the feature folder before any story work.

- [X] T001 Add `chart.js` (~4.5.x) and `decimal.js` to `frontend/package.json` dependencies and run `cd frontend && npm install` (research.md §1, §2)
- [X] T002 Create empty feature folder `frontend/src/app/features/historical-rates/` (no files yet — placeholder for Phase 2/3 tasks)

**Checkpoint**: `chart.js` resolvable via `import` in the frontend project.

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: Pure helper modules and the route wiring every user story's UI depends on. No user
story's UI can be built/tested until these exist.

**⚠️ CRITICAL**: No user story work can begin until this phase is complete.

- [X] T003 [P] Create `period-presets.ts` in `frontend/src/app/features/historical-rates/period-presets.ts`: define the 5 presets (`7D`,`15D`,`1M`,`3M`,`6M`) as `{id, label, unit: 'days'|'months', amount}` (`7D`/`15D` → days; `1M`/`3M`/`6M` → months), a shared `subtractMonths(date, n)` helper used by both the month-based presets and the FR-006 cap check, the `PeriodSelection` discriminated union (`{kind:'preset', id}` | `{kind:'custom', startDate, endDate}`), and `resolveRange(selection, today) -> {startDate, endDate}` using string-based `yyyy-MM-dd` date arithmetic matching `rate-lookup`'s `todayIso()` approach (data-model.md "Period Selection", research.md §4, §8)
- [X] T004 [P] Create `period-presets.spec.ts` in `frontend/src/app/features/historical-rates/period-presets.spec.ts` covering: each preset resolves to the correct trailing window ending "today"; custom range passthrough; span/start-after-end validation helper cases; `subtractMonths` calendar clamping (e.g. Aug 31 − 1 month = Jul 31); the `6M` preset's `startDate` equals the FR-006 cap boundary's `subtractMonths(endDate, 6)` for the same `today` (research.md §4, §8)
- [X] T005 [P] Create `trend-metrics.ts` in `frontend/src/app/features/historical-rates/trend-metrics.ts`: pure functions computing `latest`, `periodChange` (absolute + percent, `null` if <2 points), `periodHigh`/`periodLow` (value + date), and `dailyChanges` (per-row % vs. previous row, `null` for the oldest row) from `RateTrendPoint[]`, using `Decimal` (`decimal.js`) for every comparison/subtraction/division — never `parseFloat`/JS `number` — each headline value returned as `{display, value}` with `display` always the verbatim source string and `value` a `Decimal` (data-model.md "Trend Metrics", research.md §2, Constitution I)
- [X] T006 [P] Create `trend-metrics.spec.ts` in `frontend/src/app/features/historical-rates/trend-metrics.spec.ts` covering: empty points, single point (periodChange null), multiple points (high/low/percent-change correctness), daily-change boundary (first row has no prior value), and a decimal-precision case (e.g. a value like `1.005`) asserting no floating-point drift in the computed percent/display strings
- [X] T007 Add `historical-rates` route to `frontend/src/app/app.routes.ts` (lazy `loadComponent`, path `historical-rates`, matching the existing `rate-lookup`/`usage-analytics`/`ai-insight` pattern) — component created in Phase 3
- [X] T008 Add "Historical Rates" nav link to `frontend/src/app/shell/shell.html` (same `routerLink`/`routerLinkActive` pattern as the existing three nav entries)

**Checkpoint**: Foundation ready — presets/metrics logic and routing/nav exist; user story UI work can now begin.

---

## Phase 3: User Story 1 - View trend and key metrics for a currency pair (Priority: P1) 🎯 MVP

**Goal**: Selecting a base/quote currency pair shows summary metrics and a line chart for the
default period, all driven by one reactive data source, with an explicit "no data" state.

**Independent Test**: Pick a currency pair with historical data for the default (1M) period;
verify metrics and chart render and stay consistent (chart endpoints match reported latest
rate/period change); verify a no-data pair shows the explicit empty state instead of a blank
screen.

### Tests for User Story 1

- [X] T009 [P] [US1] Add container-level spec cases to `historical-rates.spec.ts` in `frontend/src/app/features/historical-rates/historical-rates.spec.ts`: default USD/EUR + 1M preset renders metrics+chart in order (FR-010); changing base/quote fires exactly one new trend request and updates metrics/chart together (User Story 1 Acceptance Scenario 3, SC-001); empty `points` response renders the "no data" state for metrics and chart, not a blank/crashed view (FR-015, User Story 1 Acceptance Scenario 2); selecting an identical base/quote currency shows the pair validation message and fires no `/exchange/trend` request (FR-002, spec.md Edge Cases)

### Implementation for User Story 1

- [X] T010 [US1] Create `historical-rates.ts` container component in `frontend/src/app/features/historical-rates/historical-rates.ts`: standalone component routed at `historical-rates`; `baseCurrency`/`quoteCurrency` signals (initial `'USD'`/`'EUR'` per data-model.md, importing `CURRENCIES`/`Currency` from `../rate-lookup/currencies`); `pairError` computed (non-null when equal, FR-002); `period` signal (initial `{kind:'preset', id:'1M'}`); `pairAndRange` computed combining pair+period via `resolveRange` from `period-presets.ts`, `undefined` when `pairError()` is set (data-model.md "Resolved Request")
- [X] T011 [US1] Add the `trend` `rxResource` to `historical-rates.ts`, keyed on `pairAndRange()`, calling `ExchangeRateAnalyticsService.getExchangeRateTrend(from, to, startDate, endDate)` only (no other generated service method) per `contracts/ui-contract.md` "Backend calls" (research.md §3)
- [X] T012 [US1] Add base/quote currency combobox markup to `historical-rates.ts`'s template using the reused `CurrencyCombobox` from `../rate-lookup/currency-combobox` (`input[name="base-currency"]`, `input[name="quote-currency"]` selector contract) and render the pair validation message only when `pairError()` is non-null, per `contracts/ui-contract.md`
- [X] T013 [US1] Compute summary metrics in `historical-rates.ts`'s template via `trend-metrics.ts`'s pure functions over `trend.value()?.points`, rendering latest rate, period change (%, signed), period high (+date), period low (+date) when points exist, and the explicit "no data" state otherwise (FR-007, FR-015)
- [X] T014 [US1] Create `rate-trend-chart.ts` in `frontend/src/app/features/historical-rates/rate-trend-chart.ts`: standalone `app-rate-trend-chart` component with `points`, `dailyChanges`, `periodHigh`, `periodLow` input signals (from `trend-metrics.ts`, mirrors `historical-rates-table.ts`'s inputs), wrapping a `<canvas>` and a Chart.js line-chart instance, created/updated in an `effect()`/`afterRenderEffect` keyed on those inputs, `chart.destroy()` on component destroy; category x-axis (point-index → date label, research.md §6) with `ticks.autoSkip`/`maxTicksLimit` tuned by point count; y-axis not forced to zero; tooltip shows date, exact verbatim rate string, and daily % change (from the `dailyChanges` input); period-high and period-low points rendered as a distinct styled point + label via an inline Chart.js plugin object registered on the chart instance (no new npm dependency); renders a "no data" placeholder when `points` is empty (FR-008, FR-015, ui-contract.md, research.md §7)
- [X] T015 [US1] Wire `rate-trend-chart.ts` into `historical-rates.ts`'s template, passing `trend.value()?.points` (or `[]`) plus `dailyChanges`/`periodHigh`/`periodLow` computed via `trend-metrics.ts` (same values already used for the summary row), placed after the summary metrics row per the layout order (FR-010)

**Checkpoint**: User Story 1 fully functional and independently testable — pair selection, auto-refresh, metrics, chart, and no-data state all work without periods/swap/table/AI.

---

## Phase 4: User Story 2 - Choose the period via presets or a custom range (Priority: P2)

**Goal**: Users switch between 5 named presets or a validated custom date range; the same
`pairAndRange` resource re-fetches for each.

**Independent Test**: With a pair spanning several months of data, select each preset in turn and
verify the chart/metrics window changes accordingly; pick a valid custom range and verify the same;
pick an invalid custom range (>6 months, or start after end) and verify a validation message with
no request fired.

### Tests for User Story 2

- [X] T016 [P] [US2] Add spec cases to `historical-rates.spec.ts`: selecting each preset resolves to the correct trailing window and fires a new trend request (FR-004, User Story 2 Acceptance Scenario 1); a valid custom range (≤6 months, start ≤ end) fires a request with those exact dates (FR-005, Acceptance Scenario 2); a custom range >6 months or start-after-end shows a validation message and fires no request (FR-006, Acceptance Scenarios 3–4, SC-004)

### Implementation for User Story 2

- [X] T017 [US2] Add `periodError` computed to `historical-rates.ts` (non-null when `period().kind === 'custom'` and either end < start or `startDate < subtractMonths(endDate, 6)` using `period-presets.ts`'s shared helper — the same "6 months" definition as the `6M` preset, per data-model.md "Period Selection"), and gate `pairAndRange` on `periodError() === null` in addition to `pairError()`
- [X] T018 [US2] Add preset buttons to `historical-rates.ts`'s template (`button[data-preset="7D"|"15D"|"1M"|"3M"|"6M"]`, ui-contract.md), each setting `period` to `{kind:'preset', id}` and visually indicating the active preset
- [X] T019 [US2] Add custom range date inputs to `historical-rates.ts`'s template (`input[type="date"][name="range-start"]`, `input[type="date"][name="range-end"]`, ui-contract.md), each setting `period` to `{kind:'custom', startDate, endDate}`, and render the range validation message only when `periodError() !== null`

**Checkpoint**: User Stories 1 AND 2 both work independently — full period control layered on top of US1's pair/metrics/chart.

---

## Phase 5: User Story 3 - Quickly swap base and quote currencies (Priority: P2)

**Goal**: A single swap action exchanges base/quote, refreshes the view for the same period, and
clears any AI narrative (staleness rule shared with User Story 5).

**Independent Test**: With a pair selected and a trend showing, trigger swap and verify base/quote
exchange, the view refreshes for the swapped pair using the same period, and any visible AI
narrative is cleared.

### Tests for User Story 3

- [X] T020 [P] [US3] Add spec cases to `historical-rates.spec.ts`: triggering swap exchanges `baseCurrency`/`quoteCurrency`, fires exactly one new trend request for the swapped pair using the unchanged period (FR-003, SC-002). (Swap-clears-AI-narrative, User Story 3 Acceptance Scenario 2, is asserted in T026 instead — it depends on the `aiRequest` signal/effect introduced in Phase 7.)

### Implementation for User Story 3

- [X] T021 [US3] Add `swap()` method to `historical-rates.ts`: reads `baseCurrency`/`quoteCurrency` and writes them back transposed in one batch (single signal-write tick, data-model.md "Currency Pair")
- [X] T022 [US3] Add swap control button to `historical-rates.ts`'s template (`button[aria-label="Swap currencies"]`, ui-contract.md) between the currency combobox pair, calling `swap()`

**Checkpoint**: User Stories 1–3 all work independently — pair, period, and swap fully functional.

---

## Phase 6: User Story 4 - Review raw historical rates in a table (Priority: P3)

**Goal**: A table lists one row per chart point (most-recent-first), with date, verbatim rate, and
signed daily % change — always exactly matching the chart's data.

**Independent Test**: With a pair/period showing N chart points, verify the table shows exactly
those N dates/rates with daily % changes; verify a no-data selection shows the table's empty state
consistent with the chart's.

### Tests for User Story 4

- [X] T023 [P] [US4] Create `historical-rates.spec.ts` cases (or a co-located table-level block) covering: table row count/order/values exactly match `trend.value()?.points` reversed (most-recent-first) for a given pair/period (FR-009, SC-003, User Story 4 Acceptance Scenario 3); empty points renders the table's "no data" state consistent with the chart's (Acceptance Scenario 2)

### Implementation for User Story 4

- [X] T024 [US4] Create `historical-rates-table.ts` in `frontend/src/app/features/historical-rates/historical-rates-table.ts`: standalone `app-historical-rates-table` component taking `points` (and derived `dailyChanges` from `trend-metrics.ts`) as inputs, rendering rows most-recent-first with columns Date, Exchange rate (verbatim string), Daily change (%, signed); renders the same "no data" state as the chart when `points` is empty (FR-009, FR-015, ui-contract.md)
- [X] T025 [US4] Wire `historical-rates-table.ts` into `historical-rates.ts`'s template as the last element, after the chart section (the AI Insights panel lands inside that same section in Phase 7/T030 — the table only needs to stay last; nothing here depends on the panel already existing), per the layout order (FR-010)

**Checkpoint**: User Stories 1–4 all work independently — full data view (pair, period, swap, chart, table) functional without AI.

---

## Phase 7: User Story 5 - Request an AI-generated interpretation of the trend (Priority: P3)

**Goal**: An explicit "Generate insight" action produces a short narrative grounded in the
currently displayed data; it never auto-fires and is cleared on any pair/period/swap change.

**Independent Test**: With a pair/period that has data, trigger "Generate insight" and verify a
narrative grounded in the displayed data appears; verify it's unavailable/disabled with no data;
verify it clears on pair/period/swap change; verify a clear "unavailable" message when the AI
service fails.

### Tests for User Story 5

- [ ] T026 [P] [US5] Add spec cases to `historical-rates.spec.ts`: no narrative appears until "Generate insight" is clicked (FR-011); clicking it with data fires exactly one insight request and shows a narrative grounded in the displayed range (FR-012, Acceptance Scenario 1); the button is disabled when `trend.value()?.points` is empty/absent or `trend.isLoading()` (FR-013, Acceptance Scenario 3); an insight-service error (404 vs. other) shows the correct categorized message rather than a fabricated narrative or raw error (FR-013, Acceptance Scenario 2, SC-005); any pair/period/swap change clears a shown/in-flight narrative (FR-014, Acceptance Scenario 4, SC-006)

### Implementation for User Story 5

- [ ] T027 [US5] Create `ai-insights-panel.ts` in `frontend/src/app/features/historical-rates/ai-insights-panel.ts`: standalone `app-ai-insights-panel` component (header "AI Insights") taking the `aiInsight` resource's `value()`/`isLoading()`/`error()` and a `canGenerate` input as inputs, emitting a `generate` output on button click; button disabled when `!canGenerate` or loading; renders loading/narrative/error states, error state categorized via a computed `AiInsightError` (`404` → `'no-data'`, else `'unavailable'`, using `ProblemDetail.detail` when present else a fixed fallback) per data-model.md "AI Insight Error" (FR-013)
- [ ] T028 [US5] Add `aiRequest` signal (`signal<TrendRequest | undefined>(undefined)`) and the `aiInsight` `rxResource` to `historical-rates.ts`, keyed on `aiRequest()`, calling `ExchangeRateAIInsightService.getExchangeRateTrendInsight(from, to, startDate, endDate)` only when `aiRequest()` is defined (research.md §3, ui-contract.md "Backend calls")
- [ ] T029 [US5] Add an `effect()` to `historical-rates.ts` watching `pairAndRange()` that sets `aiRequest.set(undefined)` on every change (including swap), implementing FR-014's staleness rule structurally (data-model.md "AI Insight Request")
- [ ] T030 [US5] Wire `ai-insights-panel.ts` into `historical-rates.ts`'s template beside the chart (≈30–35% width on desktop, stacked below `lg` per `contracts/ui-contract.md` "Layout order"), passing `canGenerate = trend.value()?.points.length > 0 && !trend.isLoading()` and a `generate` handler that sets `aiRequest.set(pairAndRange())`

**Checkpoint**: All 5 user stories independently functional — full feature complete per spec.md.

---

## Phase 8: Polish & Cross-Cutting Concerns

**Purpose**: Final verification against the full behavioral contract and quickstart scenarios.

- [ ] T031 [P] Verify `frontend/src/app/features/historical-rates/historical-rates.ts` template layout order matches FR-010 exactly: title/supporting text → pair selectors + swap → period presets/custom range → summary metrics → chart beside AI panel → table (`contracts/ui-contract.md` "Layout order")
- [ ] T032 Run `cd frontend && npm test -- historical-rates` and confirm all specs (`period-presets.spec.ts`, `trend-metrics.spec.ts`, `historical-rates.spec.ts`) pass (quickstart.md "Automated check")
- [ ] T033 Execute `quickstart.md` Scenarios 1–7 manually against a running backend with several months of ingested history (`docker compose up -d`, `cd backend && ./mvnw spring-boot:run`, `cd frontend && npm start`), confirming each `Expect:` outcome including the Ollama-unavailable case in Scenario 6

---

## Dependencies & Execution Order

### Phase Dependencies

- **Setup (Phase 1)**: No dependencies — start immediately.
- **Foundational (Phase 2)**: Depends on Setup — BLOCKS all user stories (routing/nav and
  presets/metrics pure functions are shared by every story's UI).
- **User Stories (Phase 3–7)**: All depend on Foundational completion.
  - US1 (P1) has no dependency on other stories — it is the MVP.
  - US2 (P2) extends US1's `historical-rates.ts` (adds `periodError`, preset/custom controls) —
    build after US1's container exists (T010).
  - US3 (P2) extends US1's `historical-rates.ts` (adds `swap()`) — build after US1's container
    exists (T010); independent of US2.
  - US4 (P3) is additive (new table component + wiring) — build after US1's `trend` resource
    exists (T011).
  - US5 (P3) is additive (new panel component + second resource) — build after US1's
    `pairAndRange` computed exists (T010); the swap-clears-narrative behavior (User Story 3
    Acceptance Scenario 2) is asserted in US5's own test task (T026), not US3's, since it depends
    on the `aiRequest` signal/effect (T028–T029) that US5 introduces.
- **Polish (Phase 8)**: Depends on all desired user stories being complete.

### User Story Dependencies

- **US1 (P1)**: Independent — buildable and testable alone (MVP).
- **US2 (P2)**: Builds on US1's container component; independently testable once layered on.
- **US3 (P2)**: Builds on US1's container component; independent of US2; independently testable.
- **US4 (P3)**: Builds on US1's `trend` resource; independent of US2/US3; independently testable.
- **US5 (P3)**: Builds on US1's `pairAndRange`; its full staleness behavior (FR-014) touches
  US3's swap path, but US5 is independently testable for the explicit-trigger/error/disabled
  behaviors without US3 present.

### Within Each User Story

- Spec/test tasks before implementation tasks (write first, confirm they fail, per template
  convention — though this repo has not marked tests as strictly TDD-required elsewhere).
- Container/resource wiring before template markup that consumes it.
- Story complete and checkpointed before moving to the next priority.

### Parallel Opportunities

- T003/T004 (presets) and T005/T006 (metrics) can run in parallel — different files.
- T007 (routes) and T008 (nav) can run in parallel — different files.
- Once Foundational (Phase 2) completes: US2 (Phase 4) and US3 (Phase 5) can be built in parallel
  by different developers, since both only extend `historical-rates.ts` independently (watch for
  same-file edit conflicts if done concurrently by different people — coordinate on
  `historical-rates.ts` edits).
- US4 (Phase 6) and US5 (Phase 7) can be built in parallel — separate new components
  (`historical-rates-table.ts` vs. `ai-insights-panel.ts`).

---

## Parallel Example: Foundational Phase

```bash
# Launch presets and metrics helper modules together (different files):
Task: "Create period-presets.ts with resolveRange() and PeriodSelection union"
Task: "Create period-presets.spec.ts covering preset/custom resolution and validation cases"
Task: "Create trend-metrics.ts with latest/periodChange/periodHigh/periodLow/dailyChanges"
Task: "Create trend-metrics.spec.ts covering empty/single/multi-point and boundary cases"
```

## Parallel Example: User Story 4 & 5 (after Foundational + US1)

```bash
# Launch the table and AI panel components together (different files, both additive to US1):
Task: "Create historical-rates-table.ts rendering points most-recent-first with daily % change"
Task: "Create ai-insights-panel.ts with explicit Generate-insight trigger and error categorization"
```

---

## Implementation Strategy

### MVP First (User Story 1 Only)

1. Complete Phase 1: Setup (`chart.js` dependency).
2. Complete Phase 2: Foundational (presets/metrics pure functions, routing, nav) — blocks all
   stories.
3. Complete Phase 3: User Story 1 (pair selection, auto-refresh `trend` resource, metrics, chart,
   no-data state).
4. **STOP and VALIDATE**: Confirm chart/metrics render and stay consistent for a known pair,
   default 1M period; confirm a no-data pair shows the explicit empty state (quickstart.md
   Scenarios 1, 2, 5 partial).
5. Deploy/demo if ready — this alone delivers spec.md's stated core value ("without it there is no
   feature").

### Incremental Delivery

1. Setup + Foundational → foundation ready.
2. Add US1 → validate independently → MVP.
3. Add US2 (period control) → validate independently (quickstart.md Scenario 3).
4. Add US3 (swap) → validate independently (quickstart.md Scenario 4).
5. Add US4 (table) → validate independently (quickstart.md Scenario 7).
6. Add US5 (AI insight) → validate independently (quickstart.md Scenario 6).
7. Phase 8 polish → full quickstart.md pass (Scenarios 1–7).

### Parallel Team Strategy

With multiple developers, after Setup + Foundational:

- Developer A: US1 (must land first — everyone else extends its container).
- Once US1's container exists: Developer B takes US2, Developer C takes US3 (both extend
  `historical-rates.ts` — coordinate to avoid conflicting edits), Developer D takes US4, Developer
  E takes US5 (both additive components, safely parallel with B/C).

---

## Notes

- [P] tasks = different files, no dependencies.
- [Story] label maps task to specific user story for traceability.
- This is a frontend-only feature — `contracts/openapi.yaml` and all backend code are unchanged
  (plan.md Summary, `contracts/ui-contract.md`).
- Constitution I (Monetary Precision): every `RateTrendPoint.rate` is rendered verbatim as a
  string in metrics/table/chart tooltip; `trend-metrics.ts`'s derived `Decimal` (decimal.js)
  values are display/comparison-only and never re-serialized or sent to any API — all arithmetic
  on rate values uses `Decimal`, never JS `number`/`parseFloat` (research.md §2).
- Verify tests fail before implementing, where practical.
- Commit after each task or logical group.
- Stop at any checkpoint to validate a story independently.
- Avoid: vague tasks, same-file conflicts, cross-story dependencies that break independence.
