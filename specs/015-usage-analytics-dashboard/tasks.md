---

description: "Task list for 015-usage-analytics-dashboard"
---

# Tasks: Usage Analytics Dashboard

**Input**: Design documents from `/specs/015-usage-analytics-dashboard/`

**Prerequisites**: plan.md, spec.md, research.md, data-model.md, contracts/ui-contract.md, quickstart.md

**Tests**: Test tasks ARE included — plan.md → Technical Context ("Testing": Vitest pure-derivation
specs plus a `TestBed` component spec), plan.md → Project Structure (three NEW `*.spec.ts` files),
and quickstart.md → "Automated tests" all specify them explicitly.

**Organization**: Tasks are grouped by user story so each story is independently implementable and
testable.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Can run in parallel (different files, no dependencies)
- **[Story]**: Which user story this task belongs to (US1, US2, US3, US4)
- Every task names its exact file path

## Path Conventions

Web application monorepo (`backend/` + `frontend/`). This feature is **frontend-only**; all source
paths are under `frontend/src/app/features/usage-analytics/`. No backend file, no
`contracts/openapi.yaml` change, no `api-client` regeneration.

---

## Phase 1: Setup (Shared Infrastructure)

**Purpose**: Confirm the existing surfaces this feature builds on, before writing any code

- [X] T001 Confirm the generated API surface is present and unchanged: `getUsageAnalytics()` in `frontend/src/app/api-client/api/exchangeRateUsageAnalytics.service.ts` and the `currencyCode` / `queryCount` / `lastQueriedAt` fields of `frontend/src/app/api-client/model/currencyUsageEntry.ts`. Do **not** run `npm run generate:api` (quickstart.md → Prerequisites).
- [X] T002 [P] Confirm the current placeholder component in `frontend/src/app/features/usage-analytics/usage-analytics.ts` and its route entry `usage-analytics` in `frontend/src/app/app.routes.ts`, plus the nav link in `frontend/src/app/shell/shell.html` — record the exported class name so the rewrite keeps the route address and lazy `loadComponent` target intact (FR-020).
- [X] T003 [P] Copy the design-token block (`--surface`, `--border`, `--muted`, `--accent`, …), the `max-width: 1180px` container rule and the `900px`/`640px` breakpoints from `frontend/src/app/features/rate-lookup/rate-lookup.css` and `frontend/src/app/features/historical-rates/historical-rates.css` into a new `frontend/src/app/features/usage-analytics/usage-analytics.css` as the page's base layer (research.md §8, FR-018).

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: The shared derivation module skeleton, the page shell with its data retrieval and state
machine, and the component test harness — every user story builds on these

**⚠️ CRITICAL**: No user story work can begin until this phase is complete

- [X] T004 Create `frontend/src/app/features/usage-analytics/usage-metrics.ts` with the exported view-model types and display-cap constants only (no derivation bodies yet): `UsageSummary`, `RankedUsageRow`, `BreakdownView`, `RecentActivityEntry`, `BREAKDOWN_ROW_LIMIT = 10`, `RECENT_ENTRY_LIMIT = 8` — field shapes exactly per data-model.md §2. Module must be dependency-free and take `readonly CurrencyUsageEntry[]` inputs (Principle VI, research.md §9).
- [X] T005 [P] Add the shared count formatter to `frontend/src/app/features/usage-analytics/usage-metrics.ts`: a single module-level `new Intl.NumberFormat()` instance exported as `formatCount(value: number): string` — no rounding, no abbreviation, no truncation (FR-019, data-model.md §4, research.md §5).
- [X] T006 Rewrite `frontend/src/app/features/usage-analytics/usage-analytics.ts` as the standalone page shell: inject `ExchangeRateUsageAnalyticsService`, one `rxResource({ stream: () => service.getUsageAnalytics().pipe(timeout({ each: 10_000 })) })` called with **no** `limit` and **no** `recentDays`, capture `now = new Date()` once at construction, and expose the four mutually exclusive states (loading / error / empty / populated) as computed signals (research.md §3, data-model.md §5, ui-contract §Backend calls).
- [X] T007 Add the loading and error blocks to the `usage-analytics.ts` template in `frontend/src/app/features/usage-analytics/usage-analytics.ts`: `[data-testid="usage-loading"]` as the sole data-area content while in flight, and `[data-testid="usage-error"]` as one clear message replacing all three data sections for HTTP failure and the 10 s timeout alike — never zeros or partial data (FR-014, FR-015, FR-015a).
- [X] T008 [P] Style the loading and error states plus the shared card/panel border and spacing rules in `frontend/src/app/features/usage-analytics/usage-analytics.css` (FR-018).
- [X] T009 Create the component test harness `frontend/src/app/features/usage-analytics/usage-analytics.spec.ts`: `TestBed` with `ExchangeRateUsageAnalyticsService` replaced by `{ getUsageAnalytics: vi.fn() }`, following `frontend/src/app/features/historical-rates/historical-rates.spec.ts`; add specs asserting exactly one call with no arguments, the loading state, the HTTP-failure error state (`throwError`), and the 10 s timeout error state (never-emitting `Subject` + Vitest fake timers) (research.md §9, quickstart.md → Automated tests).

