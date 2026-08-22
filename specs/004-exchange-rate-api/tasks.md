---

description: "Task list template for feature implementation"
---

# Tasks: Exchange Rate API

**Input**: Design documents from `/specs/004-exchange-rate-api/`

**Prerequisites**: plan.md, spec.md, research.md, data-model.md, contracts/exchange-rate-api.yaml, quickstart.md

**Tests**: Not explicitly requested as TDD in spec.md; plan.md's Project Structure names two test
files (`ExchangeRateServiceTest`, `ExchangeControllerIT`) as required deliverables of this feature
(the concurrency requirement FR-009/SC-003 has no other verification path), so they are included
as implementation tasks within their owning user story, not a separate TDD-first phase.

**Organization**: Tasks are grouped by user story to enable independent implementation and testing
of each story.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Can run in parallel (different files, no dependencies)
- **[Story]**: Which user story this task belongs to (e.g., US1, US2, US3, US4)
- Include exact file paths in descriptions

## Path Conventions

Existing single-module Spring Boot backend at `backend/`, monorepo sibling to `frontend/`. Package
root: `com.exchangerate.manager`. Contract source of truth: `contracts/openapi.yaml`.

---

## Phase 1: Setup (Contract)

**Purpose**: Land the OpenAPI contract change that both the generated server interfaces and this
feature's implementation depend on.

- [x] T001 Merge the two new paths (`GET /exchange`, `GET /exchange/usage`) and their schemas
      (`ExchangeRateResponse`, `UsageAnalyticsResponse`, `CurrencyUsageEntry`, `ProblemDetail`
      reuse) from `specs/004-exchange-rate-api/contracts/exchange-rate-api.yaml` into
      `contracts/openapi.yaml`
- [x] T002 Run backend contract regeneration (`cd backend && ./mvnw generate-sources`) and confirm
      the `ExchangeApi` server interface now declares both new operations with the expected DTOs

**Checkpoint**: Generated `ExchangeApi` interface exists with both operation signatures — controller
implementation can now begin.

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: Shared pieces every user story's implementation calls into. No user story is
independently testable until this phase is done.

**⚠️ CRITICAL**: No user story work can begin until this phase is complete

- [x] T003 [P] Create `SpreadLookup` component in
      `backend/src/main/java/com/exchangerate/manager/service/SpreadLookup.java` — immutable
      `Map<String, BigDecimal>` keyed by currency code plus a `"DEFAULT"` sentinel (`USD`→0.00,
      tier1→3.25, tier2→4.50, tier3→6.00, default→2.75 per Appendix B / data-model.md), with a
      `BigDecimal spreadFor(String currencyCode)` accessor that upper-cases the key and falls back
      to `DEFAULT`
- [x] T004 [P] Add `UnknownCurrencyException`, `SameCurrencyException`, `RateDataNotFoundException`
      in `backend/src/main/java/com/exchangerate/manager/exception/` following the existing
      exception shape used by `FixerApiException`/`CollectionInProgressException`
- [x] T005 Add handlers for the three new exception types to
      `backend/src/main/java/com/exchangerate/manager/exception/GlobalExceptionHandler.java`,
      mapping `UnknownCurrencyException`/`SameCurrencyException` → 400 `ProblemDetail` and
      `RateDataNotFoundException` → 404 `ProblemDetail`, each identifying the specific problem
      (FR-007, FR-004, FR-013)
- [x] T006 [P] Add `existsByCurrencyCode(String currencyCode)` and
      `findLatestCommonDate(String from, String to)` (native correlated-EXISTS query per
      research.md, returning `Optional<LocalDate>`) to
      `backend/src/main/java/com/exchangerate/manager/repository/ExchangeRateRepository.java`
- [x] T007 [P] Add a currency-usage-analytics query method (e.g.
      `findAllCurrencyUsage()` returning a projection of `currencyCode`, `queryCount`,
      `lastQueriedAt`) to
      `backend/src/main/java/com/exchangerate/manager/repository/CurrencyUsageRepository.java`,
      implemented as the `DISTINCT exchange_rates.currency_code LEFT JOIN currency_usage` native
      query from data-model.md so never-queried currencies return `0`/`null` instead of being
      omitted
