---

description: "Task list template for feature implementation"
---

# Tasks: Analytics Endpoint

**Input**: Design documents from `/specs/005-analytics-endpoint/`

**Prerequisites**: plan.md, spec.md, research.md, data-model.md, contracts/analytics-endpoints.yaml, quickstart.md

**Tests**: Included — repo convention (Constitution Principle X, `CLAUDE.md` test-isolation rule) requires Testcontainers-backed tests for DB-touching code, and existing analytics code already has test coverage (`ExchangeControllerTest`, `ExchangeRateRepositoryTest`, `CurrencyUsageRepositoryTest`, `ExchangeRateServiceTest`) that must be extended alongside it.

**Organization**: Tasks are grouped by user story (US1 = trend endpoint, US2 = ranked usage, US3 = recency-filtered usage) per spec.md priorities.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Can run in parallel (different files, no dependencies)
- **[Story]**: Which user story this task belongs to (e.g., US1, US2, US3)
- Paths are relative to repo root; backend package root is `backend/src/main/java/com/exchangerate/manager`, tests under `backend/src/test/java/com/exchangerate/manager`

## Path Conventions

Existing single-module Spring Boot backend, extended in place — see plan.md Project Structure. No new module/package root.

---

## Phase 1: Setup (Shared Infrastructure)

**Purpose**: Contract-first changes shared by all three user stories

- [X] T001 Add `/exchange/trend` path and `ExchangeRateTrendResponse`/`RateTrendPoint` schemas to `contracts/openapi.yaml`, per `specs/005-analytics-endpoint/contracts/analytics-endpoints.yaml`
- [X] T002 Extend `/exchange/usage` in `contracts/openapi.yaml` with optional `limit` (`integer`, `minimum: 1`) and `recentDays` (`integer`, `minimum: 1`) query parameters, per `specs/005-analytics-endpoint/contracts/analytics-endpoints.yaml`
- [X] T003 Regenerate backend server interfaces/DTOs by running `cd backend && ./mvnw generate-sources` and confirm `getExchangeRateTrend`/`getUsageAnalytics` signatures land in the generated API interface

**Checkpoint**: Contract updated and regenerated; generated `ExchangeRateTrendResponse`, `RateTrendPoint` DTOs and updated `getUsageAnalytics` signature exist before any story implementation begins.

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: Shared exception type and repository plumbing needed by more than one story

**⚠️ CRITICAL**: No user story work can begin until this phase is complete

