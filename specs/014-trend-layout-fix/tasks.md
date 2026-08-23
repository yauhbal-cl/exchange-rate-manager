---

description: "Task list for 014-trend-layout-fix"
---

# Tasks: Historical Trends Full-Width Chart & AI Insights Layout

**Input**: Design documents from `/specs/014-trend-layout-fix/`

**Prerequisites**: plan.md (required), spec.md (required for user stories), research.md,
data-model.md, quickstart.md

**Tests**: `historical-rates.spec.ts` already asserts structural markers (element order via
`innerHTML.indexOf`, `data-testid` presence) rather than computed pixel widths, since Vitest/jsdom
does not perform real layout. This task list follows that existing convention and includes test
tasks per user story, consistent with 013-historical-rate-trends's tasks.md.

**Organization**: This is a frontend-only, single-file fix (`historical-rates.ts`'s template).
Both user stories below edit the same template region, so they are sequenced (US2 depends on
US1's edit) rather than parallel, even though each remains independently verifiable per its own
acceptance scenarios.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Can run in parallel (different files, no dependencies)
- **[Story]**: Which user story this task belongs to (US1, US2)
- Paths are relative to the repository root

## Path Conventions

Web application structure per plan.md: `frontend/src/app/features/historical-rates/`. No
backend files are touched by this fix.

---

## Phase 1: Setup

**Purpose**: Establish the pre-change baseline so regressions are detectable

- [X] T001 Run `cd frontend && npm test -- historical-rates` and confirm the current suite in
  `frontend/src/app/features/historical-rates/historical-rates.spec.ts` passes before any layout
  change, to serve as the regression baseline (plan.md Constraints)

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: N/A — no shared infrastructure, model, or contract change is needed. The existing
`RateTrendChart`, `AiInsightsPanel`, and `HistoricalRatesTable` standalone components and their
inputs/outputs are unchanged (data-model.md); this fix only rearranges how `historical-rates.ts`
lays out those already-working components. No tasks in this phase.

**Checkpoint**: Proceed directly to Phase 3.

---

## Phase 3: User Story 1 - Trend chart renders full container width (Priority: P1) 🎯 MVP

**Goal**: The trend chart spans the full width of the `historical-rates` container (matching the
metrics row and table width) at every viewport width, instead of sharing a row with the AI
Insights panel (FR-001, FR-004, FR-005).

**Independent Test**: With a pair/period that has historical data, verify the chart's rendered
width matches the metrics row/table width at both narrow and wide viewport sizes (spec.md User
Story 1).

### Tests for User Story 1

- [X] T002 [P] [US1] In `frontend/src/app/features/historical-rates/historical-rates.spec.ts`,
  add a test asserting the component's rendered `innerHTML` no longer contains the side-by-side
  split classes (`lg:grid-cols-3`, `lg:col-span-2`) that currently constrain the chart to a
  partial-width column — write it so it fails against the current template (FR-001, FR-004)

### Implementation for User Story 1

- [X] T003 [US1] In `frontend/src/app/features/historical-rates/historical-rates.ts`'s template,
  remove the `<div class="mt-6 grid grid-cols-1 gap-4 lg:grid-cols-3">` wrapper and its
  `<div class="lg:col-span-2">` chart cell; render `<app-rate-trend-chart>` in its own full-width
  block (`<div class="mt-6">`), matching the pattern already used for the table block below it
  (research.md decision) (FR-001, FR-005, FR-006)

**Checkpoint**: T002 passes; the chart renders full width with no other behavior changed.

---

## Phase 4: User Story 2 - AI Insights stacked between chart and table, full width (Priority: P1)

**Goal**: The AI Insights section renders directly after the trend chart and directly before the
raw data table, at the same full container width, in every state (idle, loading, result, error),
at every viewport width — never beside the chart in a side column (FR-002, FR-003, FR-004).

**Independent Test**: With a pair/period that has historical data, request an AI insight and
verify it renders full-width, positioned after the chart and before the table, at both narrow and
wide viewport sizes, across idle/loading/result/error states (spec.md User Story 2).

### Tests for User Story 2

- [X] T004 [P] [US2] In `frontend/src/app/features/historical-rates/historical-rates.spec.ts`, add
  a test (or extend the existing FR-010 order test) asserting via `innerHTML.indexOf(...)` that
  `app-rate-trend-chart` renders before `app-ai-insights-panel`, which renders before the raw data
  table markup, in that order — verify this holds for the idle state, the loaded-insight state
  (using the existing `insightResponse()` helper), and the error state (using the existing 503
  `throwError` setup) already present in this spec file (FR-002, FR-003, SC-003)

### Implementation for User Story 2

- [X] T005 [US2] In `frontend/src/app/features/historical-rates/historical-rates.ts`'s template
  (depends on T003's edit to the same block), move `<app-ai-insights-panel>` out of the removed
  grid's `lg:col-span-1` cell into its own full-width block (`<div class="mt-6">`) placed
  immediately after the chart's block from T003 and immediately before the existing
  `<div class="mt-6"><app-historical-rates-table ...></div>` block; keep its `[value]`,
  `[isLoading]`, `[error]`, `[canGenerate]`, and `(generate)` bindings exactly as they are today
  (FR-002, FR-003, FR-004, FR-006)

**Checkpoint**: T004 passes; both P1 stories are done — the page now stacks chart → AI Insights →
table, each full width, at every viewport width.

---

## Phase 5: Polish & Cross-Cutting Concerns

**Purpose**: Confirm no regressions beyond the two stories above

- [ ] T006 [P] Run `cd frontend && npm test -- historical-rates` and confirm every test in
  `historical-rates.spec.ts` (pre-existing plus T002 and T004) passes (SC-004; quickstart.md
  "Automated validation")
- [ ] T007 [P] Run `cd frontend && npm test` (full suite) and confirm no unrelated regressions in
  `period-presets.spec.ts`, `trend-metrics.spec.ts`, or any other spec (plan.md Constraints)
- [ ] T008 Walk through quickstart.md's "Manual validation scenarios" in a browser — resize from
  narrow (~375px) to wide (~1440px+) and confirm the chart and AI Insights panel stay full width
  and stacked (never side by side) across the idle, loading, result, and error insight states

---

## Dependencies & Execution Order

### Phase Dependencies

- **Setup (Phase 1)**: No dependencies
- **Foundational (Phase 2)**: Empty — no blocking prerequisite beyond Setup
- **User Story 1 (Phase 3)**: Depends on Setup only
- **User Story 2 (Phase 4)**: Depends on User Story 1's T003 (same template region is edited
  again in T005) — not independent at the code level, though independently *testable* per its own
  acceptance scenarios
- **Polish (Phase 5)**: Depends on both user stories being complete

### Within Each User Story

- Write the test task before the implementation task; confirm it fails against the current
  template, then implement to make it pass

### Parallel Opportunities

- T002 (US1 test) and T004 (US2 test) touch the same spec file but different `it(...)` blocks —
  they can be authored in either order but are not meaningfully parallelizable by separate
  agents/people since they land in one file; marked `[P]` here only to indicate no *code*
  dependency between the two test bodies themselves
- T006, T007 (Polish test runs) can run in parallel with each other

---

## Implementation Strategy

### MVP First (User Story 1 Only)

1. Complete Phase 1 (Setup)
2. Complete Phase 3 (User Story 1) — the chart already renders full width; this alone is a
   shippable, visible improvement even before User Story 2 lands
3. **STOP and VALIDATE**: confirm T002 passes and the chart visually spans the container

### Incremental Delivery

1. Setup → baseline confirmed
2. User Story 1 → chart full width → validate → (optional) demo
3. User Story 2 → AI Insights stacked full width between chart and table → validate → demo
4. Polish → full regression run + manual quickstart walkthrough
