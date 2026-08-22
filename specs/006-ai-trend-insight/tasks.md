---

description: "Task list template for feature implementation"
---

# Tasks: AI Trend Insight (Local LLM) — Backend Spring AI Slice

**Input**: Design documents from `/specs/006-ai-trend-insight/`
**Prerequisites**: plan.md, spec.md, research.md, data-model.md, contracts/trend-insight-endpoint.yaml, quickstart.md

**Tests**: Not explicitly requested in spec.md beyond the manual quickstart; automated slice/unit
tests are still included below because they are how this repo validates each user story's failure
mode (mocked `ChatClient`), matching `research.md`'s "Testing approach for the AI-dependent path"
and the existing `ExchangeControllerTest`/`ExchangeRateServiceTest` conventions.

**Organization**: Tasks are grouped by user story (spec.md priorities P1/P2/P3) to enable
independent implementation and testing of each.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Can run in parallel (different files, no dependencies)
- **[Story]**: Which user story this task belongs to (US1/US2/US3)
- Paths are relative to repo root unless stated otherwise

## Path Conventions

Existing web-app monorepo; this feature touches only `contracts/` and `backend/`:
- `contracts/openapi.yaml` (shared contract, edited first per CLAUDE.md)
- `backend/src/main/java/com/exchangerate/manager/{controller,service,exception,mapper,config}/`
- `backend/src/test/java/com/exchangerate/manager/{controller,service}/`

---

## Phase 1: Setup (Shared Infrastructure)

**Purpose**: Contract + dependency plumbing all three user stories build on.

- [X] T001 Add the `/exchange/trend/insight` path and `TrendInsightResponse` schema to
  `contracts/openapi.yaml`, per `specs/006-ai-trend-insight/contracts/trend-insight-endpoint.yaml`
  (add an `Exchange Rate AI Insight` tag alongside the existing tags)
- [X] T002 Add `spring-ai-bom` (import scope, version 2.0.1) to `backend/pom.xml`'s
  `<dependencyManagement>` and `spring-ai-starter-model-ollama` to `<dependencies>`
- [X] T003 Run the backend's `generate-sources` build phase (`cd backend && ./mvnw generate-sources`)
  to regenerate `ExchangeApi` and `TrendInsightResponse` from the updated
  `contracts/openapi.yaml`; confirm the new operation/model classes appear under
  `backend/target/generated-sources/`
- [X] T004 Regenerate the frontend API client (`cd frontend && npm run generate:api`) so
  `frontend/src/app/api-client/` stays in sync with the new contract (frontend consumption itself
  is out of scope for this slice per plan.md, but the generated client must not drift from the
  contract)

**Checkpoint**: Contract, backend interface, and frontend client all reflect the new endpoint shape.

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: Shared config, exception types, and the date-range helper every user story's code path depends on.

**⚠️ CRITICAL**: No user story work can begin until this phase is complete.

- [X] T005 Add `spring.ai.ollama.*` config block to `backend/src/main/resources/application.yml`
  (`base-url: ${OLLAMA_BASE_URL:-http://localhost:11434}`, chat model `llama3.2`, and the
  configurable read-timeout property per research.md's "Request timeout" section, default
  `${AI_INSIGHT_TIMEOUT_SECONDS:-30}s`)