**Checkpoint**: Page shell renders loading/error states against a stubbed service; derivation module
types exist — user stories can now proceed.

---

## Phase 3: User Story 1 - Grasp overall query activity at a glance (Priority: P1) 🎯 MVP

**Goal**: Page title + one-line subtitle above a row of three bordered KPI cards showing total
queries, distinct queried currencies, and the most-queried currency with its count — all computed
over the complete, uncapped response.

**Independent Test**: With known usage data recorded, open the page and check the title, subtitle and
the three KPI values against the raw `GET /exchange/usage` payload: sum of every `queryCount`, count
of entries with `queryCount > 0`, and the highest-count currency with its count (alphabetically first
on a tie). Reload repeatedly — identical every time.

### Tests for User Story 1 ⚠️

> Write these first and confirm they fail before implementing

- [X] T010 [P] [US1] Create `frontend/src/app/features/usage-analytics/usage-metrics.spec.ts` with `computeUsageSummary` specs: total over **all** entries (including entries a 10-row cap would drop), `queriedCurrencyCount` excluding `queryCount === 0`, `mostQueried` highest-count selection, alphabetical tie-break, `mostQueried === null` when nothing was ever queried, and the empty-array case (data-model.md §2.1, US1 scenarios 1–4).

### Implementation for User Story 1

- [X] T011 [US1] Implement `computeUsageSummary(entries: readonly CurrencyUsageEntry[]): UsageSummary` in `frontend/src/app/features/usage-analytics/usage-metrics.ts` — sum, `queryCount > 0` count, and `mostQueried` by `queryCount` DESC then `currencyCode` ASC on a **copy** of the array, computed before any display cap (FR-003 … FR-005a, INV-2, INV-6).
- [X] T012 [US1] Add the page header to the template in `frontend/src/app/features/usage-analytics/usage-analytics.ts`: a single `<h1>` identifying the page as usage/query analytics plus a one-line subtitle paragraph stating it is an overview of query activity, positioned above all data sections (FR-001).
- [X] T013 [US1] Add the KPI section to the template in `frontend/src/app/features/usage-analytics/usage-analytics.ts`: `section[data-testid="kpi-row"]` with an `<h2>` "Summary" referenced via `aria-labelledby`, containing exactly three cards — `[data-testid="kpi-total-queries"]`, `[data-testid="kpi-queried-currencies"]`, `[data-testid="kpi-most-queried"]` — each value rendered through `formatCount` and the most-queried card rendering an explicit empty indication (not blank, not `0`) when `mostQueried` is `null` (FR-002, FR-005, FR-013, FR-019, FR-024).
- [ ] T014 [P] [US1] Style the KPI row in `frontend/src/app/features/usage-analytics/usage-analytics.css`: `grid-template-columns: repeat(3, 1fr)`, bordered cards on the token set, generous spacing, and tabular numerals so very large counts stay inside their card without truncation (FR-002, FR-018, FR-019).
- [ ] T015 [US1] Extend `frontend/src/app/features/usage-analytics/usage-analytics.spec.ts` with populated-state and nothing-queried-state specs asserting the rendered KPI values against the stubbed payload, including that the KPI totals reflect currencies beyond the top 10 (FR-005a, SC-003).

