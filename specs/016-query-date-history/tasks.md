---

description: "Task list template for feature implementation"
---

# Tasks: Query Timestamp History

**Input**: Design documents from `/specs/016-query-date-history/`

**Prerequisites**: plan.md, spec.md, research.md, data-model.md, contracts/usage-analytics-history.yaml, quickstart.md

**Tests**: Included — repo convention (Constitution Principle X, `CLAUDE.md` test-isolation rule) requires Testcontainers-backed tests for DB-touching code, and the existing usage-analytics code already has test coverage (`ExchangeControllerIT`, `CurrencyUsageRepositoryTest`, `ExchangeRateServiceTest`) that must be extended alongside it.

**Organization**: Tasks are grouped by user story (US1 = record & serve every query moment, US2 = bound history with the existing `recentDays` window, US3 = existing consumers unaffected) per spec.md priorities, plus a cross-cutting retention-purge phase (FR-022–FR-025) that no single user story owns.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Can run in parallel (different files, no dependencies)
- **[Story]**: Which user story this task belongs to (e.g., US1, US2, US3)
- Paths are relative to repo root; backend package root is `backend/src/main/java/com/exchangerate/manager`, tests under `backend/src/test/java/com/exchangerate/manager`

## Path Conventions

Existing single-module Spring Boot backend, extended in place — see plan.md Project Structure. No new module/package root. Frontend is touched only to regenerate the committed API client (no UI work — out of scope per spec Assumptions).

---

## Phase 1: Setup (Shared Infrastructure)

**Purpose**: Contract-first change shared by every story — nothing else compiles until the generated DTO carries the new field

- [X] T001 Add `queryTimestamps` to `CurrencyUsageEntry` in `contracts/openapi.yaml` (add to both `required` and `properties`; `type: array`, `items: {type: string, format: date-time}`, **not** `nullable`), and update the `recentDays` parameter description and the `getUsageAnalytics` operation summary to document its dual role (currency selection unchanged; also trims returned history; 90-day default when omitted; explicit value always honored in full) — per `specs/016-query-date-history/contracts/usage-analytics-history.yaml`
- [X] T002 Regenerate backend server interfaces/DTOs by running `cd backend && ./mvnw generate-sources` and confirm the generated `CurrencyUsageEntry` now exposes a non-nullable `List<OffsetDateTime> queryTimestamps`

**Checkpoint**: Contract updated and regenerated; the generated `CurrencyUsageEntry.queryTimestamps` exists before any story implementation begins.

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: The new event-log table, its entity, and the repository both later stories build on

**⚠️ CRITICAL**: No user story work can begin until this phase is complete

- [X] T003 [P] Create `CurrencyQueryEvent` JPA entity (`id` identity PK, `currencyCode` `CHAR(3)` with `@Pattern("^[A-Z]{3}$")`, `queriedAt: Instant`), following the `CurrencyUsage`/`ExchangeRate` Lombok conventions, in `backend/src/main/java/com/exchangerate/manager/entity/CurrencyQueryEvent.java`
- [X] T004 [P] Create Flyway migration `V4__create_currency_query_event.sql`: `CREATE TABLE currency_query_event (id BIGSERIAL PRIMARY KEY, currency_code CHAR(3) NOT NULL CHECK (currency_code ~ '^[A-Z]{3}$'), queried_at TIMESTAMPTZ NOT NULL)` with **no unique constraint** (FR-002 requires duplicate timestamps to persist); add index `idx_currency_query_event_code_queried_at ON currency_query_event (currency_code, queried_at)`; then one-time seed `INSERT INTO currency_query_event (currency_code, queried_at) SELECT currency_code, last_queried_at FROM currency_usage` in the same migration so re-running Flyway can never duplicate it (FR-020, FR-021) — in `backend/src/main/resources/db/migration/V4__create_currency_query_event.sql`
- [X] T005 [P] Create `CurrencyUsageSummary` record (`currencyCode: String`, `queryCount: long`, `lastQueriedAt: Instant`, `queryTimestamps: List<Instant>`) in `backend/src/main/java/com/exchangerate/manager/service/CurrencyUsageSummary.java`
- [X] T006 Create `CurrencyQueryEventRepository` (depends on T003, T004) in `backend/src/main/java/com/exchangerate/manager/repository/CurrencyQueryEventRepository.java` with:
  - `insertEvents(String firstCurrencyCode, String secondCurrencyCode)`: a single native two-row `INSERT INTO currency_query_event (currency_code, queried_at) VALUES (:firstCurrencyCode, now()), (:secondCurrencyCode, now())` (`now()` = transaction timestamp, matching the `incrementUsage` upsert exactly)
  - `findQueryTimestamps(List<String> currencyCodes, Integer windowDays)`: native query `SELECT currency_code AS currencyCode, queried_at AS queriedAt FROM currency_query_event WHERE currency_code IN (:currencyCodes) AND queried_at >= now() - (:windowDays || ' days')::interval ORDER BY currency_code ASC, queried_at ASC, id ASC`
  - `CurrencyQueryEventProjection` interface (`getCurrencyCode()`, `getQueriedAt()`)

