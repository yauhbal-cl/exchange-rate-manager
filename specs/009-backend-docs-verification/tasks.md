---

description: "Task list for Backend Docs & Verification"
---

# Tasks: Backend Docs & Verification

**Input**: Design documents from `/specs/009-backend-docs-verification/`

**Prerequisites**: plan.md, spec.md, research.md, data-model.md (N/A — no new entities), quickstart.md

**Tests**: Not requested for this feature — validation happens via the quickstart.md scenarios
(running the real backend/build), not automated test suites.

**Organization**: Tasks are grouped by user story (US1/US2/US3, matching spec.md P1/P2/P3) so each
can be validated independently against its Independent Test.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Can run in parallel (different files, no dependencies)
- **[Story]**: Which user story this task belongs to (US1/US2/US3)
- Tasks touching the same file (`README.md`, `contracts/openapi.yaml`) are kept sequential even
  within a story to avoid conflicting concurrent edits.

## Phase 1: Setup

**Purpose**: Confirm the baseline the rest of the audit reasons from is real, before editing docs
against it.

- [X] T001 Verify local toolchain (Java 21, Maven 3.9.x, Docker + Docker Compose, Node 22 LTS) is
      actually installed, per the Prerequisites section of `README.md` — this is the environment the
      rest of the tasks below validate against.

**Checkpoint**: Toolchain confirmed; proceed to Foundational.

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: Fix the one drift that every later doc claim and verification run depends on being
true (research.md R1) before writing/checking anything that asserts "Java 21."

**⚠️ CRITICAL**: T002/T003 MUST complete before any User Story 1 or User Story 3 task, since those
stories assert or verify a Java 21 build.

- [X] T002 Update `<java.version>` in `backend/pom.xml` from `17` to `21` to match the documented
      tech stack in `CLAUDE.md` and root `README.md` (research.md R1).
- [ ] T003 Run `cd backend && ./mvnw clean verify` once and confirm `BUILD SUCCESS` under Java 21,
      so no downstream doc task asserts a Java 21 build that doesn't actually pass.

**Checkpoint**: Backend build target is verifiably Java 21. User story work can begin.

---

## Phase 3: User Story 1 - Set up and run the backend from documentation alone (Priority: P1) 🎯 MVP

**Goal**: A newcomer following only `README.md` reaches a healthy, running backend — including
Postgres and the local Ollama/`llama3.2` dependency — inside 15 minutes, with every
environment-configurable setting documented.

**Independent Test**: quickstart.md Scenario 1 — clean-checkout `docker compose up -d` →
`./mvnw spring-boot:run` → `curl http://localhost:8080/api/v1/status` returns healthy, with no
undocumented step.

### Implementation for User Story 1

- [ ] T004 [US1] In `README.md` Quick Start, document that `docker compose up -d` also starts the
      `ollama` service and a one-shot `ollama-pull` container that pulls `llama3.2`, and how to
      confirm the pull finished (`docker compose logs ollama-pull`) before relying on
      `/exchange/trend/insight` (research.md R2; addresses FR-001 Acceptance Scenario 2).
- [ ] T005 [US1] In `README.md` "Environment Configuration > Backend", add entries for
      `OLLAMA_BASE_URL` (default `http://localhost:11434`, points the backend at the local Ollama
      instance) and `AI_INSIGHT_TIMEOUT_SECONDS` (default `30`, read-timeout for AI insight calls)
      — both currently read from `backend/src/main/resources/application.yml` but undocumented
      (research.md R5).
- [ ] T006 [US1] Diff every `${VAR:default}` placeholder in
      `backend/src/main/resources/application.yml` against `README.md`'s Environment Configuration
      section in both directions; fix any gap beyond the two already identified in T005 (FR-002).
- [ ] T007 [US1] Run quickstart.md Scenario 1 end-to-end starting from `docker compose down -v`
      (simulating a clean checkout): bring up infra, start the backend with `FIXER_API_KEY` set, hit
      `/api/v1/status`, and confirm the whole sequence needs no step beyond what `README.md` now
      says and completes within 15 minutes (SC-001).

**Checkpoint**: User Story 1 independently satisfies FR-001/FR-002/SC-001.

---

## Phase 4: User Story 2 - Discover the true API surface from documentation (Priority: P2)

**Goal**: `contracts/openapi.yaml` (and the Swagger UI generated from it) accurately and completely
documents all 6 implemented endpoints — inputs, success shape, and every error condition.

**Independent Test**: quickstart.md Scenario 2 — for each of `/status`, `/exchange`,
`/exchange/refresh`, `/exchange/trend`, `/exchange/trend/insight`, `/exchange/usage`, the documented
example request/response and documented error case match the live backend's actual behavior.

### Implementation for User Story 2

- [ ] T008 [US2] Audit `/status` and `/exchange` in `contracts/openapi.yaml` against
      `backend/src/main/java/com/exchangerate/manager/controller/StatusController.java` and
      `ExchangeController.java` for request parameters, success response fields, and every error
      condition raised through the central `@RestControllerAdvice`; fix any drift directly in
      `contracts/openapi.yaml` (research.md R4).
- [ ] T009 [US2] Audit `/exchange/refresh` and `/exchange/usage` in `contracts/openapi.yaml` against
      `ExchangeController.java` the same way; fix any drift in `contracts/openapi.yaml`.
- [ ] T010 [US2] Audit `/exchange/trend` and `/exchange/trend/insight` in `contracts/openapi.yaml`
      against `ExchangeController.java`, including the AI-unavailable degrade error path
      (Constitution Principle VIII); fix any drift in `contracts/openapi.yaml`.
- [ ] T011 [US2] Run `cd backend && ./mvnw generate-sources` after T008-T010 and confirm the
      controllers still compile against the regenerated server interfaces with zero hand edits to
      generated code (FR-004 sanity check).
