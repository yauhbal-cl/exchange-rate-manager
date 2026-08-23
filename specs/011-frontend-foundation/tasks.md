---

description: "Task list for Frontend Foundation"
---

# Tasks: Frontend Foundation

**Input**: Design documents from `/specs/011-frontend-foundation/`

**Prerequisites**: plan.md, spec.md, research.md, data-model.md, contracts/routes.md, quickstart.md

**Tests**: Not explicitly requested in spec.md's Functional Requirements. `quickstart.md`'s
"Automated checks" section does call for Vitest coverage of shell nav-active-state and route
resolution, so those two test tasks are included under User Story 1 (where that logic lives); no
other test tasks are added.

**Organization**: Tasks are grouped by user story (spec.md priorities P1/P2/P3) to enable
independent implementation and validation of each story.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Can run in parallel (different files, no dependencies)
- **[Story]**: Maps task to US1, US2, or US3
- Paths are relative to repo root; frontend work lives under `frontend/src/app/`

## Path Conventions

Existing Option 2 web-application layout (`backend/` + `frontend/` siblings, per plan.md). All
tasks in this file touch `frontend/` only — no backend changes in this feature.

---

## Phase 1: Setup (Shared Infrastructure)

**Purpose**: Confirm the existing Angular scaffold's toolchain is ready before any feature work

- [X] T001 Run `cd frontend && npm install` to ensure `node_modules/` matches `package.json`/
      `package-lock.json` (Node 22 LTS per CLAUDE.md); confirm `npm start`, `npm test`, and
      `npm run generate:api` scripts are present in `frontend/package.json` (they already are —
      this is a sanity check before building on top of them, not a script-authoring task)

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: Shared infrastructure every user story's views build on — MUST complete first

**⚠️ CRITICAL**: No user story work can begin until this phase is complete

- [X] T002 Run `cd frontend && npm run generate:api` to regenerate `frontend/src/app/api-client/`
      fresh from `contracts/openapi.yaml`, and confirm `ExchangeRateLookupService`
      (`getExchangeRate`), `ExchangeRateUsageAnalyticsService` (`getUsageAnalytics`), and
      `ExchangeRateAIInsightService` (`getExchangeRateTrendInsight`) are exported from
      `frontend/src/app/api-client/index.ts` — every Phase 3 (US1) feature view depends on these
      generated services existing and matching the current contract

**Checkpoint**: Foundation ready — user story implementation can now begin

---

## Phase 3: User Story 1 - Navigate between core business views (Priority: P1) 🎯 MVP

**Goal**: A persistent nav lets a user switch between rate-lookup, usage-analytics, and ai-insight
views without a full page reload; base address defaults to rate-lookup; unknown addresses show a
not-found state; the nav always indicates the active view and is keyboard/screen-reader operable.

**Independent Test**: Load the app, confirm rate-lookup renders by default with its nav entry
marked active; click the other two nav entries in turn and confirm each view loads without a full
page reload and the nav's active indicator moves; navigate directly to `/usage-analytics` and
confirm it loads directly; navigate to `/nope` and confirm a clear not-found state renders with nav
still visible.

### Implementation for User Story 1

- [X] T003 [P] [US1] Create not-found standalone component in
      `frontend/src/app/not-found/not-found.ts` (class `NotFound`, inline template, minimal
      Tailwind-utility-styled "page not found" message) — wildcard route target (FR-005)

- [X] T004 [P] [US1] Create rate-lookup standalone component in
      `frontend/src/app/features/rate-lookup/rate-lookup.ts` (class `RateLookup`, inline
      template). Inject `ExchangeRateLookupService`, use `rxResource` (from
      `@angular/core/rxjs-interop`) with a fixed default pair (`from: 'USD'`, `to: 'EUR'`) calling
      `getExchangeRate(from, to)`. Render three mutually-exclusive states off the resource's
      `.isLoading()`, `.error()`, `.value()` signals: loading text, an error message on failure,
      and on success the `ExchangeRateResponse` fields (`fromCurrency`, `toCurrency`, `rate`,
      `rateDate`) — this view's own error/unavailable state per FR-014, independent of the shell