**Checkpoint**: Foundation ready — US1 implementation can now proceed. (US2 and US3 also depend on this phase, but only reach it via US1's code.)

---

## Phase 3: User Story 1 - See every moment a currency was queried (Priority: P1) 🎯 MVP

**Goal**: Every successful rate query records a timestamp for both participating currencies, and `GET /exchange/usage` returns each currency's recorded moments (within a 90-day default window) alongside the existing count and last-queried fields.

**Independent Test**: Perform several rate queries involving a currency at distinct moments, then request usage analytics; verify the response lists exactly those query timestamps for that currency, in addition to the existing count and last-queried values.

### Tests for User Story 1

- [X] T007 [P] [US1] Repository test for `CurrencyQueryEventRepository` in `backend/src/test/java/com/exchangerate/manager/repository/CurrencyQueryEventRepositoryTest.java`: `insertEvents` writes one row per currency with an identical `queried_at`; two calls sharing the same instant both persist (no collapsing); `findQueryTimestamps` returns rows ordered by `(currency_code, queried_at, id)`, excludes rows outside the window, and returns an empty list (not an error) for a currency code with no rows
- [X] T008 [P] [US1] Extend `ExchangeRateServiceTest` in `backend/src/test/java/com/exchangerate/manager/service/ExchangeRateServiceTest.java`: a successful `lookup()` records exactly one event per currency (five successful calls for the same pair leave five events per currency, matching `queryCount`); `SameCurrencyException`, `UnknownCurrencyException`, and `RateDataNotFoundException` paths record zero events
- [X] T009 [P] [US1] Create `UsageAnalyticsServiceTest` in `backend/src/test/java/com/exchangerate/manager/service/UsageAnalyticsServiceTest.java`: a queried currency's `queryTimestamps` come back chronological oldest-first with the `id` tie-break for identical timestamps; the newest entry equals `lastQueriedAt` exactly; a never-queried currency (`queryCount == 0`) returns `queryTimestamps: []`, never null; two identical requests return byte-identical results
- [X] T010 [P] [US1] Extend `ExchangeControllerIT` in `backend/src/test/java/com/exchangerate/manager/controller/ExchangeControllerIT.java`: `GET /exchange/usage` response includes a non-null `queryTimestamps` array per currency; a never-queried currency has `[]`; a rejected query (same currency, unknown currency, no rate for date) and the manual refresh endpoint add zero timestamps

### Implementation for User Story 1

- [X] T011 [US1] In `ExchangeRateService.lookup()` (`backend/src/main/java/com/exchangerate/manager/service/ExchangeRateService.java`), after both `incrementUsage` calls, call `currencyQueryEventRepository.insertEvents(firstCurrency, secondCurrency)` inside the method's existing `@Transactional` boundary — placed after every validation/lookup failure point, so a failed query throws before this line is reached
- [X] T012 [US1] Create `UsageAnalyticsService` (`@Service`, `@RequiredArgsConstructor`, `@Transactional(readOnly = true)`) in `backend/src/main/java/com/exchangerate/manager/service/UsageAnalyticsService.java` with a `DEFAULT_HISTORY_WINDOW_DAYS = 90` constant and `getUsageAnalytics(Integer limit, Integer recentDays)`: call the existing, unchanged `currencyUsageRepository.findCurrencyUsage(limit, recentDays)` for currency selection; if it returns no rows, return an empty list immediately (avoids an invalid `IN ()`); otherwise extract the currency codes, call `currencyQueryEventRepository.findQueryTimestamps(codes, DEFAULT_HISTORY_WINDOW_DAYS)` (hardcoded default for this story — US2 wires `recentDays` through), group the flat rows into a `Map<String, List<Instant>>`, and assemble one `CurrencyUsageSummary` per selected currency defaulting to `List.of()` when it has no grouped entries
- [X] T013 [P] [US1] Update `UsageAnalyticsMapper` in `backend/src/main/java/com/exchangerate/manager/mapper/UsageAnalyticsMapper.java` to map `CurrencyUsageSummary → CurrencyUsageEntry`, adding `queryTimestamps` (`List<Instant> → List<OffsetDateTime>` via `.atOffset(ZoneOffset.UTC)` per entry) alongside the existing `currencyCode`/`queryCount`/`lastQueriedAt` mapping; update `toResponse` to accept `List<CurrencyUsageSummary>`
- [X] T014 [US1] Update `ExchangeController.getUsageAnalytics` in `backend/src/main/java/com/exchangerate/manager/controller/ExchangeController.java` to depend on `UsageAnalyticsService` instead of injecting `CurrencyUsageRepository` directly — delegates selection, history assembly, and mapping to it (removes the direct repository dependency, correcting the pre-existing Constitution Principle VI layering gap) (depends on T012, T013)

**Checkpoint**: At this point, User Story 1 is fully functional and independently testable — every successful query is recorded for both currencies and `GET /exchange/usage` returns each currency's history within the default 90-day window.

---

## Phase 4: User Story 2 - Keep the query history from bloating the response (Priority: P2)

**Goal**: The existing `recentDays` parameter also bounds each currency's returned history — explicit values (including ones wider than the 90-day default) are honored in full; omitting it applies the 90-day default without changing which currencies are selected.

**Independent Test**: Record query timestamps spanning a long period for one currency, request analytics with a recency window covering only part of that period, and verify only timestamps inside the window are returned while the currency's lifetime query count remains the full total.

### Tests for User Story 2

- [X] T015 [P] [US2] Extend `UsageAnalyticsServiceTest` in `backend/src/test/java/com/exchangerate/manager/service/UsageAnalyticsServiceTest.java`: `recentDays=30` returns only in-window timestamps while `queryCount` still reports the full lifetime total; `recentDays=180` (wider than the 90-day default) returns the full 180 days, not narrowed to 90; a `recentDays` wider than the 365-day retention period succeeds and returns whatever history remains retained; omitting `recentDays` still applies the 90-day default
- [X] T016 [P] [US2] Extend `ExchangeControllerIT` in `backend/src/test/java/com/exchangerate/manager/controller/ExchangeControllerIT.java`: `GET /exchange/usage?recentDays=N` trims `queryTimestamps` to `N` days without changing `queryCount`; a currency with a large number of timestamps inside the window returns every one of them, untruncated (SC-010)

### Implementation for User Story 2

- [X] T017 [US2] In `UsageAnalyticsService.getUsageAnalytics` (`backend/src/main/java/com/exchangerate/manager/service/UsageAnalyticsService.java`), resolve the effective history window as `recentDays != null ? recentDays : DEFAULT_HISTORY_WINDOW_DAYS` and pass it to `findQueryTimestamps` in place of T012's hardcoded default — no `min()`/`max()` anywhere, so an explicit value always wins even when wider than the default (depends on T012)

**Checkpoint**: At this point, User Stories 1 AND 2 both work independently — `recentDays` now bounds both currency selection (unchanged) and returned history, with explicit requests always honored in full.

---

## Phase 5: User Story 3 - Existing analytics consumers keep working (Priority: P3)

**Goal**: Every field and option an existing client already relies on — `currencyCode`, `queryCount`, `lastQueriedAt`, ranking, `limit`, and `recentDays`'s currency-selection behavior — is unchanged in shape, meaning, and ordering.

**Independent Test**: Exercise the analytics endpoint the way an existing client does — with and without the current ranking and recency options — and verify every previously returned field is present with unchanged meaning and ordering.

### Tests for User Story 3

- [X] T018 [P] [US3] Extend `CurrencyUsageRepositoryTest` in `backend/src/test/java/com/exchangerate/manager/repository/CurrencyUsageRepositoryTest.java`: re-assert `findCurrencyUsage`/`findAllCurrencyUsage` still rank by `queryCount DESC, currencyCode ASC`, still apply `limit` and the recency-based currency-selection filter exactly as before this feature, on the unmodified query from R-003
- [X] T019 [P] [US3] Extend `ExchangeControllerIT` in `backend/src/test/java/com/exchangerate/manager/controller/ExchangeControllerIT.java`: a client reading only `currencyCode`/`queryCount`/`lastQueriedAt` gets correct, unchanged values; `limit` and `recentDays` combined behave identically to the pre-feature endpoint (an unrecognized extra `queryTimestamps` field does not break a strict-shape assertion built to only check the three original fields)

**Checkpoint**: All three user stories are now independently functional. No production code changes were needed for this story — `CurrencyUsageRepository`'s selection query (T006's dependency, untouched since Phase 2) already guarantees it; these tasks are regression proof.