**Checkpoint**: User Story 1 fully functional and independently testable — the page delivers the
headline totals on its own.

---

## Phase 4: User Story 2 - Compare query volume across currencies (Priority: P1)

**Goal**: A wider left-hand "Activity breakdown" panel ranking up to 10 queried currencies as code +
proportional bar + count, with a "top N of M" indication and a never-queried footnote.

**Independent Test**: With usage data covering several currencies at different volumes plus some
never queried, verify rows descend by count (ties alphabetical), no zero-count currency appears, each
row shows code/bar/count, the top bar is full width and a half-count currency's bar is roughly half,
and the footnote's never-queried figure matches the payload.

### Tests for User Story 2 ⚠️

- [ ] T016 [P] [US2] Add `buildBreakdownView` specs to `frontend/src/app/features/usage-analytics/usage-metrics.spec.ts`: zero-count exclusion, `queryCount` DESC then `currencyCode` ASC ordering, the 10-row cap, `proportionPercent` against the highest **displayed** count (top row = 100, all-tied = all 100, single row = 100, 2-dp rounding), `queriedTotal` vs `displayedCount`, `neverQueriedCount` counted over all entries, and INV-3 (`queryCount ≥ 1` on every row) / INV-4 (`neverQueriedCount + queriedCurrencyCount === entries.length`) (data-model.md §2.2, US2 scenarios 1–6).

### Implementation for User Story 2

- [ ] T017 [US2] Implement `buildBreakdownView(entries: readonly CurrencyUsageEntry[]): BreakdownView` in `frontend/src/app/features/usage-analytics/usage-metrics.ts` — filter `queryCount > 0`, sort a copy DESC/ASC, cap at `BREAKDOWN_ROW_LIMIT`, compute `proportionPercent` rounded to 2 dp, and carry `displayedCount`, `queriedTotal`, `neverQueriedCount` (FR-006 … FR-009a, INV-3, INV-6).
- [ ] T018 [US2] Create the presentational component `frontend/src/app/features/usage-analytics/usage-breakdown-panel.ts`: standalone, selector `app-usage-breakdown-panel`, a single `input()` of `BreakdownView`, no outputs; renders `<section>` + `<h2>` "Activity breakdown" via `aria-labelledby`, `[data-testid="breakdown-row"][data-code="XXX"]` rows with code text, bar and `formatCount`ed count text, the conditional "top N of M" indication, `[data-testid="never-queried-footnote"]` (omitted or explicitly zero when none), and `[data-testid="breakdown-empty"]` when there are no rows (FR-006 … FR-009a, FR-013, FR-022, FR-024).
- [ ] T019 [US2] Render the bar in `frontend/src/app/features/usage-analytics/usage-breakdown-panel.ts` as `[data-testid="breakdown-bar"][aria-hidden="true"]` — a track element with an inner fill bound via `[style.width.%]="row.proportionPercent"`, no `role="progressbar"`, no `aria-label`, no value conveyed by length alone (FR-008, FR-023, INV-5, research.md §6).
- [ ] T020 [P] [US2] Create `frontend/src/app/features/usage-analytics/usage-breakdown-panel.css`: panel border/padding on the shared token set, row grid (label / bar track / count), bar track + accent fill with a minimum visible fill width, and a visually subordinate footnote treatment (FR-009a, FR-018, research.md §6, §8).
- [ ] T021 [US2] Wire `UsageBreakdownPanel` into the populated branch of `frontend/src/app/features/usage-analytics/usage-analytics.ts`, passing `buildBreakdownView(entries)` computed from the same single response as the KPIs (FR-005a, ui-contract behavioral rule 1).
- [ ] T022 [US2] Extend `frontend/src/app/features/usage-analytics/usage-analytics.spec.ts` with breakdown rendering specs: row order and count, no zero-count row, footnote figure, "top 10 of M" indication with >10 queried currencies, and the empty-state-with-footnote case (US2 scenarios 3–6).