- [ ] T012 [US2] Run quickstart.md Scenario 2 against the running backend: for each of the 6
      endpoints, send the documented example request and trigger one documented error case, and
      confirm actual responses match the documented shapes (SC-002 — 100% of endpoints match).

**Checkpoint**: User Story 2 independently satisfies FR-003/FR-004/SC-002.

---

## Phase 5: User Story 3 - Confirm the system is verified before it's trusted (Priority: P3)

**Goal**: One documented command (`./mvnw verify`) gives an unambiguous, repeatable pass/fail
signal covering unit + Testcontainers-backed integration tests.

**Independent Test**: quickstart.md Scenarios 3 and 4 — `./mvnw verify` run twice on an unchanged
checkout gives the same result within 10 minutes, and gives a clear (not raw-stack-trace) failure
signal when required infra is missing.

### Implementation for User Story 3

- [ ] T013 [US3] In `README.md` "Development" section, change "Backend tests: `cd backend && ./mvnw
      test`" to "`cd backend && ./mvnw verify`", adding one sentence noting it runs unit tests
      (Surefire) and Testcontainers-backed integration tests (Failsafe) in one pass with a single
      `BUILD SUCCESS`/`BUILD FAILURE` result (research.md R3; resolves the README/CLAUDE.md
      inconsistency).
- [ ] T014 [US3] Re-read `CLAUDE.md`'s Commands section and confirm it still correctly states
      `cd backend && ./mvnw verify` with no remaining drift against `README.md` after T013.
- [ ] T015 [US3] Run quickstart.md Scenario 3: `cd backend && ./mvnw verify` twice in a row on an
      unchanged checkout; confirm both runs finish in under 10 minutes with the identical
      `BUILD SUCCESS`/`BUILD FAILURE` result (SC-003, FR-007), and that a failure (temporarily break
      one test to check) clearly names the failing class/phase (SC-004, FR-006).
- [ ] T016 [US3] Run quickstart.md Scenario 4: `docker compose down`, then `./mvnw verify` again and
      confirm the Testcontainers-backed suite still passes on its own ephemeral Postgres
      (Constitution Principle X) with no unexplained failure; separately start the backend without
      Ollama running and confirm `/exchange/trend/insight` degrades to a clear, explicit error
      rather than a stack trace or fabricated insight (Constitution Principle VIII).

**Checkpoint**: User Story 3 independently satisfies FR-005/FR-006/FR-007/SC-003/SC-004.

---

## Phase 6: Polish & Cross-Cutting Concerns

**Purpose**: Close the one remaining functional requirement that cuts across all three stories
(FR-008) and do a final combined sign-off.

- [ ] T017 Review `README.md` and `CLAUDE.md` backend-relevant sections for any capability described
      as future/planned that is actually already implemented, or vice versa — in particular
      `README.md`'s Architecture line "AI: Ollama + Spring AI (for later features)", which is stale
      now that the AI trend insight feature (specs/006-ai-trend-insight) is done; correct the wording
      so implemented vs. not-yet-built capabilities are clearly distinguished (FR-008). Frontend-only
      version claims (e.g. the Angular/TypeScript versions in the Project Structure block) are out of
      scope per spec.md Assumptions.
- [ ] T018 Run all four quickstart.md scenarios once more, back to back, after T001-T017 land, as
      final sign-off that the corrected docs and verification procedure hold together end-to-end.

---

## Dependencies & Execution Order

### Phase Dependencies

- **Setup (Phase 1)**: No dependencies.
- **Foundational (Phase 2)**: Depends on Setup. BLOCKS User Story 1 and User Story 3 (both assert
  Java 21). User Story 2 does not depend on it (contract/controller audit is Java-version-agnostic)
  but running T011's build still benefits from T002 being done first — do Foundational first
  regardless.
- **User Stories (Phase 3-5)**: All depend on Foundational. Can proceed in priority order
  (US1 → US2 → US3) or, since each edits mostly-distinct files (README.md vs contracts/openapi.yaml
  vs README.md/CLAUDE.md), US2 can run in parallel with US1/US3 if staffed separately — just
  serialize any task that touches `README.md`.
- **Polish (Phase 6)**: Depends on US1, US2, and US3 all being complete (T018 exercises all four
  quickstart scenarios).

### Within Each User Story

- US1: T004 → T005 → T006 (all edit `README.md`, sequential) → T007 (validates all three).
- US2: T008 → T009 → T010 (all edit `contracts/openapi.yaml`, sequential) → T011 → T012.
- US3: T013 → T014 (both reason about the same doc pair) → T015 → T016.

### Parallel Opportunities

- T004-T006 (US1, README.md) and T008-T010 (US2, contracts/openapi.yaml) touch different files and
  can run in parallel across the two stories.
- T013 (US3, README.md) conflicts with T004-T006 (also README.md) — do not run concurrently with
  those; otherwise US3 can start as soon as Foundational is done.
- T001 has no dependents besides gating everything after it; nothing else in Setup to parallelize.

---

## Implementation Strategy

### MVP First (User Story 1 Only)

1. Complete Phase 1 (T001) and Phase 2 (T002-T003).
2. Complete Phase 3 (T004-T007) — User Story 1.
3. **STOP and VALIDATE**: quickstart.md Scenario 1 passes independently (SC-001).

### Incremental Delivery

1. Setup + Foundational → Java 21 build confirmed.
2. Add User Story 1 → validate Scenario 1 → newcomer onboarding fixed (MVP).
3. Add User Story 2 → validate Scenario 2 → API docs trustworthy.
4. Add User Story 3 → validate Scenarios 3-4 → verification procedure trustworthy.
5. Phase 6 → FR-008 wording pass + full four-scenario sign-off.