---

## Phase 6: Retention Purge (Cross-Cutting)

**Purpose**: Bound stored history to 365 days without ever touching `currency_usage`, using the platform's existing multi-instance-safe scheduling mechanism (FR-022–FR-025, SC-011, SC-012) — not owned by any single user story, so it lands after US1–US3 are independently proven

- [X] T020 Add a batched expiry-delete to `CurrencyQueryEventRepository` (`backend/src/main/java/com/exchangerate/manager/repository/CurrencyQueryEventRepository.java`): `deleteExpiredBatch(int batchSize)` running `DELETE FROM currency_query_event WHERE ctid IN (SELECT ctid FROM currency_query_event WHERE queried_at < now() - INTERVAL '365 days' LIMIT :batchSize)`, returning the affected row count (depends on T004, T006)
- [X] T021 [P] Create `QueryEventPurgeService` (`@Service`, `@RequiredArgsConstructor`) in `backend/src/main/java/com/exchangerate/manager/service/QueryEventPurgeService.java` with a `@SchedulerLock(name = "query-event-retention-purge")`-annotated method that loops `deleteExpiredBatch(10_000)` until a call returns `0`, each batch in its own transaction (depends on T020)
- [X] T022 [P] Create `QueryEventPurgeScheduler` (`@Component`, `@RequiredArgsConstructor`) in `backend/src/main/java/com/exchangerate/manager/scheduler/QueryEventPurgeScheduler.java` with `@Scheduled(cron = "0 30 2 * * *", zone = "GMT")` delegating to `QueryEventPurgeService`, following `RateCollectionScheduler`'s thin-delegate shape and offset well clear of the existing `00:05 GMT` ingestion job (depends on T021)
- [X] T023 [P] Create `QueryEventPurgeServiceTest` (Testcontainers) in `backend/src/test/java/com/exchangerate/manager/service/QueryEventPurgeServiceTest.java`: events older than 365 days are removed and events inside retention are kept; every `currency_usage.query_count` and `last_queried_at` is byte-identical before and after; seeding more than one batch's worth of expired rows exercises the loop; a rate lookup issued concurrently with the purge still succeeds and its new event survives (depends on T020)