- [X] T006 [P] Create `AiInsightUnavailableException` in
  `backend/src/main/java/com/exchangerate/manager/exception/AiInsightUnavailableException.java`
  (unchecked, message-only constructor, matching `RateDataNotFoundException`'s style)
- [X] T007 [P] Create `TrendRangeTooLargeException` in
  `backend/src/main/java/com/exchangerate/manager/exception/TrendRangeTooLargeException.java`
  (unchecked, message-only constructor)
- [X] T008 Add `@ExceptionHandler` methods for `AiInsightUnavailableException` (→503) and
  `TrendRangeTooLargeException` (→400) to
  `backend/src/main/java/com/exchangerate/manager/exception/GlobalExceptionHandler.java`
- [X] T009 Extract the shared default-resolution + `startDate > endDate` validation out of
  `ExchangeRateService.getTrend` in
  `backend/src/main/java/com/exchangerate/manager/service/ExchangeRateService.java` into a small
  package-private static helper (e.g. `DateRangeResolver.resolve(startDate, endDate)` returning the
  effective start/end, throwing the existing `InvalidDateRangeException`), and update
  `ExchangeRateService.getTrend` to call it instead of duplicating the two-line logic
- [X] T010 [P] Create the `TrendInsightResult` record (`fromCurrency`, `toCurrency`, `startDate`,
  `endDate`, `narrative`) in
  `backend/src/main/java/com/exchangerate/manager/service/TrendInsightResult.java`

**Checkpoint**: Config, exceptions, shared date-range helper, and result shape exist — user story
implementation can now begin.

---

## Phase 3: User Story 1 - View AI commentary on a currency trend (Priority: P1) 🎯 MVP

**Goal**: Return a short, data-grounded narrative for a currency pair/date range that has
historical rate data.

**Independent Test**: Request an insight for a currency pair/date range with real stored rates;
confirm a non-empty narrative referencing the actual observed direction/high/low is returned.

### Implementation for User Story 1

- [X] T011 [US1] Create `TrendInsightResponseMapper` (MapStruct) in
  `backend/src/main/java/com/exchangerate/manager/mapper/TrendInsightResponseMapper.java` mapping
  `TrendInsightResult` → generated `TrendInsightResponse`, following
  `ExchangeRateTrendResponseMapper`'s pattern
- [X] T012 [US1] Create `TrendInsightService` in
  `backend/src/main/java/com/exchangerate/manager/service/TrendInsightService.java`: inject
  `ExchangeRateService`, `ExchangeRateRepository`, and a Spring AI `ChatClient`; implement
  `generateInsight(from, to, startDate, endDate)` that:
  1. validates `from`/`to` via `existsByCurrencyCode` → `UnknownCurrencyException`
  2. resolves the date range via the `DateRangeResolver` helper from T009
  3. rejects a resolved range spanning more than ~365 daily points → `TrendRangeTooLargeException`
     (checked before querying, per research.md)
  4. calls `exchangeRateService.getTrend(...)`; throws `RateDataNotFoundException` if empty
  5. serializes each `RateTrendPoint` as `YYYY-MM-DD: <rate>` lines into the `ChatClient` user
     message, with a system prompt constraining the model to: reference only supplied values,
     never invent a date/figure, describe a single point as an observation (not a trend) when only
     one point exists, and keep the response to 2-4 sentences (per research.md's "Grounding
     technique" and spec.md's FR-002/FR-007)
  6. wraps the `ChatClient` call in a single broad `catch (Exception e)`, logs at `WARN`, rethrows
     as `AiInsightUnavailableException`
  7. returns a `TrendInsightResult` with the resolved dates and generated narrative
- [X] T013 [US1] Add `AiConfig` in
  `backend/src/main/java/com/exchangerate/manager/config/AiConfig.java` only if a `ChatClient` bean
  is not already satisfied by Spring AI's auto-configuration (verify `ChatClient.Builder`
  autowires from `spring-ai-starter-model-ollama`, and construct the `ChatClient` bean with the
  fixed system prompt from T012 if a per-call system prompt isn't used instead)
- [X] T014 [US1] Wire `getExchangeRateTrendInsight` in
  `backend/src/main/java/com/exchangerate/manager/controller/ExchangeController.java`: implement
  the new `ExchangeApi` operation, delegate to `TrendInsightService.generateInsight(...)`, map the
  result via `TrendInsightResponseMapper`, return `ResponseEntity.ok(body)` — no
  `CurrencyUsageRepository` increment (per research.md's "Usage counters" decision)
- [X] T015 [P] [US1] Unit test `TrendInsightService` success path in
  `backend/src/test/java/com/exchangerate/manager/service/TrendInsightServiceTest.java`: mock
  `ChatClient`/`ExchangeRateService`, assert a narrative is returned and the prompt user message
  contains the serialized rate lines; include the single-data-point case (FR-007) asserting the
  service still returns a result (narrative content assertion is out of scope for an automated
  test, per research.md — assert only that generation is attempted and a result returned)
- [X] T016 [P] [US1] Controller slice test for the success path in
  `backend/src/test/java/com/exchangerate/manager/controller/ExchangeControllerTest.java`: mock
  `TrendInsightService`, assert `GET /api/v1/exchange/trend/insight` returns 200 with the expected
  JSON shape

**Checkpoint**: User Story 1 fully functional — a valid currency pair/range with data returns a
grounded narrative.

---

## Phase 4: User Story 2 - Clear failure when the AI service is unavailable (Priority: P2)

**Goal**: An unreachable/timed-out AI capability surfaces as an explicit 503, never a fabricated
narrative, and recovers automatically once the AI capability is reachable again.

**Independent Test**: With the mocked `ChatClient` throwing, request an insight for a pair/range
with data and confirm a 503 `ProblemDetail` is returned (not a narrative, not a raw stack trace).

### Implementation for User Story 2

- [X] T017 [US2] Unit test in `TrendInsightServiceTest.java` (T015's file): mock `ChatClient` to
  throw, assert `generateInsight(...)` throws `AiInsightUnavailableException` (covers FR-004,
  FR-005); add a second case confirming a subsequent call with the mock no longer throwing
  succeeds (FR-008 — stateless recovery, no reset needed)
- [X] T018 [US2] Controller slice test in `ExchangeControllerTest.java` (T016's file): mock
  `TrendInsightService` to throw `AiInsightUnavailableException`, assert the response is 503
  `application/problem+json` with a clear `detail` message (not a generic 500)

**Checkpoint**: User Stories 1 AND 2 both work independently — AI-unavailable is now honestly
surfaced and self-heals on retry.

---

## Phase 5: User Story 3 - No insight when there is no underlying data (Priority: P3)

**Goal**: A pair/range with zero stored observations (including entirely-future ranges) returns a
clear 404, never an attempted narrative; a range with exactly one observation returns a
single-value narrative rather than a fabricated trend.

**Independent Test**: Request an insight for a pair/range known to have zero stored rates; confirm
a 404 stating no data is available, with no AI call attempted.

### Implementation for User Story 3

- [X] T019 [US3] Unit test in `TrendInsightServiceTest.java` (T015's file): stub
  `exchangeRateService.getTrend(...)` to return an empty list, assert
  `generateInsight(...)` throws `RateDataNotFoundException` **and** that the mocked `ChatClient` is
  never invoked (verifies FR-003's "MUST NOT attempt to generate a narrative" ordering)
- [X] T020 [US3] Unit test in `TrendInsightServiceTest.java` (T015's file): resolve a range spanning
  more than ~365 daily points, assert `generateInsight(...)` throws `TrendRangeTooLargeException`
  **before** `exchangeRateService.getTrend(...)` or the `ChatClient` are invoked (FR-009, verifies
  the up-front span check from research.md)
- [X] T021 [US3] Controller slice test in `ExchangeControllerTest.java` (T016's file): mock
  `TrendInsightService` to throw `RateDataNotFoundException`, assert 404
  `application/problem+json`; and to throw `TrendRangeTooLargeException`, assert 400
  `application/problem+json`

**Checkpoint**: All three user stories independently functional — success, AI-unavailable, and
no-data/range-too-large all surface distinctly and correctly.

---

## Phase 6: Polish & Cross-Cutting Concerns

**Purpose**: Validate the full slice end-to-end and close out documentation.

- [X] T022 Run `cd backend && ./mvnw verify` and confirm all new and existing tests pass
- [ ] T023 Execute `specs/006-ai-trend-insight/quickstart.md` Scenarios 1-4 against a locally
  running backend + Ollama (per its documented prerequisite: the earlier infra slice's `ollama`
  `docker-compose.yml` service and `ollama pull llama3.2` must already be done); record any
  deviation from expected responses
- [X] T024 [P] Update `contracts/README.md` if present, or any top-level API documentation index,
  to reference the new `/exchange/trend/insight` endpoint alongside the existing `/exchange/trend`
  entry

---

## Dependencies & Execution Order

### Phase Dependencies

- **Setup (Phase 1)**: No dependencies — start immediately. T003 depends on T001 (contract must be
  edited first); T002 is independent of T001/T003; T004 depends on T001.
- **Foundational (Phase 2)**: Depends on Setup (needs the generated `TrendInsightResponse`/
  `ExchangeApi` operation from T003, and the `spring-ai` dependency from T002) — BLOCKS all user
  stories.
- **User Stories (Phase 3-5)**: All depend on Foundational (Phase 2) completion. US2 and US3's test
  tasks depend on US1's `TrendInsightService`/`ExchangeController` wiring existing (T012, T014)
  since they add cases to the same files, but conceptually test independent failure paths.
- **Polish (Phase 6)**: Depends on all three user stories being complete.

### Within Phase 2

- T005 independent. T006, T007, T010 are `[P]` (distinct new files). T008 depends on T006 and T007
  (needs both exception classes to exist). T009 is independent of T006-T008 but is on the same
  file (`ExchangeRateService.java`) as no other Phase 2 task, so no conflict.

### Within User Story 1

- T011 and T013 can proceed in parallel with each other once Phase 2 is done, but T012 depends on
  T010 (`TrendInsightResult`) and benefits from T013's `ChatClient` bean existing. T014 depends on
  T011 and T012. T015 and T016 (`[P]`, distinct test files) depend on T012 and T014 respectively.

### Parallel Opportunities

- Setup: T002 parallel with T001 (distinct files); T004 waits on T001/T003.
- Foundational: T006, T007, T010 in parallel (distinct new files).
- US1 test tasks T015/T016 in parallel with each other (distinct files) once their respective
  implementation tasks land.
- T024 (Polish, docs) can run in parallel with T022/T023.

---

## Parallel Example: Phase 2 (Foundational)

```bash
# Launch independent new-file tasks together:
Task: "Create AiInsightUnavailableException in backend/src/main/java/.../exception/AiInsightUnavailableException.java"
Task: "Create TrendRangeTooLargeException in backend/src/main/java/.../exception/TrendRangeTooLargeException.java"
Task: "Create the TrendInsightResult record in backend/src/main/java/.../service/TrendInsightResult.java"
```

## Parallel Example: User Story 1 tests

```bash
Task: "Unit test TrendInsightService success path in backend/src/test/java/.../service/TrendInsightServiceTest.java"
Task: "Controller slice test for success path in backend/src/test/java/.../controller/ExchangeControllerTest.java"
```

---

## Implementation Strategy

### MVP First (User Story 1 Only)

1. Complete Phase 1: Setup (contract + dependency + codegen)
2. Complete Phase 2: Foundational (config, exceptions, shared date-range helper, result record)
3. Complete Phase 3: User Story 1 (service, mapper, controller wiring, success-path tests)
4. **STOP and VALIDATE**: Run quickstart.md Scenario 1 manually against a real Ollama instance
5. Demo the grounded-narrative happy path

### Incremental Delivery

1. Setup + Foundational → endpoint compiles, returns nothing user-facing yet
2. Add User Story 1 → narrative on real data works → run quickstart Scenario 1
3. Add User Story 2 → AI-unavailable honestly surfaces as 503 → run quickstart Scenario 2
4. Add User Story 3 → no-data/range-too-large surface as 404/400 → run quickstart Scenarios 3-4
5. Polish → full `./mvnw verify` + all four quickstart scenarios pass

## Notes

- [P] tasks touch different files with no completed-task dependency between them.
- All new backend files follow existing package conventions
  (`controller`/`service`/`exception`/`mapper`/`config`) and Lombok/MapStruct usage per CLAUDE.md.
- No new database migration, entity, or scheduled job in this slice — read-only over the existing
  `exchange_rates` table.
- Frontend consumption of this endpoint is explicitly out of scope for this slice (plan.md) — T004
  only keeps the generated client from drifting, it does not add any UI.