- [x] T008 Add the atomic upsert-increment native query (`INSERT ... ON CONFLICT (currency_code)
      DO UPDATE SET query_count = currency_usage.query_count + 1, last_queried_at = now()`) as
      `incrementUsage(String currencyCode)` to
      `backend/src/main/java/com/exchangerate/manager/repository/CurrencyUsageRepository.java`
      (Constitution Principle V — no read-modify-write)

**Checkpoint**: Foundation ready — user story implementation can now begin.

---

## Phase 3: User Story 1 - Look up a spread-adjusted exchange rate (Priority: P1) 🎯 MVP

**Goal**: `GET /exchange?from=&to=&date=` returns a spread-adjusted rate computed from locally
stored data, applying the higher of the two currencies' spreads, with no external Fixer.io call.

**Independent Test**: With rate data already collected for at least one date, request a rate for a
supported currency pair with no date specified; verify the response returns a spread-adjusted rate
computed from the most recent common date, with the correct spread applied.

### Implementation for User Story 1

- [x] T009 [US1] Create `ExchangeRateLookupResult` internal record (fromCurrency, toCurrency, rate,
      rateDate, fromCurrencyUsageCount, toCurrencyUsageCount) in
      `backend/src/main/java/com/exchangerate/manager/service/ExchangeRateLookupResult.java`
- [x] T010 [US1] Create `ExchangeRateService` in
      `backend/src/main/java/com/exchangerate/manager/service/ExchangeRateService.java` with a
      `lookup(String from, String to, LocalDate date)` method: resolves effective date (supplied
      date, or `findLatestCommonDate` when absent), loads both currencies' `ExchangeRate` rows,
      applies `SpreadLookup`, computes
      `(toRateUsd / fromRateUsd) × ((100 − MAX(toSpread, fromSpread)) / 100)` with `BigDecimal`
      (explicit `MathContext`/scale per FR-014) — this task only covers the calculation path, not
      validation/error paths or the usage-counter write (see US2/US3)
- [x] T011 [P] [US1] Create `ExchangeRateResponseMapper` (MapStruct) in
      `backend/src/main/java/com/exchangerate/manager/mapper/ExchangeRateResponseMapper.java`
      mapping `ExchangeRateLookupResult` → generated `ExchangeRateResponse` DTO
- [x] T012 [US1] Implement `GET /exchange` in
      `backend/src/main/java/com/exchangerate/manager/controller/ExchangeController.java` by
      implementing the generated `ExchangeApi` method: delegate to `ExchangeRateService.lookup`,
      map via `ExchangeRateResponseMapper`, return 200
- [x] T013 [P] [US1] Unit tests for the spread formula and date-resolution logic (happy paths;
      base-currency 0% spread; explicit-date vs. no-date resolution) in
      `backend/src/test/java/com/exchangerate/manager/service/ExchangeRateServiceTest.java`
- [x] T014 [US1] Integration test for the full HTTP round trip of `GET /exchange` (happy path,
      explicit past date) in
      `backend/src/test/java/com/exchangerate/manager/controller/ExchangeControllerIT.java`

**Checkpoint**: `GET /exchange` returns a correct spread-adjusted rate for a valid pair/date, fully
testable independent of error handling or usage counting.

---

## Phase 4: User Story 2 - Safe handling of missing or invalid lookups (Priority: P1)

**Goal**: Invalid pairs, unknown currencies, and dates with no data each produce a distinct,
structured `ProblemDetail` error (400/400/404) instead of a fabricated rate, crash, or hang.

**Independent Test**: Request a rate for an unsupported currency code, and separately for a date
with no stored rate data, and verify each produces a distinct, well-structured error response.

### Implementation for User Story 2

- [x] T015 [US2] Extend `ExchangeRateService.lookup` (in `ExchangeRateService.java`) to validate
      before any calculation: reject `from == to` with `SameCurrencyException`; reject either code
      failing `existsByCurrencyCode` with `UnknownCurrencyException`; reject a resolved-date miss
      (explicit date with no row for either side, or no common date at all) with
      `RateDataNotFoundException` — all three short-circuit before touching usage counters
      (depends on T010)