**Checkpoint**: Retained `currency_query_event` volume stops growing once the platform has run longer than 365 days; `currency_usage` is structurally unreachable by the purge.

---

## Phase 7: Polish & Cross-Cutting Concerns

**Purpose**: Regenerate the frontend client and validate the full feature end-to-end

- [X] T024 [P] Regenerate the frontend typed client via `cd frontend && npm run generate:api` so `frontend/src/app/api-client/` reflects `queryTimestamps` on `CurrencyUsageEntry` (review and commit the diff — never hand-edit generated output)
- [X] T025 Run `cd backend && ./mvnw verify` to confirm the full suite (new and existing, all Testcontainers-backed) passes
- [X] T026 Execute `quickstart.md` Scenarios 1–8 against a running instance (recording for both currencies, ordering/agreement with `lastQueriedAt`, window trimming and explicit-wins-over-default, non-recording paths, backward compatibility, rollout seeding, retention purge, 1,000-way concurrency) and confirm expected responses
- [X] T027 Execute `quickstart.md` Scenario 9 (p95 latency at the ~100,000-event reference volume) and record the measured result against the SC-005 target (< 1s p95)

---

## Dependencies & Execution Order

### Phase Dependencies

- **Setup (Phase 1)**: No dependencies — start immediately
- **Foundational (Phase 2)**: Independent of Setup (the entity, migration, and repository don't reference the generated DTO) — but both Phase 1 and Phase 2 must be complete before Phase 3, since US1 needs the regenerated `CurrencyUsageEntry` (T002) and the repository (T006)
- **User Story 1 (Phase 3)**: Depends on Phase 1 + Phase 2
- **User Story 2 (Phase 4)**: Depends on Phase 3 (T017 modifies the window-resolution line T012 introduced) — sequential, not parallel, with US1
- **User Story 3 (Phase 5)**: Depends on Phase 1 only for the response-shape assertions to compile against the new field, but is otherwise independent of Phase 3/4 — can run any time after Setup, since it verifies code (`CurrencyUsageRepository`) that this feature never touches
- **Retention Purge (Phase 6)**: Depends on Phase 2 (T004, T006) only — independent of US1/US2/US3, can be built in parallel with them by a different developer
- **Polish (Phase 7)**: Depends on all of Phase 3–6 being complete

### User Story Dependencies

- **US1 (P1)**: No dependency on US2/US3 — delivers a complete, independently useful increment (recording + default-windowed serving)
- **US2 (P2)**: Builds directly on US1's `UsageAnalyticsService` (same method, same file) — implement after US1 lands to avoid two developers editing `getUsageAnalytics` concurrently
- **US3 (P3)**: Independent of US1/US2 — it proves that code neither story touches (`CurrencyUsageRepository`'s selection query) still behaves correctly

### Within Each User Story

- Tests before implementation (write and confirm they fail first)
- Repository/entity work before service logic
- Service logic before controller/mapper wiring
- Story complete and independently testable before moving to the next priority

### Parallel Opportunities

- T003, T004, T005 (entity, migration, summary record) in parallel — different files, no shared state
- T007, T008, T009, T010 (US1 tests) in parallel — different files
- T013 in parallel with T011/T012 (separate mapper file)
- T015, T016 (US2 tests) in parallel
- T018, T019 (US3 tests) in parallel; the whole of Phase 5 can run in parallel with Phase 3/4 by a different developer
- T021, T022, T023 (Phase 6) in parallel once T020 lands; the whole of Phase 6 can run in parallel with Phase 3–5
- T024 (frontend regen) in parallel with T025 (backend verify)

---

## Parallel Example: User Story 1

```bash
# Launch all tests for User Story 1 together:
Task: "Repository test for insertEvents/findQueryTimestamps in CurrencyQueryEventRepositoryTest.java"
Task: "Service test for event recording on success/failure paths in ExchangeRateServiceTest.java"
Task: "Service test for history assembly/ordering/agreement in UsageAnalyticsServiceTest.java"
Task: "Controller/integration test for GET /exchange/usage response shape in ExchangeControllerIT.java"

# Mapper can be built alongside the service work:
Task: "Update UsageAnalyticsMapper to map CurrencyUsageSummary in mapper/UsageAnalyticsMapper.java"
```

---

## Implementation Strategy

### MVP First (User Story 1 Only)

1. Complete Phase 1: Setup (contract + backend codegen)
2. Complete Phase 2: Foundational (event table, entity, repository)
3. Complete Phase 3: User Story 1 (recording + default-windowed serving)
4. **STOP and VALIDATE**: run quickstart.md Scenarios 1, 2, 4 independently
5. Deploy/demo if ready — operators already get full query-moment history within a 90-day window

### Incremental Delivery

1. Setup + Foundational → event table and generated DTO ready
2. Add US1 (record + serve, default window) → validate independently → MVP
3. Add US2 (`recentDays` also bounds history) → validate independently
4. Add US3 (backward-compatibility regression proof) → validate independently, any time after Setup
5. Add Retention Purge (365-day bound, counters untouched) → validate independently, any time after Foundational
6. Polish: regenerate frontend client, full `mvnw verify`, full quickstart pass including latency measurement

### Parallel Team Strategy

With multiple developers, once Setup + Foundational are done:

- Developer A: User Story 1 → then User Story 2 (sequential, same file)
- Developer B: User Story 3 (independent — touches only test files)
- Developer C: Retention Purge (independent — touches only new files)

## Notes

- [P] tasks touch different files with no dependency on an incomplete task
- US2 intentionally sequenced after US1 (not parallel) because both touch the same `UsageAnalyticsService.getUsageAnalytics` method — avoids a merge conflict on one method's window-resolution line
- The Retention Purge phase is deliberately not labeled with a `[Story]` tag: FR-022–FR-025 aren't owned by any of the three spec.md user stories, but are still MUST requirements, so they get their own cross-cutting phase per the plan's Complexity Tracking and research.md R-006
- Verify tests fail before implementing each task
- Commit after each task or logical group
- Constitution Principle VI (thin controllers, service-layer logic) applies to T014; Principle IV (multi-instance scheduler safety) applies to T021/T022; Principle X (Testcontainers-only) applies to every test task
