---

description: "Task list for Exchange Rate Calculator View"
---

# Tasks: Exchange Rate Calculator View

**Input**: Design documents from `/specs/012-exchange-rate-calculator/`

**Prerequisites**: plan.md, spec.md, research.md, data-model.md, contracts/ui-contract.md, quickstart.md

**Tests**: Included — plan.md explicitly lists `rate-lookup.spec.ts` as a deliverable and quickstart.md's "Automated check" exercises the validation rules, request-gating, error categories, and stale-response discarding.

**Organization**: All work lands in `frontend/src/app/features/rate-lookup/` only (existing routed placeholder). Tasks are grouped by user story per spec.md priorities (P1/P2/P3).

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Can run in parallel (different files, no dependencies)
- **[Story]**: US1 / US2 / US3 per spec.md
- Every task states an exact file path

## Path Conventions

Frontend-only feature (Option 2 web-app layout, frontend half): all paths rooted at
`frontend/src/app/features/rate-lookup/`.

---

## Phase 1: Setup

**Purpose**: Confirm the generated API surface this feature depends on is current

- [X] T001 Run `cd frontend && npm run generate:api` to regenerate `frontend/src/app/api-client/` from `contracts/openapi.yaml`, confirming `ExchangeRateLookupService`, `ExchangeRateResponse`, and `ProblemDetail` are present and current

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: Shared constant + component skeleton every user story builds on

**⚠️ CRITICAL**: No user story work can begin until this phase is complete

- [X] T002 [P] Create `CURRENCY_CODES: readonly string[]` constant in `frontend/src/app/features/rate-lookup/currencies.ts` per research.md §5 (USD + backend spread-table currencies + common majors)
- [X] T003 Replace the hardcoded placeholder in `frontend/src/app/features/rate-lookup/rate-lookup.ts` with signal-based state: `fromCurrency`, `toCurrency`, `date` signals (all `''` initial) and a `submittedRequest = signal<RateLookupRequest | undefined>(undefined)` signal; rewire the existing `rate = rxResource(...)` to key its `stream` off `submittedRequest()`, returning no request (no backend call) when it's `undefined` (depends on T002)
- [X] T004 [P] Create test scaffold in `frontend/src/app/features/rate-lookup/rate-lookup.spec.ts`: TestBed setup for `RateLookup` with `ExchangeRateLookupService` provided as a spy/mock

**Checkpoint**: Foundation ready — user story implementation can now begin

---

## Phase 3: User Story 1 - Look up current spread-adjusted rate (Priority: P1) 🎯 MVP

**Goal**: User picks two currencies, submits, sees spread-adjusted rate, rate date, and usage counts

