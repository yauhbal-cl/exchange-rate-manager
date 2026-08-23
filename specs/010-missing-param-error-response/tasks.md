---

description: "Task list for feature implementation"
---

# Tasks: Consistent Error Response for Missing Required Query Parameters

**Input**: Design documents from `/specs/010-missing-param-error-response/`
**Prerequisites**: plan.md, spec.md, research.md, data-model.md (N/A), quickstart.md

**Tests**: Included — spec's acceptance scenarios are directly testable via the existing
`@WebMvcTest` convention and this is a bug fix, so tests-first is used to pin down the fix.

**Organization**: Tasks are grouped by user story per spec.md (US1 = P1 fix, US2 = P2 regression
guard).

## Format: `[ID] [P?] [Story] Description`

## Phase 1: Setup

Not applicable — `backend/` module, `GlobalExceptionHandler`, and its test conventions already
exist (see plan.md Project Structure). No initialization tasks needed.

## Phase 2: Foundational

Not applicable — no shared infrastructure is needed beyond the existing
`@RestControllerAdvice`-based `GlobalExceptionHandler`, which this feature extends in place. No
blocking prerequisite tasks.

---

## Phase 3: User Story 1 - API consumer omits a required query parameter (Priority: P1) 🎯 MVP

**Goal**: A request missing a required query parameter (e.g. `GET /api/v1/exchange` without
`from`) returns `400 Bad Request` with a `ProblemDetail` JSON body naming the missing parameter,
instead of an empty body.

**Independent Test**: `curl -i "http://localhost:8080/api/v1/exchange?to=USD"` (omitting `from`)
returns `400` with a JSON body whose `detail` names `from` — see quickstart.md step 1.

### Tests for User Story 1 ⚠️

> Write these tests FIRST; they must FAIL against current code (empty body / no `ProblemDetail`)
> before the implementation task below.

- [X] T001 [P] [US1] Add test methods to `backend/src/test/java/com/exchangerate/manager/controller/ExchangeControllerTest.java` asserting that `GET /api/v1/exchange` with `to` present but `from` omitted returns `status 400` and a JSON body with `jsonPath("$.status").value(400)` and `jsonPath("$.detail")` containing `from`; add a second case omitting `to` (with `from` present) asserting the body's `detail` names `to`. Use the existing `@WebMvcTest(ExchangeController.class)` + `MockMvc` setup already in this file (mocked collaborators are not invoked since MVC parameter binding fails before the controller method runs).

### Implementation for User Story 1

- [X] T002 [US1] In `backend/src/main/java/com/exchangerate/manager/exception/GlobalExceptionHandler.java`, add an `@ExceptionHandler(MissingServletRequestParameterException.class)` method (import `org.springframework.web.bind.MissingServletRequestParameterException`) returning `ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, e.getMessage())`, following the same style as the adjacent `handleUnknownCurrency`/`handleSameCurrency` methods. Run `cd backend && ./mvnw test -Dtest=ExchangeControllerTest` and confirm T001's new tests now pass (depends on T001).

**Checkpoint**: User Story 1 is fully functional — missing-parameter requests now return a proper
`ProblemDetail` body. This alone is a shippable MVP.

---

## Phase 4: User Story 2 - Existing error consumers see no format change (Priority: P2)

**Goal**: Confirm the fix in Phase 3 caused zero regressions to any already-handled exception
type's response shape or status code (FR-004).

**Independent Test**: Full backend test suite passes, including every pre-existing
exception-handler-covering test (unknown currency, same currency, invalid date range, trend range
too large, rate not found, AI insight unavailable, constraint violation) with unchanged
status/body assertions.

### Implementation for User Story 2

- [X] T003 [US2] Run `cd backend && ./mvnw verify` and confirm every pre-existing test in `backend/src/test/java/com/exchangerate/manager/controller/ExchangeControllerIT.java` and any other exception-handler-covering test still passes with unchanged status codes and body shapes (no test file edits expected — this task is a verification gate, not new code; depends on T002). If any pre-existing test fails or needed a change to pass, treat that as a regression introduced by T002 and fix `GlobalExceptionHandler.java` rather than the test.

**Checkpoint**: Both user stories verified — the fix is complete and regression-free.

---

## Phase 5: Polish & Cross-Cutting Concerns

- [X] T004 Run the manual validation steps in `specs/010-missing-param-error-response/quickstart.md` (steps 1-3: missing-param curl check, multi-endpoint `detail` check, regression curl checks) against a locally running backend (`docker compose up -d` + `cd backend && ./mvnw spring-boot:run`) to confirm end-to-end behavior matches the automated tests (depends on T002, T003).

---

## Dependencies & Execution Order

### Phase Dependencies

- Setup/Foundational: N/A, nothing blocks Phase 3.
- **User Story 1 (Phase 3)**: T001 before T002 (test-first). No dependency on US2.
- **User Story 2 (Phase 4)**: T003 depends on T002 (verifies the fix didn't regress anything).
- **Polish (Phase 5)**: T004 depends on T002 and T003.

### Parallel Opportunities

- T001 is marked `[P]` only in the sense that it's a single self-contained file edit distinct from
  T002's file; in practice, given the test-first dependency, run T001 → T002 → T003 → T004
  sequentially — this feature is too small for meaningful multi-agent/multi-developer
  parallelism.

---

## Implementation Strategy

### MVP First (User Story 1 Only)

1. T001: write the two failing test cases.
2. T002: add the exception handler; watch T001's tests go green.
3. **STOP and VALIDATE**: `./mvnw test -Dtest=ExchangeControllerTest` passes — this is a shippable
   MVP on its own (US2 and Polish are verification, not new behavior).

### Incremental Delivery

1. T001 + T002 → MVP: missing-parameter requests now get a proper `ProblemDetail` body.
2. T003 → confidence that nothing else broke.
3. T004 → manual end-to-end confirmation per quickstart.md.

## Notes

- No `[Story]` label on Setup/Foundational/Polish tasks per format rules — Phases 1-2 have no
  tasks at all (explicitly N/A) since this feature adds no new infrastructure.
- Commit after T002 (fix + its test go together) and again after T003/T004 (verification).