- [X] T004 Create `InvalidDateRangeException` (extends the codebase's existing runtime-exception base pattern, mirroring `UnknownCurrencyException`) in `backend/src/main/java/com/exchangerate/manager/exception/InvalidDateRangeException.java`
- [X] T005 Map `InvalidDateRangeException` to a 400 `ProblemDetail` in `backend/src/main/java/com/exchangerate/manager/exception/GlobalExceptionHandler.java`, following the existing handler pattern for `UnknownCurrencyException`

**Checkpoint**: Foundation ready — US1, US2, US3 implementation can now proceed (US1 needs T004/T005; US2/US3 only need Phase 1).

---

## Phase 3: User Story 1 - View historical rate trend for a currency pair (Priority: P1) 🎯 MVP

**Goal**: `GET /exchange/trend?from=&to=&startDate=&endDate=` returns a chronologically ordered, spread-adjusted rate series computed only from locally stored data, defaulting to the most recent 30 days when no range is given.

**Independent Test**: Seed rate data for a currency pair across several consecutive dates; request the trend for that pair and range; verify one spread-adjusted entry per date both currencies have data for, oldest to newest, with no external Fixer.io call made.

### Tests for User Story 1

- [ ] T006 [P] [US1] Repository test for the trend join/range query (dates present for both currencies included, dates missing either currency's data excluded, range boundaries respected) in `backend/src/test/java/com/exchangerate/manager/repository/ExchangeRateRepositoryTest.java`
- [X] T007 [P] [US1] Service test for `ExchangeRateService` trend logic: default 30-day window when dates omitted, unknown-currency rejection, `startDate > endDate` rejection, spread-adjusted rate computed identically to `lookup`, chronological ordering, no usage-counter increment in `backend/src/test/java/com/exchangerate/manager/service/ExchangeRateServiceTest.java`
- [ ] T008 [P] [US1] Controller/integration test for `GET /exchange/trend`: happy path, empty-range-with-no-data returns empty array, unknown currency returns 400 `ProblemDetail`, `startDate > endDate` returns 400 `ProblemDetail`, usage counters unchanged before/after in `backend/src/test/java/com/exchangerate/manager/controller/ExchangeControllerIT.java`

### Implementation for User Story 1

- [X] T009 [US1] Add native join/range query to `backend/src/main/java/com/exchangerate/manager/repository/ExchangeRateRepository.java` returning one row per `rate_date` present in `exchange_rates` for both `fromCurrency` and `toCurrency` within `[startDate, endDate]`, mirroring the existing `findLatestCommonDate` native-query pattern
- [X] T010 [US1] Create `RateTrendPoint` transient DTO (`rateDate: LocalDate`, `rate: BigDecimal`) in `backend/src/main/java/com/exchangerate/manager/service/RateTrendPoint.java`
- [X] T011 [US1] Add trend method to `backend/src/main/java/com/exchangerate/manager/service/ExchangeRateService.java`: validate currencies exist (reuse `UnknownCurrencyException`) and `startDate <= endDate` (throw `InvalidDateRangeException`, depends on T004), default missing dates to `[today-29, today]`, call the T009 query, compute spread-adjusted rate per row using the same formula/`MathContext` as `lookup`, return ordered `List<RateTrendPoint>` — MUST NOT call `CurrencyUsageRepository.incrementUsage`
- [X] T012 [P] [US1] Create `ExchangeRateTrendResponseMapper` (MapStruct) mapping `List<RateTrendPoint>` + `fromCurrency`/`toCurrency` to the generated `ExchangeRateTrendResponse`/`RateTrendPoint` DTOs in `backend/src/main/java/com/exchangerate/manager/mapper/ExchangeRateTrendResponseMapper.java`
- [X] T013 [US1] Implement `getExchangeRateTrend` in `backend/src/main/java/com/exchangerate/manager/controller/ExchangeController.java`, delegating to the T011 service method and T012 mapper (depends on T009-T012)

**Checkpoint**: `GET /exchange/trend` fully functional and independently testable; MVP deliverable.

---

## Phase 4: User Story 2 - Rank currencies by usage (Priority: P2)

**Goal**: `GET /exchange/usage?limit=N` returns at most N currencies sorted by `queryCount` descending, ties broken by `currencyCode` ascending.

**Independent Test**: Seed usage counts across several currencies with distinct and tied totals; request with a limit; verify count, order, and tie-break.

### Tests for User Story 2

- [ ] T014 [P] [US2] Repository test for ranked projection query: descending `queryCount` order, `currencyCode` ascending tie-break, `limit` truncation, `limit` larger than available rows returns all rows in `backend/src/test/java/com/exchangerate/manager/repository/CurrencyUsageRepositoryTest.java`
- [ ] T015 [P] [US2] Controller/integration test for `GET /exchange/usage?limit=N`: correct count/order/tie-break, omitted `limit` returns all currencies same ordering, non-positive `limit` returns 400 `ProblemDetail` in `backend/src/test/java/com/exchangerate/manager/controller/ExchangeControllerIT.java`

### Implementation for User Story 2

- [X] T016 [US2] Add ranked projection query (order by `queryCount DESC, currencyCode ASC`, optional row limit) to `backend/src/main/java/com/exchangerate/manager/repository/CurrencyUsageRepository.java`
- [X] T017 [US2] Extend `getUsageAnalytics` in `backend/src/main/java/com/exchangerate/manager/controller/ExchangeController.java` (or a small validation step in a service, per Constitution Principle VI) to accept `limit`, validate it's positive when supplied (else 400 via `@Positive`/existing validation pattern, FR-010), and pass through to the T016 query

**Checkpoint**: US1 and US2 both independently functional.

---

## Phase 5: User Story 3 - Filter usage analytics by recency (Priority: P3)

**Goal**: `GET /exchange/usage?recentDays=N` returns only currencies whose `lastQueriedAt` falls within the last N days; never-queried currencies excluded. Composable with `limit` from US2.

**Independent Test**: Seed currencies with varying/absent last-queried dates; request with a `recentDays` window; verify only in-window currencies returned, never-queried ones excluded.

### Tests for User Story 3

- [ ] T018 [P] [US3] Repository test for recency filter: currencies within window included, outside window excluded, never-queried (`lastQueriedAt == null`) excluded, composed correctly with the T016 ranking query in `backend/src/test/java/com/exchangerate/manager/repository/CurrencyUsageRepositoryTest.java`
- [ ] T019 [P] [US3] Controller/integration test for `GET /exchange/usage?recentDays=N`: correct filtering, never-queried excluded, non-positive `recentDays` returns 400 `ProblemDetail`, combined `limit`+`recentDays` request in `backend/src/test/java/com/exchangerate/manager/controller/ExchangeControllerIT.java`

### Implementation for User Story 3

- [X] T020 [US3] Extend the T016 query in `backend/src/main/java/com/exchangerate/manager/repository/CurrencyUsageRepository.java` with an optional recency filter (`lastQueriedAt >= now() - recentDays days`, excluding null `lastQueriedAt`), composable with the existing ranking/limit logic
- [X] T021 [US3] Extend `getUsageAnalytics` in `backend/src/main/java/com/exchangerate/manager/controller/ExchangeController.java` to accept `recentDays`, validate it's positive when supplied (400 on failure, FR-010), and pass through to the T020 query

**Checkpoint**: All three user stories independently functional and composable.

---

## Phase 6: Polish & Cross-Cutting Concerns

**Purpose**: Regenerate the frontend client and validate the full feature end-to-end

- [ ] T022 [P] Regenerate frontend typed client via `cd frontend && npm run generate:api` so `frontend/src/app/api-client/` reflects `getExchangeRateTrend` and the extended `getUsageAnalytics` signature (no hand-editing generated output)
- [ ] T023 Run `cd backend && ./mvnw verify` to confirm all new and existing tests pass (Testcontainers-backed)
- [ ] T024 Execute all `quickstart.md` validation steps against a running instance (default/explicit trend windows, invalid range, unknown currency, ranked usage, recency filter, parameter rejection, usage-counter side-effect check) and confirm expected responses

---

## Dependencies & Execution Order

### Phase Dependencies

- **Setup (Phase 1)**: No dependencies — start immediately
- **Foundational (Phase 2)**: Depends on Phase 1 (needs generated DTOs/interfaces in place before wiring exceptions used by them) — BLOCKS User Story 1
- **User Story 1 (Phase 3)**: Depends on Phase 1 + Phase 2 (needs `InvalidDateRangeException`)
- **User Story 2 (Phase 4)**: Depends on Phase 1 only — independent of Phase 2 and US1
- **User Story 3 (Phase 5)**: Depends on Phase 1 + Phase 4 (extends the same `CurrencyUsageRepository` query US2 adds, T016/T020 touch the same method)
- **Polish (Phase 6)**: Depends on all desired user stories being complete

### User Story Dependencies

- **US1 (P1)**: Independent of US2/US3 — different repository (`ExchangeRateRepository`) and controller method (`getExchangeRateTrend`)
- **US2 (P2)**: Independent of US1 — can start right after Phase 1
- **US3 (P3)**: Builds on US2's query in `CurrencyUsageRepository` (both extend the same ranked-projection method) — implement after US2 lands to avoid two developers editing the same query concurrently

### Within Each User Story

- Tests before implementation (write and confirm they fail first)
- Repository query before service/controller wiring
- Story complete and independently testable before moving to the next priority

### Parallel Opportunities

- T001, T002 can be done together (same file, do sequentially in practice) then T003 after both
- T006, T007, T008 (US1 tests) in parallel — different files, no shared state
- T012 in parallel with T009-T011 (separate mapper file)
- T014, T015 (US2 tests) in parallel
- T018, T019 (US3 tests) in parallel
- US1 (Phase 3) and US2 (Phase 4) can be implemented in parallel by different developers once Phase 1/2 complete — they touch disjoint files (`ExchangeRateRepository`/`ExchangeRateService` vs `CurrencyUsageRepository`), only converging in `ExchangeController.java` for the final wiring tasks (T013 vs T017)

---

## Parallel Example: User Story 1

```bash
# Launch all tests for User Story 1 together:
Task: "Repository test for trend join/range query in ExchangeRateRepositoryTest.java"
Task: "Service test for trend validation/computation in ExchangeRateServiceTest.java"
Task: "Controller/integration test for GET /exchange/trend in ExchangeControllerIT.java"

# Mapper can be built alongside the repository/service work:
Task: "Create ExchangeRateTrendResponseMapper in mapper/ExchangeRateTrendResponseMapper.java"
```

---

## Implementation Strategy

### MVP First (User Story 1 Only)

1. Complete Phase 1: Setup (contract + codegen)
2. Complete Phase 2: Foundational (`InvalidDateRangeException`)
3. Complete Phase 3: User Story 1 (trend endpoint)
4. **STOP and VALIDATE**: run quickstart.md US1 steps independently
5. Deploy/demo if ready — trend endpoint alone unblocks frontend trend charts and the future AI insight feature

### Incremental Delivery

1. Setup + Foundational → contract and exception plumbing ready
2. Add US1 (trend endpoint) → validate independently → MVP
3. Add US2 (ranked usage) → validate independently
4. Add US3 (recency-filtered usage, extends US2's query) → validate independently
5. Polish: regenerate frontend client, full `mvnw verify`, full quickstart pass

## Notes

- [P] tasks touch different files with no dependency on an incomplete task
- US3 intentionally sequenced after US2 (not fully parallel) because both extend the same `CurrencyUsageRepository` ranking query — avoids merge conflicts on one method
- Verify tests fail before implementing each task
- Commit after each task or logical group
- Constitution Principle VI (thin controllers, service-layer validation) applies to all new controller wiring (T013, T017, T021)