**Checkpoint**: User Stories 1 and 2 both work independently.

---

## Phase 5: User Story 3 - Check the latest query activity (Priority: P2)

**Goal**: A narrower right-hand "Recent activity" panel listing the 8 most recently queried
currencies, newest first, each as a code plus an elapsed-time phrase carrying a machine-readable
instant and a local absolute date-time on inspection.

**Independent Test**: With usage data whose `lastQueriedAt` values differ (some minutes old, some
days old, one never queried, one future-stamped), verify entries run newest first, each shows a
plausible elapsed phrase, the `<time datetime>` holds the raw ISO instant, the tooltip gives the
local absolute date-time, never-queried currencies are absent, and the future instant reads as the
just-now phrase.

### Tests for User Story 3 ⚠️

- [ ] T023 [P] [US3] Create `frontend/src/app/features/usage-analytics/relative-time.spec.ts`: every unit-ladder threshold (`< 60 s` → the distinct just-now literal, `< 60 min` minutes, `< 24 h` hours, `< 30 d` days, `< 12 mo` months, else years), the future-instant clock-skew clamp to just-now, the unparseable-instant case, and the local absolute date-time string — all as pure `(instant, now)` calls with no fake timers (data-model.md §3, research.md §4).
- [ ] T024 [P] [US3] Add `buildRecentActivity` specs to `frontend/src/app/features/usage-analytics/usage-metrics.spec.ts`: `lastQueriedAt === null` exclusion (including a currency with `queryCount > 0` but a null timestamp), `lastQueriedAt` DESC then `currencyCode` ASC ordering, the 8-entry cap, and the empty case (data-model.md §2.3, US3 scenarios 1–4).

### Implementation for User Story 3

- [ ] T025 [P] [US3] Create `frontend/src/app/features/usage-analytics/relative-time.ts`: a declared, ordered threshold table (not an `if` chain — Principle VII) driving `Intl.RelativeTimeFormat(undefined, { numeric: 'auto' })`, exporting `relativePhrase(instant: string, now: Date): string` with the future clamp, and `absoluteLocal(instant: string): string` via `Intl.DateTimeFormat(undefined, { dateStyle: 'medium', timeStyle: 'short' })`. Pure functions, no dependency (FR-012, FR-012a, data-model.md §3).
- [ ] T026 [US3] Implement `buildRecentActivity(entries: readonly CurrencyUsageEntry[], now: Date): RecentActivityEntry[]` in `frontend/src/app/features/usage-analytics/usage-metrics.ts` — drop null/unparseable `lastQueriedAt`, sort a copy DESC then code ASC, cap at `RECENT_ENTRY_LIMIT`, and attach `relativePhrase` / `absoluteLocal` from `relative-time.ts` while keeping `lastQueriedAt` verbatim for the `datetime` attribute (FR-010 … FR-012a, FR-025).
- [ ] T027 [US3] Create the presentational component `frontend/src/app/features/usage-analytics/recent-activity-panel.ts`: standalone, selector `app-recent-activity-panel`, a single `input()` of `RecentActivityEntry[]`, no outputs; renders `<section>` + `<h2>` "Recent activity" via `aria-labelledby`, `[data-testid="recent-entry"][data-code="XXX"]` entries as code text plus `<time [attr.datetime]="entry.lastQueriedAt" [title]="entry.absoluteLocal">{{ entry.relativePhrase }}</time>`, and `[data-testid="recent-empty"]` when there are no entries (FR-010 … FR-013, FR-022, FR-024, FR-025).
- [ ] T028 [P] [US3] Create `frontend/src/app/features/usage-analytics/recent-activity-panel.css`: panel border/padding on the shared token set, entry list spacing and separators, muted time treatment — no focusable or pointer-only affordance (FR-018, FR-026).
- [ ] T029 [US3] Wire `RecentActivityPanel` into the populated branch of `frontend/src/app/features/usage-analytics/usage-analytics.ts`, passing `buildRecentActivity(entries, this.now)` where `now` is the single value captured at construction and never advanced (FR-012, ui-contract behavioral rule 10).
- [ ] T030 [US3] Extend `frontend/src/app/features/usage-analytics/usage-analytics.spec.ts` with recent-activity rendering specs: entry order and cap, the `datetime` attribute holding the raw ISO instant, the `title` holding the local absolute date-time, exclusion of null-timestamp currencies, and the empty-state message (US3 scenarios 1–4, FR-025).