- [x] T016 [P] [US2] Verify, after T002's regeneration, that the generated `ExchangeApi` parameter
      constraints reject `from`/`to` not matching `^[A-Z]{3}$` (the contract already declares this
      `pattern`); only add an explicit check in `ExchangeController.java` if the generated
      constraint does not enforce it
- [x] T017 [US2] Integration tests for all three rejected-lookup cases (unknown currency → 400,
      same-currency-both-sides → 400, no-data-for-date → 404, each a `application/problem+json`
      body identifying the specific problem) in
      `backend/src/test/java/com/exchangerate/manager/controller/ExchangeControllerIT.java`
      (depends on T014)

**Checkpoint**: All three rejected-lookup cases return distinct, structured errors; User Stories 1
and 2 together make `GET /exchange` fully correct end-to-end for both success and failure paths.

---

## Phase 5: User Story 3 - Track usage per currency (Priority: P2)

**Goal**: Every successful `GET /exchange` lookup atomically increments both involved currencies'
usage counters by exactly one, with no lost or double-counted increments under concurrency, and no
increment on a failed/rejected lookup.

**Independent Test**: Perform several successful rate lookups across different currency pairs and
verify each currency's usage count increments by exactly one per lookup; verify concurrent
simultaneous lookups don't lose increments.

### Implementation for User Story 3

- [x] T018 [US3] Extend `ExchangeRateService.lookup` (in `ExchangeRateService.java`, wrapped
      `@Transactional`) to call `CurrencyUsageRepository.incrementUsage` once for `from` and once
      for `to` only after the rate has been successfully computed, and populate
      `fromCurrencyUsageCount`/`toCurrencyUsageCount` on `ExchangeRateLookupResult` from the
      post-increment values (depends on T008, T015)
- [x] T019 [US3] Concurrent-increment test in
      `backend/src/test/java/com/exchangerate/manager/service/ExchangeRateServiceTest.java` (or a
      dedicated non-`@Transactional` test class): fire N concurrent successful lookups against the
      same currency pair via `ExecutorService`/`CountDownLatch` and assert the final `query_count`
      equals exactly N for each currency (FR-009, SC-003) — per research.md, must NOT be wrapped in
      a single test transaction, or row-lock contention won't be exercised
