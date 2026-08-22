---

description: "Task list for feature implementation"
---

# Tasks: Fixer.io Data Collection

**Input**: Design documents from `/specs/003-fixer-data-collection/`

**Prerequisites**: plan.md, spec.md, research.md, data-model.md, quickstart.md,
`contracts/openapi.yaml` (repo root, `POST /exchange/refresh` already added)

**Tests**: Test tasks included, matching the testing strategy in research.md §8
(`MockRestServiceServer` for the Fixer.io client, a plain unit test for the cross-rate math, and
a real-Postgres repository test for the new upsert query — consistent with
`ExchangeRateRepositoryTest`'s existing pattern from spec 002).

**Organization**: Tasks are grouped by user story (US1, US2, US3 from spec.md). **User Story 3
(manual refresh) is optional** — TASK.md §4.4 names it an optional extension, and per explicit
direction for this task list it MUST NOT block MVP delivery or any other story. Skipping it still
leaves US1+US2 fully functional and independently deployable.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Can run in parallel (different files, no dependencies)
- **[Story]**: Which user story this task belongs to (US1, US2, US3)
- Include exact file paths in descriptions

## Path Conventions

Web application layout per plan.md: `backend/src/main/java/com/exchangerate/manager/`,
`backend/src/main/resources/`, `backend/src/test/java/com/exchangerate/manager/`. Frontend is
untouched by this feature (the regenerated typed client is only needed once the frontend consumes
`POST /exchange/refresh`, which is out of scope here).

---

## Phase 1: Setup (Shared Infrastructure)

**Purpose**: Configuration this feature needs before any client/service code is written

- [X] T001 Add `fixer.api-key` (from `${FIXER_API_KEY}` env var, no default) and
      `fixer.base-url` (default `https://data.fixer.io/api`) properties to
      `backend/src/main/resources/application.yml`
- [X] T002 [P] Document the `FIXER_API_KEY` environment variable requirement (how to obtain a
      free key, how to set it locally) in `README.md`

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: Shared client, config, and repository plumbing that both US1 and US2 (and,
optionally, US3) build on

**⚠️ CRITICAL**: No user story work can begin until this phase is complete

- [X] T003 [P] Create `FixerLatestResponse` DTO (`success`, `base`, `date`, `rates` map,
      nullable `error`) in `backend/src/main/java/com/exchangerate/manager/client/FixerLatestResponse.java`
      per data-model.md
- [X] T004 [P] Create `FixerApiException` (wraps network failure, non-2xx, or a
      `success:false` error envelope) in
      `backend/src/main/java/com/exchangerate/manager/client/FixerApiException.java`
- [X] T005 Create `FixerClient` in
      `backend/src/main/java/com/exchangerate/manager/client/FixerClient.java` — wraps a
      `RestClient` bean, calls `GET {fixer.base-url}/latest?access_key={key}` (EUR base, no
      `symbols` restriction so the full provider set is returned per FR-005), deserializes into
      `FixerLatestResponse`, throws `FixerApiException` on network error, non-2xx, or
      `success:false` (depends on T003, T004)
- [X] T006 [P] Create `SchedulerLockConfig` in
      `backend/src/main/java/com/exchangerate/manager/config/SchedulerLockConfig.java` —
      `@EnableSchedulerLock(defaultLockAtMostFor = "PT10M")` plus a `LockProvider` bean
      (`JdbcTemplateLockProvider` over the existing `DataSource`) per research.md §4
- [X] T007 Add native upsert method to `ExchangeRateRepository` in
      `backend/src/main/java/com/exchangerate/manager/repository/ExchangeRateRepository.java`
      — `@Modifying @Query(nativeQuery = true)` `INSERT ... ON CONFLICT (currency_code,
      rate_date) DO UPDATE SET rate_to_usd = EXCLUDED.rate_to_usd` (data-model.md)

**Checkpoint**: Foundation ready — US1/US2 implementation can now begin (US3 also depends on
this phase if it is picked up)

---

## Phase 3: User Story 1 - Automatic daily rate refresh (Priority: P1) 🎯 MVP

**Goal**: A daily scheduled job fetches Fixer.io's `/latest` rates, derives each currency's
USD-relative rate, and upserts one row per currency into `exchange_rates`, coordinated so only
one instance calls the provider per run.

**Independent Test**: Deploy with a valid `FIXER_API_KEY` and an empty rate history; trigger the
scheduled method directly (or wait for 00:05 GMT); verify one `exchange_rates` row per provider
currency exists for the current date (quickstart.md "Validate: scheduled collection").

### Tests for User Story 1

- [X] T008 [P] [US1] `FixerClientTest` (happy path: request URL/params, response
      deserialization) using `MockRestServiceServer` in
      `backend/src/test/java/com/exchangerate/manager/client/FixerClientTest.java`
- [ ] T009 [P] [US1] `RateCollectionServiceTest` happy-path case (EUR cross-rate math correct
      for several currencies, `USD` forced to `1.000000`) in
      `backend/src/test/java/com/exchangerate/manager/service/RateCollectionServiceTest.java`
- [ ] T010 [P] [US1] Add upsert test cases to the existing
      `backend/src/test/java/com/exchangerate/manager/repository/ExchangeRateRepositoryTest.java`
      — insert-when-absent and update-in-place-on-conflict for the same
      `(currency_code, rate_date)`

### Implementation for User Story 1

- [ ] T011 [US1] Create `RateCollectionService` in
      `backend/src/main/java/com/exchangerate/manager/service/RateCollectionService.java` —
      calls `FixerClient`, computes `rateToUsd(X) = eurToX / eurToUsd` for every currency in
      the response plus `USD = 1.000000`, then upserts each via T007's repository method inside
      one `@Transactional` method annotated `@SchedulerLock(name = "fixer-rate-collection")`
      (depends on T005, T006, T007)
- [ ] T012 [US1] Create `RateCollectionScheduler` in
      `backend/src/main/java/com/exchangerate/manager/scheduler/RateCollectionScheduler.java`
      — `@Scheduled(cron = "0 5 0 * * *", zone = "GMT")` method delegating to
      `RateCollectionService` (depends on T011)

**Checkpoint**: User Story 1 is fully functional and independently testable/deployable (MVP)

---

## Phase 4: User Story 2 - Resilience to provider failures (Priority: P2)

**Goal**: A collection run that fails (unreachable provider, error response) leaves existing
data untouched and logs the failure; a response missing some currencies still persists the
currencies that were present; a failed run never blocks the next scheduled attempt.

**Independent Test**: Point `FIXER_API_KEY` at an invalid value (or otherwise force a
`FixerApiException`), trigger collection, and verify `exchange_rates` is unchanged and an
`ERROR`-level log line appears (quickstart.md "Validate: provider failure handling"); separately,
verify a response with a subset of `symbols` still persists exactly those currencies.

### Tests for User Story 2

- [ ] T013 [P] [US2] `FixerClientTest` failure cases (non-2xx, network error, `success:false`
      body → `FixerApiException`) added to
      `backend/src/test/java/com/exchangerate/manager/client/FixerClientTest.java`
- [ ] T014 [P] [US2] `RateCollectionServiceTest` cases: (a) `FixerApiException` from the client
      aborts the run with zero repository writes attempted, (b) a response missing some
      currencies still upserts exactly the currencies present, added to
      `backend/src/test/java/com/exchangerate/manager/service/RateCollectionServiceTest.java`

### Implementation for User Story 2

- [ ] T015 [US2] In `RateCollectionService` (from T011), wrap the `FixerClient` call in a
      try/catch that logs at `ERROR` level and returns/rethrows without invoking any upsert
      call on failure (no partial writes; depends on T011)
- [ ] T016 [US2] In `RateCollectionService`, iterate only over `response.rates.keySet()` (plus
      `USD`) when upserting, so currencies absent from the response are naturally skipped rather
      than causing a failure (depends on T011; verify against T014b)

**Checkpoint**: User Stories 1 AND 2 both work independently — the daily job is now
production-resilient to provider outages/partial responses

---

## Phase 5: User Story 3 - Manual on-demand refresh (Priority: P3) — OPTIONAL

**Goal**: An operator can trigger a collection run on demand via `POST /exchange/refresh`,
reusing the same upsert/lock behavior as the scheduled run, without touching usage counters.

**Optional**: TASK.md §4.4 marks this endpoint as an optional extension. Skip this entire phase
if time-boxing the feature to US1+US2 — nothing in Phases 3–4 depends on it, and the daily
schedule alone (US1) plus its failure handling (US2) fully satisfies the core feature.

**Independent Test**: `curl -X POST /api/v1/exchange/refresh`, verify `200` with a
`{currenciesCollected, rateDate}` body, `exchange_rates` updated accordingly, and
`currency_usage` row count unchanged (quickstart.md "Validate: manual refresh endpoint" and
"Validate: usage counters untouched").

### Tests for User Story 3 (OPTIONAL)

- [ ] T017 [P] [US3] Contract/integration test for `POST /exchange/refresh` — `200` with
      correct body on success, `502` `ProblemDetail` on `FixerApiException`, `409` `ProblemDetail`
      when a scheduled run already holds the lock, `currency_usage` row count unchanged
      before/after — in
      `backend/src/test/java/com/exchangerate/manager/controller/ExchangeControllerTest.java`

### Implementation for User Story 3 (OPTIONAL)

- [ ] T018 [US3] Regenerate the server interface from `contracts/openapi.yaml`
      (`cd backend && ./mvnw generate-sources`) to produce the `refreshExchangeRates`
      interface method for `POST /exchange/refresh`
- [ ] T019 [P] [US3] Create `RefreshResult` DTO (`currenciesCollected`, `rateDate`) in
      `backend/src/main/java/com/exchangerate/manager/service/RefreshResult.java` per
      data-model.md
- [ ] T020 [US3] Change `RateCollectionService.collect()` (T011) to return a `RefreshResult`
      (currency count + rate date) instead of `void`, updating T012's scheduler call site to
      discard the return value (depends on T011, T019)
- [ ] T021 [US3] Create `ExchangeController` implementing the generated
      `refreshExchangeRates` interface in
      `backend/src/main/java/com/exchangerate/manager/controller/ExchangeController.java` —
      delegates to `RateCollectionService.collect()` (same `@SchedulerLock` name as the
      scheduler, so a manual call can never race a scheduled run), maps `FixerApiException` to
      a `502` `ProblemDetail` (depends on T018, T020)
- [ ] T021a [US3] Handle the lock-already-held case in `ExchangeController` — `@SchedulerLock`
      skips the method body and returns `null` when the lock is unavailable (e.g., a scheduled
      run is in progress); detect this (`null` result) and map it to a `409 Conflict`
      `ProblemDetail` instead of an empty/broken `200`, per spec.md's "manual trigger during an
      in-progress run" edge case (depends on T021)

**Checkpoint**: All user stories independently functional; US3 adds an operational
recovery/testing convenience on top of US1+US2

---

## Phase 6: Polish & Cross-Cutting Concerns

**Purpose**: Final validation across whichever stories were implemented

- [ ] T022 Run `./mvnw verify` from `backend/` — confirm all new and existing tests pass
- [ ] T023 Execute quickstart.md's full validation sequence end-to-end (scheduled path via
      manual trigger, failure handling, concurrent-run rejection, and — if US3 was implemented —
      the manual refresh endpoint and usage-counter-untouched checks)