**Independent Test**: Select two distinct valid currencies, submit, verify a rate and rate date render (per spec.md's Independent Test for US1)

### Tests for User Story 1

- [X] T005 [P] [US1] Component test in `frontend/src/app/features/rate-lookup/rate-lookup.spec.ts`: selecting USD/EUR and submitting calls `getExchangeRate('USD','EUR', undefined)` exactly once and renders `fromCurrency`, `toCurrency`, `rate` (verbatim string), `rateDate`, both usage counts
- [X] T006 [P] [US1] Component test in `frontend/src/app/features/rate-lookup/rate-lookup.spec.ts`: after a successful lookup, changing the target currency and resubmitting replaces the previous result rather than appending to it

### Implementation for User Story 1

- [X] T007 [US1] Render source (`select[name="from"]`) and target (`select[name="to"]`) currency dropdowns bound to `fromCurrency`/`toCurrency` signals, options from `CURRENCY_CODES` with an empty placeholder option first, in `frontend/src/app/features/rate-lookup/rate-lookup.ts` template
- [X] T008 [US1] Implement the submit handler in `frontend/src/app/features/rate-lookup/rate-lookup.ts`: build `{ from: fromCurrency(), to: toCurrency(), date: undefined }` and write it to `submittedRequest` (depends on T003, T007)
- [X] T009 [US1] Wire the `rxResource` `stream` in `frontend/src/app/features/rate-lookup/rate-lookup.ts` to call `exchangeRateLookupService.getExchangeRate(req.from, req.to, req.date)` using the current `submittedRequest()` value (depends on T008)
- [X] T010 [US1] Render a loading indicator when `rate.isLoading()` and bind `button[type="submit"]` `disabled` to `rate.isLoading()`, in `frontend/src/app/features/rate-lookup/rate-lookup.ts` template (FR-005, SC-004)
- [X] T011 [US1] Render the success result block (`fromCurrency`, `toCurrency`, `rate` shown verbatim as a string — no `Number()`/`parseFloat`, `rateDate`, `fromCurrencyUsageCount`, `toCurrencyUsageCount`) when `rate.value()` is set and `rate.error()` is not, in `frontend/src/app/features/rate-lookup/rate-lookup.ts` template (FR-006)

**Checkpoint**: User Story 1 is fully functional and independently testable — valid pair in, rate out

---

## Phase 4: User Story 2 - Look up rate for a specific historical date (Priority: P2)

**Goal**: Optional date input lets the user request the rate as of a specific past date

**Independent Test**: Submit currency pair with a past date that has stored data, verify returned rate date matches the requested date (per spec.md)

### Tests for User Story 2

- [X] T012 [P] [US2] Component test in `frontend/src/app/features/rate-lookup/rate-lookup.spec.ts`: entering a past date and submitting calls `getExchangeRate('USD','EUR','2026-01-15')` with the date passed through
- [X] T013 [P] [US2] Component test in `frontend/src/app/features/rate-lookup/rate-lookup.spec.ts`: leaving the date field blank and submitting calls `getExchangeRate` with `date` omitted/`undefined`, never `''` (FR-007)

### Implementation for User Story 2

- [X] T014 [US2] Render the optional date input (`input[type="date"][name="date"]`) bound to the `date` signal, with its `max` attribute bound to today's `yyyy-MM-dd`, in `frontend/src/app/features/rate-lookup/rate-lookup.ts` template
- [X] T015 [US2] Extend the submit handler in `frontend/src/app/features/rate-lookup/rate-lookup.ts` so `submittedRequest.date` is `date() || undefined` (never an empty string), per data-model.md's `RateLookupRequest` (depends on T008, T014)
- [X] T016 [US2] Confirm the `rxResource` stream call in `frontend/src/app/features/rate-lookup/rate-lookup.ts` passes `req.date` through to `getExchangeRate` only when defined (depends on T009, T015)

**Checkpoint**: User Stories 1 AND 2 both work independently — date-scoped lookups now supported

---

## Phase 5: User Story 3 - Understand invalid input or lookup failure (Priority: P3)

**Goal**: Clear inline feedback for invalid input and backend failures, with the form staying usable

**Independent Test**: Submit with source == target, submit with an unknown currency code, and submit for a date with no shared data; verify each shows a distinct, human-readable message and the form remains usable (per spec.md)

### Tests for User Story 3

- [X] T017 [P] [US3] Component test in `frontend/src/app/features/rate-lookup/rate-lookup.spec.ts`: selecting identical currencies shows a validation message and `getExchangeRate` is never called (FR-002, FR-004)
- [X] T018 [P] [US3] Component test in `frontend/src/app/features/rate-lookup/rate-lookup.spec.ts`: submitting with a currency field unselected shows a validation message and `getExchangeRate` is never called (FR-002, FR-004)
- [X] T019 [P] [US3] Component test in `frontend/src/app/features/rate-lookup/rate-lookup.spec.ts`: entering a date after today shows a validation message and `getExchangeRate` is never called (FR-003, FR-004)
- [X] T020 [P] [US3] Component test in `frontend/src/app/features/rate-lookup/rate-lookup.spec.ts`: a `400` response (with `ProblemDetail.detail`) renders the `invalid` category message verbatim from `detail` (FR-008)
- [X] T021 [P] [US3] Component test in `frontend/src/app/features/rate-lookup/rate-lookup.spec.ts`: a `404` response (with `ProblemDetail.detail`) renders the `no-data` category message verbatim from `detail` (FR-008)
- [X] T022 [P] [US3] Component test in `frontend/src/app/features/rate-lookup/rate-lookup.spec.ts`: a network failure / status `0` renders the `unreachable` category with the fixed fallback message, and the form/submit remain usable (FR-008, FR-009)
- [X] T023 [P] [US3] Component test in `frontend/src/app/features/rate-lookup/rate-lookup.spec.ts`: re-submitting the same inputs after an error clears the previous error once the new result resolves (FR-009)
- [X] T024 [P] [US3] Component test in `frontend/src/app/features/rate-lookup/rate-lookup.spec.ts`: changing inputs and submitting again before a slow first request resolves results in only the latest request's response being reflected in `rate.value()`/`rate.error()` (FR-010)

### Implementation for User Story 3

- [X] T025 [US3] Add a `today` ISO (`yyyy-MM-dd`) constant and a `validationError = computed<string | null>()` in `frontend/src/app/features/rate-lookup/rate-lookup.ts` checking, in order: both currencies selected, `fromCurrency !== toCurrency`, `date` not lexicographically after `today` (string comparison per research.md §6) (depends on T003)
- [X] T026 [US3] Guard the submit handler in `frontend/src/app/features/rate-lookup/rate-lookup.ts` to no-op when `validationError() !== null`; bind `button[type="submit"]` `disabled` to `validationError() !== null || rate.isLoading()` (depends on T008, T010, T025)
- [X] T027 [US3] Render the inline validation message block (text matching the failing rule) when `validationError() !== null`, in `frontend/src/app/features/rate-lookup/rate-lookup.ts` template (depends on T025)
- [X] T028 [US3] Add a `lookupError = computed<LookupError | null>()` in `frontend/src/app/features/rate-lookup/rate-lookup.ts` deriving `category` from `rate.error()` (`HttpErrorResponse.status`: `400` → `invalid`, `404` → `no-data`, else → `unreachable`) and `message` from `error?.detail` else a fixed fallback string (depends on T009)
- [X] T029 [US3] Render the error block using `lookupError()` when `rate.error()` is set — category-distinct message, form and submit remain editable/available — in `frontend/src/app/features/rate-lookup/rate-lookup.ts` template (depends on T028)

**Checkpoint**: All three user stories are independently functional — the view matches contracts/ui-contract.md in full

---

## Phase 6: Polish & Cross-Cutting Concerns

**Purpose**: Final verification against the full contract and quickstart

- [X] T030 [P] Run `cd frontend && npm test -- rate-lookup` and fix any failing assertions across T005–T024
- [X] T031 Walk through quickstart.md Scenarios 1–5 manually against a running backend (`docker compose up -d`, `cd backend && ./mvnw spring-boot:run`, `cd frontend && npm start`)
- [X] T032 [P] Grep `frontend/src/app/features/rate-lookup/rate-lookup.ts` to confirm no hand-rolled `HttpClient` usage and no `Number()`/`parseFloat` applied to `rate` (Constitution VI, I — frontend analogues)

---

## Dependencies & Execution Order

### Phase Dependencies

- **Setup (Phase 1)**: No dependencies
- **Foundational (Phase 2)**: Depends on Setup — BLOCKS all user stories
- **User Story 1 (Phase 3)**: Depends on Foundational only
- **User Story 2 (Phase 4)**: Depends on Foundational; extends US1's submit handler/stream call (T008, T009) in place — implement after US1 for a working diff, though its own tests (T012, T013) are independent
- **User Story 3 (Phase 5)**: Depends on Foundational; extends US1's submit handler/loading bind (T008, T010) and stream (T009) in place — implement after US1/US2 for a working diff
- **Polish (Phase 6)**: Depends on all desired user stories being complete

### Note on "independent" stories sharing one file

All three stories edit the same single component file (`rate-lookup.ts`), per plan.md's Structure
Decision (no new files beyond `currencies.ts` and the spec file). "Independently testable" here
means each story's *behavior* can be verified in isolation (per its Independent Test in spec.md),
not that its tasks touch disjoint files — so implementation tasks within a story are sequential
against Foundational's T003, and stories are best implemented in priority order (P1 → P2 → P3)
rather than concurrently by separate people.

### Within Each User Story

- Tests written first, expected to fail until that story's implementation tasks land
- Template rendering tasks before/alongside signal-logic tasks that feed them
- Story checkpoint reached only once all its tasks are done

### Parallel Opportunities

- T002 and T004 (Foundational) can run in parallel — different files
- All test tasks within a story ([P]-marked) can be written in parallel — same file, disjoint `it()` blocks, no shared state
- T030 and T032 (Polish) can run in parallel

---

## Parallel Example: User Story 3 tests

```bash
Task: "Component test: identical currencies blocks submit, no call"
Task: "Component test: unselected currency blocks submit, no call"
Task: "Component test: future date blocks submit, no call"
Task: "Component test: 400 renders invalid category message"
Task: "Component test: 404 renders no-data category message"
Task: "Component test: network failure renders unreachable fallback"
Task: "Component test: retry after error clears once new result resolves"
Task: "Component test: stale response from superseded request is discarded"
```

---

## Implementation Strategy

### MVP First (User Story 1 Only)

1. Complete Phase 1: Setup (T001)
2. Complete Phase 2: Foundational (T002–T004)
3. Complete Phase 3: User Story 1 (T005–T011)
4. **STOP and VALIDATE**: run T005/T006, manually verify quickstart Scenario 1
5. Demo: currency-pair lookup with spread-adjusted rate, rate date, usage counts

### Incremental Delivery

1. Setup + Foundational → foundation ready
2. + User Story 1 → test independently → demo (MVP)
3. + User Story 2 → test independently → demo (adds historical date)
4. + User Story 3 → test independently → demo (adds validation + error handling)
5. Polish → full contract + quickstart verification

---

## Notes

- [P] tasks = disjoint edits (different files, or independent `it()` blocks in the shared spec file) with no ordering dependency
- [Story] label maps each task to its spec.md user story for traceability
- Rate value stays a `string` end to end — never parsed to `number` — per Constitution I and data-model.md
- No hand-rolled `HttpClient` calls anywhere in this feature — only `ExchangeRateLookupService.getExchangeRate` (Constitution VI)
- Commit after each task or logical group; stop at a checkpoint to validate a story independently before moving on