- [X] T005 [P] [US1] Create usage-analytics standalone component in
      `frontend/src/app/features/usage-analytics/usage-analytics.ts` (class `UsageAnalytics`,
      inline template). Inject `ExchangeRateUsageAnalyticsService`, use `rxResource` calling
      `getUsageAnalytics()` with no arguments. Render loading/error/data states off the resource's
      signals; on success, list the `UsageAnalyticsResponse.currencies` array
      (`CurrencyUsageEntry.currencyCode`, `.queryCount`, `.lastQueriedAt`) — own error/unavailable
      state per FR-014

- [X] T006 [P] [US1] Create ai-insight standalone component in
      `frontend/src/app/features/ai-insight/ai-insight.ts` (class `AiInsight`, inline template).
      Inject `ExchangeRateAIInsightService`, use `rxResource` with a fixed default pair
      (`from: 'USD'`, `to: 'EUR'`) calling `getExchangeRateTrendInsight(from, to)`. Render
      loading/error/data states off the resource's signals; on success, show the
      `TrendInsightResponse` fields (`narrative`, `startDate`, `endDate`) — own error/unavailable
      state per FR-014 (this endpoint also 503s when the AI backend is down; treat that the same as
      any other resource error)

- [X] T007 [P] [US1] Create app shell component: `frontend/src/app/shell/shell.ts` (class `Shell`,
      selector `app-shell`), `frontend/src/app/shell/shell.html`, `frontend/src/app/shell/
      shell.css`. Template: a `<nav>` with three `routerLink` entries pointing at `/rate-lookup`,
      `/usage-analytics`, `/ai-insight` (labels: "Rate Lookup", "Usage Analytics", "AI Insight"),
      each using the `routerLinkActive` directive's `ariaCurrentWhenActive="page"` input (built-in
      Angular Router support — no manual `aria-current` binding needed) so the active entry is
      both visually indicated and exposed to assistive tech (FR-001); nav entries are plain
      `<a>`/`routerLink` anchors so they are keyboard-operable by default. Below the nav, a
      `<router-outlet />` renders the active routed view.

- [X] T008 [US1] Populate `frontend/src/app/app.routes.ts` with the route table from
      `contracts/routes.md`: `{ path: '', pathMatch: 'full', redirectTo: 'rate-lookup' }`, three
      lazy `{ path: '<segment>', loadComponent: () => import('./features/<view>/<view>').then(m =>
      m.<ClassName>) }` entries for rate-lookup/usage-analytics/ai-insight, and `{ path: '**',
      loadComponent: () => import('./not-found/not-found').then(m => m.NotFound) }`. Depends on
      T003-T006 (component files must exist at these import paths).

- [X] T009 [US1] Rewrite `frontend/src/app/app.ts` to a thin root component whose template is just
      `<app-shell />` (import `Shell` from `./shell/shell`), removing the current inline
      status-check template/`StatusService` call/`OnInit` logic. Delete the now-orphaned
      `frontend/src/app/app.html` and `frontend/src/app/app.css` (dead files — `App`'s
      `@Component` decorator uses an inline `template`, not `templateUrl`/`styleUrl`, so neither
      file is referenced). Depends on T007 (shell component must exist to reference `<app-shell>`).

### Tests for User Story 1 (per quickstart.md "Automated checks")