---

## Dependencies & Execution Order

### Phase Dependencies

- **Setup (Phase 1)**: No dependencies — can start immediately
- **Foundational (Phase 2)**: Depends on Setup completion — BLOCKS all user stories
- **User Story 1 (Phase 3)**: Depends on Foundational — no dependency on US2/US3
- **User Story 2 (Phase 4)**: Depends on Foundational; extends the `RateCollectionService` file
  created in US1 (T011), so it runs after US1 in this single-service-file design
- **User Story 3 (Phase 5, optional)**: Depends on Foundational and on US1's `RateCollectionService`
  (T011) existing; independent of US2's specific tasks (T015/T016) though it benefits from them
  being in place
- **Polish (Phase 6)**: Depends on whichever of US1/US2/US3 were completed

### Parallel Opportunities

- T003, T004 (Phase 2) in parallel; T006 in parallel with T003–T005
- T008, T009, T010 (US1 tests) in parallel with each other
- T013, T014 (US2 tests) in parallel with each other
- T017 (US3 test) and T019 (US3 DTO) in parallel with each other

---

## Parallel Example: User Story 1

```bash
# Launch all tests for User Story 1 together:
Task: "FixerClientTest happy path in backend/src/test/java/.../client/FixerClientTest.java"
Task: "RateCollectionServiceTest happy path in backend/src/test/java/.../service/RateCollectionServiceTest.java"
Task: "ExchangeRateRepositoryTest upsert cases in backend/src/test/java/.../repository/ExchangeRateRepositoryTest.java"
```

---

## Implementation Strategy

### MVP First (User Story 1 Only)

1. Complete Phase 1: Setup
2. Complete Phase 2: Foundational (CRITICAL — blocks all stories)
3. Complete Phase 3: User Story 1
4. **STOP and VALIDATE**: Run quickstart.md's scheduled-collection check independently
5. Deploy/demo if ready — rates now refresh automatically every day

### Incremental Delivery

1. Setup + Foundational → foundation ready
2. Add US1 → validate independently → deploy/demo (MVP!)
3. Add US2 → validate independently (simulate a provider failure) → deploy/demo
4. **Optionally** add US3 (manual refresh) → validate independently → deploy/demo
5. Skipping US3 entirely is a valid, fully-functional stopping point

---

## Notes

- [P] tasks = different files, no dependencies
- [Story] label maps task to specific user story for traceability
- US3 (manual refresh) is explicitly optional per TASK.md §4.4 and this task list's scope — do
  not treat its absence as incomplete delivery of this feature
- Verify tests fail before implementing
- Commit after each task or logical group
- Stop at any checkpoint to validate story independently