- [x] T020 [P] [US3] Integration test confirming a rejected lookup (from US2's cases) leaves usage
      counters unchanged in
      `backend/src/test/java/com/exchangerate/manager/controller/ExchangeControllerIT.java`
      (FR-010)

**Checkpoint**: Usage counters increment exactly once per successful lookup, survive concurrent
load without loss, and stay untouched on any rejected lookup.

---

## Phase 6: User Story 4 - View usage analytics (Priority: P2)

**Goal**: `GET /exchange/usage` returns, for every currency the system has ever stored a rate
record for, its total query count (0 if never queried) and last-queried date (null if never
queried).

**Independent Test**: After performing a known set of rate lookups across several currencies, call
the analytics endpoint and verify the returned counts and last-queried dates match the lookups
actually performed, including currencies never queried.

### Implementation for User Story 4

- [x] T021 [P] [US4] Create `UsageAnalyticsMapper` (MapStruct) in
      `backend/src/main/java/com/exchangerate/manager/mapper/UsageAnalyticsMapper.java` mapping
      the `findAllCurrencyUsage()` projection to the generated `CurrencyUsageEntry`/
      `UsageAnalyticsResponse` DTOs
- [ ] T022 [US4] Implement `GET /exchange/usage` in `ExchangeController.java` by implementing the
      generated `ExchangeApi` method: delegate to `CurrencyUsageRepository.findAllCurrencyUsage()`,
      map via `UsageAnalyticsMapper`, return 200 with `{"currencies": [...]}` (or `[]` when no
      rates have ever been stored)
- [ ] T023 [US4] Integration tests for `GET /exchange/usage` in
      `backend/src/test/java/com/exchangerate/manager/controller/ExchangeControllerIT.java`:
      mixed queried/never-queried currencies appear with correct counts/dates; a never-queried
      currency with a stored rate appears with `queryCount = 0`/`lastQueriedAt = null`; empty state
      (no lookups yet) returns a well-formed empty result, not an error

**Checkpoint**: All four user stories independently functional; `GET /exchange` and
`GET /exchange/usage` are both complete per spec.md.

---

## Phase 7: Polish & Cross-Cutting Concerns

**Purpose**: Final validation across the whole feature.

- [ ] T024 Run `cd backend && ./mvnw verify` and confirm all new and existing tests pass
- [ ] T025 Run `specs/004-exchange-rate-api/quickstart.md` Scenarios 1–4 against a live backend
      (`docker compose up -d` + `./mvnw spring-boot:run`) and confirm every expected status
      code/body/counter behavior matches

---

## Dependencies & Execution Order

### Phase Dependencies

- **Setup (Phase 1)**: No dependencies — can start immediately
- **Foundational (Phase 2)**: Depends on Setup (generated `ExchangeApi` types exist) — BLOCKS all
  user stories
- **User Story 1 (Phase 3)**: Depends on Foundational — no dependency on other stories
- **User Story 2 (Phase 4)**: Depends on Foundational; extends the same `ExchangeRateService`
  method US1 created (T010), so implement after US1's calculation path exists
- **User Story 3 (Phase 5)**: Depends on Foundational (T008) and US2 (T015 — must validate before
  incrementing); extends the same service method again
- **User Story 4 (Phase 6)**: Depends on Foundational (T007) only — independent of US1/US2/US3,
  could be built in parallel with them by a different developer
- **Polish (Phase 7)**: Depends on all four user stories being complete

### Within Each User Story

- US1: model/result shape → service calculation → mapper → controller → tests
- US2: validation added to the existing service method → controller-level input constraint → tests
- US3: counter increment added to the existing service method → concurrency test → negative test
- US4: mapper → controller → tests

### Parallel Opportunities

- T003, T004, T006, T007 (Phase 2) can run in parallel — different files, no cross-dependency
- T011 (US1 mapper) can run in parallel with T012 (US1 controller) once T009/T010 exist
- T013 (US1 unit tests) can run in parallel with T014 (US1 integration test)
- US4 (Phase 6) can be implemented in parallel with US1–US3 by a separate developer once
  Foundational is done, since it only depends on T007
- T016 (US2) and T020 (US3) are independent of their phase's other tasks and can run in parallel
  with them

---

## Parallel Example: Foundational Phase

```bash
Task: "Create SpreadLookup component in backend/.../service/SpreadLookup.java"
Task: "Add UnknownCurrencyException, SameCurrencyException, RateDataNotFoundException in backend/.../exception/"
Task: "Add existsByCurrencyCode and findLatestCommonDate to ExchangeRateRepository.java"
Task: "Add findAllCurrencyUsage() to CurrencyUsageRepository.java"
```

---

## Implementation Strategy

### MVP First (User Story 1 Only)

1. Complete Phase 1: Setup (contract merge + regen)
2. Complete Phase 2: Foundational (blocks everything)
3. Complete Phase 3: User Story 1 — happy-path spread-adjusted lookup
4. **STOP and VALIDATE**: quickstart.md Scenario 1 against a live backend
5. Deploy/demo if ready

### Incremental Delivery

1. Setup + Foundational → foundation ready
2. Add US1 → validate happy path → demo
3. Add US2 → validate all three error paths → demo (US1+US2 together satisfy Constitution
   Dev/Quality: ProblemDetail shape)
4. Add US3 → validate counters increment + survive concurrency → demo
5. Add US4 → validate analytics endpoint → demo (feature-complete per spec.md)
6. Phase 7 polish → full `./mvnw verify` + quickstart.md pass

### Parallel Team Strategy

With multiple developers, after Foundational:

- Developer A: US1 → US2 → US3 (all extend the same `ExchangeRateService.lookup` method, so
  sequential ownership avoids merge conflicts)
- Developer B: US4 (touches only `CurrencyUsageRepository`, a new mapper, and the second
  `ExchangeApi` method — no file overlap with A until the Polish phase)