- [X] T010 [P] [US1] Add Vitest test `frontend/src/app/shell/shell.spec.ts` verifying that when the
      router's active URL matches a nav entry's `routerLink`, that entry's rendered anchor carries
      `aria-current="page"` (via `routerLinkActive`'s `ariaCurrentWhenActive`) and the other two
      entries do not. Depends on T007.

- [X] T011 [P] [US1] Add Vitest test `frontend/src/app/app.routes.spec.ts` verifying `app.routes.ts`
      resolves the empty path (`''`) to a redirect targeting `rate-lookup`, and the wildcard path
      (`'**'`) resolves to the `not-found` route entry. Depends on T008.

**Checkpoint**: User Story 1 is fully functional and independently testable — this is the MVP.

---

## Phase 4: User Story 2 - Point the application at different backend environments (Priority: P2)

**Goal**: Confirm the backend base address is switchable via environment configuration only, with
zero source-code edits, and that a documented local-dev default applies when none is overridden.
The mechanism (`environment.ts`/`environment.production.ts` + `BASE_PATH` DI token in
`app.config.ts`) already exists per plan.md — this phase validates it, it does not rebuild it.

**Independent Test**: Edit only `frontend/src/environments/environment.ts`'s `apiBaseUrl`, restart
the app, and confirm outgoing API calls target the new address while `git diff --stat` shows only
that one file changed.

- [X] T012 [US2] Execute quickstart.md Scenario 2 end-to-end: stop the app; edit
      `frontend/src/environments/environment.ts`'s `apiBaseUrl` to a different backend address
      (e.g. a placeholder host); run `git diff --stat` and confirm only `environment.ts` appears;
      run `cd frontend && npm start` and confirm (via browser dev tools Network tab) the
      rate-lookup view's API call targets the new address; then revert the edit back to
      `http://localhost:8080` — validates FR-007/FR-008/SC-002. Run after Phase 3, since it needs
      a view that actually issues an API call to observe in the Network tab.

- [ ] T013 [P] [US2] Confirm `frontend/src/environments/environment.ts` still has a non-empty
      local-dev default `apiBaseUrl` (`http://localhost:8080`) and
      `frontend/src/environments/environment.production.ts` still has `/api` (FR-009); confirm
      `README.md`'s "Environment Configuration" section still accurately describes both files as
      the sole source of the backend base address.

**Checkpoint**: User Stories 1 and 2 both work independently.

---

## Phase 5: User Story 3 - Keep the frontend API layer consistent with the backend contract (Priority: P3)

**Goal**: Confirm the generated API layer stays the sole path for backend calls and regenerates
cleanly from `contracts/openapi.yaml` with no manual follow-up. The generation mechanism (npm
script `generate:api`) already exists per plan.md — this phase validates it against the
now-complete application (all three views built in Phase 3), it does not rebuild it.

**Independent Test**: Run `cd frontend && npm run generate:api` against the current contract and
confirm a complete, compiling API layer is produced with no manual edits needed, and that no
hand-written `HttpClient` calls exist anywhere in application code outside `api-client/`.

- [ ] T014 [US3] Execute quickstart.md Scenario 3 step 1-2: run `cd frontend && npm run
      generate:api` again, confirm the command completes and `frontend/src/app/api-client/` is
      fully rewritten, and confirm `cd frontend && npm run build` still compiles with no errors
      (no manual follow-up edits needed) — validates FR-010/FR-011/SC-003.

- [ ] T015 [P] [US3] Run `git diff --stat frontend/src/app/api-client/` after the T014
      regeneration and confirm the changes are generator output only — no hand-authored diff
      survives inside that directory — validates FR-012.

- [ ] T016 [P] [US3] Run `grep -rn "HttpClient" frontend/src/app --include=*.ts | grep -v
      api-client` and confirm zero matches outside `api-client/`, confirming the rate-lookup,
      usage-analytics, and ai-insight views built in Phase 3 all call the backend exclusively
      through their injected generated `*Service` classes — validates FR-013/SC-004. Run after
      Phase 3, since it checks the views built there.

**Checkpoint**: All three user stories are independently functional.

---

## Phase 6: Polish & Cross-Cutting Concerns

**Purpose**: Final validation spanning all user stories

- [ ] T017 [P] Run `cd frontend && npm test` and confirm all Vitest tests, including T010 and
      T011, pass.

- [ ] T018 [P] Manually validate the backend-unreachable edge case per quickstart.md ("Edge case —
      backend unreachable"): stop the backend, load the app, click through all three nav entries,
      and confirm the shell/nav stay visible and responsive throughout while each view's own data
      section shows a clear error/unavailable message — validates FR-014/SC-005.

- [ ] T019 [P] Manually validate SC-006: using keyboard only (Tab/Shift+Tab/Enter), reach and
      activate all three nav entries without a mouse; using a screen reader (or browser
      accessibility inspector), confirm the currently active entry exposes `aria-current="page"`.

- [ ] T020 Run `cd frontend && npm run build` (production configuration) and confirm the build
      succeeds within the existing `angular.json` bundle budgets, with no compile errors.

---

## Dependencies & Execution Order

### Phase Dependencies

- **Setup (Phase 1)**: No dependencies — start immediately.
- **Foundational (Phase 2)**: Depends on Setup — BLOCKS all user stories.
- **User Story 1 (Phase 3)**: Depends on Foundational. No dependency on US2/US3.
- **User Story 2 (Phase 4)**: Depends on Foundational; T012 additionally sequenced after Phase 3
  purely to have a live API call to observe (the FR-007/008/009 mechanism itself has no code
  dependency on US1).
- **User Story 3 (Phase 5)**: Depends on Foundational; T016 additionally sequenced after Phase 3 so
  the grep check covers the views built there.
- **Polish (Phase 6)**: Depends on Phases 3-5 being complete.

### Within User Story 1

- T003, T004, T005, T006, T007 have no dependencies on each other — all parallelizable.
- T008 depends on T003-T006 (imports their file paths).
- T009 depends on T007 (references `<app-shell>`).
- T010 depends on T007; T011 depends on T008.

### Parallel Opportunities

- T003, T004, T005, T006, T007 (5 tasks, 5 distinct files, no cross-imports) can all run in
  parallel once Phase 2 is done.
- T010 and T011 can run in parallel once their respective dependencies (T007, T008) land.
- T013 (US2) and T015/T016 (US3) can run in parallel with each other and with Polish tasks that
  don't depend on them.
- T017, T018, T019 in Polish can run in parallel; T020 has no code dependency on them either.

---

## Parallel Example: User Story 1

```bash
# Once Phase 2 (Foundational) is done, launch all five independent US1 files together:
Task: "Create not-found component in frontend/src/app/not-found/not-found.ts"
Task: "Create rate-lookup component in frontend/src/app/features/rate-lookup/rate-lookup.ts"
Task: "Create usage-analytics component in frontend/src/app/features/usage-analytics/usage-analytics.ts"
Task: "Create ai-insight component in frontend/src/app/features/ai-insight/ai-insight.ts"
Task: "Create shell component in frontend/src/app/shell/shell.ts + shell.html + shell.css"
```

---

## Implementation Strategy

### MVP First (User Story 1 Only)

1. Complete Phase 1: Setup.
2. Complete Phase 2: Foundational (regenerate `api-client/`).
3. Complete Phase 3: User Story 1 (shell, nav, 3 views, routes, tests).
4. **STOP and VALIDATE**: Walk through User Story 1's Independent Test above.
5. This is a demoable navigable shell even before Phases 4-5 run.

### Incremental Delivery

1. Setup + Foundational → generated API layer confirmed current.
2. User Story 1 → navigable shell with 3 real (error-isolated) views — MVP.
3. User Story 2 → confirm environment-driven backend targeting still holds with the new views live.
4. User Story 3 → confirm generated-API-only discipline holds across the whole app, contract
   regeneration still clean.
5. Polish → full automated + manual validation pass (accessibility, offline backend, prod build).