**Checkpoint**: All three data stories are independently functional.

---

## Phase 6: User Story 4 - Read the page comfortably on any screen (Priority: P3)

**Goal**: Clear visual hierarchy — title/subtitle, then the KPI row, then a balanced two-column grid
with the breakdown panel visibly wider than recent activity — stacking in reading order on narrow
viewports with no horizontal scrolling.

**Independent Test**: At a wide viewport confirm the two-column arrangement with the left panel wider
than the right and visible borders/spacing; narrow to 320 px and confirm the panels stack in order
(KPI → breakdown → recent activity), stay readable, and produce no horizontal scrollbar.

### Implementation for User Story 4

- [ ] T031 [US4] Add the two-column grid to `frontend/src/app/features/usage-analytics/usage-analytics.css`: `grid-template-columns: minmax(0, 1.6fr) minmax(0, 1fr)` for the breakdown/recent split, with DOM order already matching the stack order so no `order:` override is needed (FR-016, research.md §8).
- [ ] T032 [US4] Add the responsive collapse to `frontend/src/app/features/usage-analytics/usage-analytics.css`: single column at the existing `900px` breakpoint (and the KPI row's own collapse at `640px`), preserving reading order with no clipped or overlapping content and no horizontal scroll from 320 px to 2560 px (FR-017, SC-005).
- [ ] T033 [US4] Confirm the page container in `frontend/src/app/features/usage-analytics/usage-analytics.css` centers content at `max-width: 1180px; margin: 0 auto`, matching `frontend/src/app/features/rate-lookup/rate-lookup.css`, and that every card and panel carries a visible border with consistent spacing (FR-018, US4 scenario 3).
- [ ] T034 [US4] Verify long content cannot overflow either column in `frontend/src/app/features/usage-analytics/usage-breakdown-panel.css` and `frontend/src/app/features/usage-analytics/recent-activity-panel.css` (min-width-0 / wrapping on the row grid children), using the very-large-count condition from quickstart.md (FR-019, SC-005).

**Checkpoint**: All four user stories complete.

---

## Phase 7: Polish & Cross-Cutting Concerns

- [ ] T035 [P] Run `cd frontend && npm test -- usage` and confirm all three spec files pass: `usage-metrics.spec.ts`, `relative-time.spec.ts`, `usage-analytics.spec.ts`.
- [ ] T036 [P] Run `cd frontend && npm test` to confirm no regression in the existing suite, then `cd frontend && npm run build` for a type-clean production build.
- [ ] T037 Verify determinism (SC-006, INV-6): reload the populated page repeatedly and confirm identical ordering, most-queried currency, entries and phrases; confirm every derivation in `frontend/src/app/features/usage-analytics/usage-metrics.ts` sorts a copy and never mutates the resource value.
- [ ] T038 Accessibility pass per quickstart.md → "Accessibility & counter-safety": heading navigation `<h1>` → "Summary" / "Activity breakdown" / "Recent activity", each breakdown row announced once as code + count, no bar announced, and `Tab` taking focus to nothing on the page (FR-022 … FR-026, SC-008).
- [ ] T039 Counter-safety and call-shape check (FR-021, FR-005a, SC-007): note `curl "$BASE/exchange/usage"`, reload the page 10 times, re-`curl` and confirm every `queryCount` / `lastQueriedAt` is unchanged; in the network panel confirm exactly one `GET /exchange/usage` per load with no `limit` and no `recentDays` parameter.
- [ ] T040 Run the full quickstart.md manual validation (US1 → US4 plus the loading/error/timeout scenarios) against the seeded data conditions, including the >10-currencies, nothing-queried, no-records, clock-skew, hang and very-large-count cases.
- [ ] T041 Confirm the feature's scope boundary held: `git status` shows changes only under `frontend/src/app/features/usage-analytics/` — no edit to `frontend/src/app/app.routes.ts`, `frontend/src/app/shell/shell.html`, `frontend/src/app/api-client/`, `contracts/openapi.yaml` or `backend/` (FR-020, plan.md → Project Structure).

---

## Dependencies & Execution Order

### Phase Dependencies

- **Setup (Phase 1)**: no dependencies — start immediately
- **Foundational (Phase 2)**: depends on Setup — **blocks all user stories**
- **User Story 1 (Phase 3)**: depends on Phase 2 — no dependency on other stories
- **User Story 2 (Phase 4)**: depends on Phase 2 — independently testable; shares `usage-metrics.ts` and the page template with US1, so serialize edits to those two files
- **User Story 3 (Phase 5)**: depends on Phase 2 — independently testable; same shared-file caveat
- **User Story 4 (Phase 6)**: depends on Phase 2 for the page shell; its panel-overflow task (T034) needs the panel stylesheets from US2/US3 to exist
- **Polish (Phase 7)**: depends on all desired stories being complete

### User Story Dependencies

- **US1 (P1)**: independent — delivers the MVP on its own
- **US2 (P1)**: independent of US1's behavior; both write to `usage-metrics.ts` and `usage-analytics.ts`
- **US3 (P2)**: independent of US1/US2's behavior; adds `relative-time.ts` (its own file) plus one function in `usage-metrics.ts`
- **US4 (P3)**: layout only; the data stories render correctly without it

### Within Each User Story

- Tests are written first and must fail before implementation
- Derivation (`usage-metrics.ts` / `relative-time.ts`) before the component that consumes it
- Panel component before wiring it into the page
- Story complete before moving to the next priority

### Parallel Opportunities

- Setup: T002 and T003 in parallel
- Foundational: T005 and T008 in parallel with their siblings
- US1: T010 (spec file) parallel with T011's prep; T014 (CSS) parallel with T013 (template)
- US2: T016 (spec) and T020 (CSS) parallel with the component work
- US3: T023, T024, T025 and T028 are four different files — all parallel
- Polish: T035 and T036 in parallel
- With multiple developers: after Phase 2, one takes US1, one takes US2, one takes US3 — coordinate the two shared files (`usage-metrics.ts`, `usage-analytics.ts`)

---

## Parallel Example: User Story 3

```bash
# Different files, no dependencies — launch together:
Task: "Create relative-time.spec.ts in frontend/src/app/features/usage-analytics/relative-time.spec.ts"
Task: "Add buildRecentActivity specs to frontend/src/app/features/usage-analytics/usage-metrics.spec.ts"
Task: "Create relative-time.ts in frontend/src/app/features/usage-analytics/relative-time.ts"
Task: "Create recent-activity-panel.css in frontend/src/app/features/usage-analytics/recent-activity-panel.css"
```

---

## Implementation Strategy

### MVP First (User Story 1 only)

1. Complete Phase 1: Setup (T001–T003)
2. Complete Phase 2: Foundational (T004–T009) — blocks everything
3. Complete Phase 3: User Story 1 (T010–T015)
4. **STOP and VALIDATE**: run quickstart.md → US1 scenarios against seeded data
5. The page already answers "how much is this system used, and for what?" — demoable

### Incremental Delivery

1. Setup + Foundational → shell with loading/error states
2. + US1 → KPI dashboard (MVP)
3. + US2 → ranked breakdown with footnote
4. + US3 → recent activity panel
5. + US4 → responsive two-column polish
6. + Phase 7 → validated, accessibility-checked, scope-verified

---

## Notes

- [P] = different files, no dependencies
- `usage-metrics.ts` and `usage-analytics.ts` are touched by three stories each — never mark tasks on them `[P]` across stories
- No backend, contract or `api-client` change in this feature; do not run `npm run generate:api`
- Commit after each task or logical group
- Stop at any checkpoint to validate a story independently
