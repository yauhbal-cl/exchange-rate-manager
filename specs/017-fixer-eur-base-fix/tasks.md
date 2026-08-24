---

description: "Task list for Fixer EUR Base Currency Ingestion Fix"
---

# Tasks: Fixer EUR Base Currency Ingestion Fix

**Input**: Design documents from `/specs/017-fixer-eur-base-fix/`

**Prerequisites**: plan.md, spec.md, research.md, data-model.md, quickstart.md (all present)

**Tests**: Not separately requested (no TDD ask in spec), but User Story 3 *is itself* the test-fixture correction — those tasks are mandatory, not optional, per FR-006/SC-003.

**Organization**: Single-file backend fix (`RateCollectionService.collect()`); no Setup or Foundational phase needed — no new project scaffolding, dependencies, or shared infrastructure. Tasks are grouped by the three user stories from spec.md.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Can run in parallel (different files, no dependencies)
- **[Story]**: Which user story this task belongs to (US1, US2, US3)

## Path Conventions

Backend-only feature: `backend/src/main/java/com/exchangerate/manager/service/RateCollectionService.java` and `backend/src/test/java/com/exchangerate/manager/service/RateCollectionServiceTest.java`. No frontend or contract changes.

---

## Phase 1: User Story 1 - Valid EUR-based response is accepted even without an EUR entry (Priority: P1) 🎯 MVP

**Goal**: A genuine Fixer response (`base = "EUR"`, no `EUR` key in `rates`) no longer throws `RateCollectionException`.

**Independent Test**: Feed `collect()` a response with `base = "EUR"` and a `rates` map without an `EUR` key; confirm it returns normally instead of throwing.

- [X] T001 [US1] In `backend/src/main/java/com/exchangerate/manager/service/RateCollectionService.java`, remove the self-rate presence/equality check (current lines 58-65: `rates.get(expectedBaseCurrency)` null/`compareTo(ONE)` guard that throws `RateCollectionException`), and instead build a local copy of `response.getRates()` with `expectedBaseCurrency -> BigDecimal.ONE` merged in (overwriting any pre-existing entry for that key), before the existing per-currency `upsert` loop runs. Leave the earlier `actualBaseCurrency` missing/blank/mismatch check (lines 50-56) unchanged.
- [X] T002 [US1] In `backend/src/test/java/com/exchangerate/manager/service/RateCollectionServiceTest.java`, replace `collectThrowsAndWritesNothingWhenBaseCurrencySelfRateIsMissing` with a test asserting *acceptance*: `base = "EUR"`, `rates = {"USD": 1.080000, "GBP": 0.860000, "JPY": 160.500000}` (no `EUR` key), call `collect()`, assert no exception is thrown and `exchangeRateRepository.upsert(...)` is invoked for `EUR` with rate `1 / 1.080000` and for `USD`, `GBP`, `JPY` — four upserts total (matches quickstart.md's manual scenario).
- [X] T003 [US1] In the same test file, replace `collectThrowsAndWritesNothingWhenBaseCurrencySelfRateIsNotExactlyOne` with a test covering the spec's edge case (spec.md lines 91-94): `base = "EUR"`, `rates` *does* contain an `"EUR"` entry with a wrong value (e.g. `0.980000`) alongside `USD`; assert `collect()` still succeeds and `EUR` is upserted with the overridden self-rate of exactly `1 / eurToUsd`, not a value derived from the stale `0.980000` entry.

**Checkpoint**: `collect()` accepts real Fixer-shaped responses; User Story 1 is independently verifiable.

---

## Phase 2: User Story 2 - EUR is persisted as its own rate record (Priority: P1)

**Goal**: Confirm EUR flows through the same upsert path as every other currency, with rate `1` and the run's shared `rateDate`.

**Independent Test**: Run `collect()` against a `base = "EUR"` response with no `EUR` key in `rates`, then confirm `exchangeRateRepository.upsert("EUR", ..., rateDate)` was called with the run's `rateDate` and a rate derived from self-rate `1`.

- [ ] T004 [US2] In `backend/src/test/java/com/exchangerate/manager/service/RateCollectionServiceTest.java`, update `collectsAndUpsertsCrossRatesToUsdForEveryCurrencyInResponse`: remove the synthetic `rates.put("EUR", BigDecimal.ONE)` fixture line, and change `expectedEur` to be derived from `BigDecimal.ONE.divide(eurToUsd, 6, RoundingMode.HALF_UP)` instead of reading a (now-absent) `rates.get("EUR")`. Keep the existing four `verify(...)` assertions (USD, GBP, JPY, EUR) and the `times(4)` total.
- [ ] T005 [US2] In the same test file, update `collectUpsertsOnlyCurrenciesPresentInResponse`: remove the synthetic `rates.put("EUR", BigDecimal.ONE)` fixture line (keep `USD`, `GBP`), and keep the assertion that `EUR` is still upserted alongside them with `times(3)` total — proving EUR is persisted purely from the synthesized self-rate, not from a fixture entry.

**Checkpoint**: EUR persistence is verified independent of whether the fixture ever included an `EUR` key — both User Story 1 and 2 are satisfied by T001-T005.

---

## Phase 3: User Story 3 - Tests describe the real provider format (Priority: P2)

**Goal**: No test fixture relies on an artificial base-currency self-rate entry; the suite documents Fixer's real shape.

**Independent Test**: Inspect `RateCollectionServiceTest.java` and confirm no fixture contains a synthetic `"EUR": 1`-style self-rate entry used to satisfy the old check, and at least one test exercises `base = "EUR"` with `EUR` absent from `rates`.

- [ ] T006 [P] [US3] In `backend/src/test/java/com/exchangerate/manager/service/RateCollectionServiceTest.java`, in `collectThrowsAndWritesNothingWhenResponseBaseCurrencyIsNull`, remove the synthetic `rates.put("EUR", BigDecimal.ONE)` fixture line (keep `USD`) — this fixture only needs to prove rejection on a null `base`, not carry a leftover self-rate hack. No assertion changes needed (still `never()` on upsert).
- [ ] T007 [US3] Re-read the full test file and confirm against FR-006/SC-003: zero remaining fixtures use an artificial base-currency-equals-1 entry to pass validation, and T002's test (base `"EUR"`, no `EUR` key in `rates`) is the canonical real-format fixture. Leave `collectThrowsAndWritesNothingWhenResponseBaseCurrencyIsNotExpectedBase`'s `"EUR": 0.930000` entry as-is — that's a legitimate USD-base cross-rate, not the self-rate hack this feature removes.

**Checkpoint**: All three user stories satisfied; test suite matches Fixer's real response shape.

---

## Phase 4: Polish & Validation

- [ ] T008 Run `cd backend && ./mvnw test -Dtest=RateCollectionServiceTest` per quickstart.md; confirm all tests pass.
- [ ] T009 Run `cd backend && ./mvnw verify` per quickstart.md; confirm no regression elsewhere in the backend suite.

---

## Dependencies & Execution Order

- **T001** (implementation) blocks **T002-T006** (all depend on the new merge-based behavior to pass).
- **T002** and **T003** touch the same test file but different test methods — sequential edits to avoid clobbering each other's changes, not a true data dependency.
- **T004** and **T005** likewise share the file with T002/T003/T006; treat as sequential edits within the file even though logically independent per-story.
- **T006** has no dependency on T002-T005's specific edits (different test method) — marked `[P]` for conceptual independence, but still shares the file, so apply serially in practice.
- **T007** is a read-only audit — run after T002-T006 land.
- **T008-T009** depend on all of T001-T007.

### User Story Dependencies

- **US1 (P1)**: No dependencies on other stories — the core fix.
- **US2 (P1)**: Relies on US1's code change (T001); independently testable/verifiable once T001 lands.
- **US3 (P2)**: Relies on US1's code change (T001) to make flipped assertions true; otherwise independent of US2's specific test edits.

---

## Implementation Strategy

### MVP First (User Story 1 Only)

1. T001 — fix `collect()`.
2. T002, T003 — prove US1's acceptance behavior, including the edge case.
3. **STOP and VALIDATE**: `./mvnw test -Dtest=RateCollectionServiceTest` for just those methods.

### Incremental Delivery

1. T001-T003 → US1 done, defect fixed, MVP.
2. T004-T005 → US2 confirmed (EUR persisted correctly).
3. T006-T007 → US3 confirmed (no synthetic fixtures remain).
4. T008-T009 → full regression validation.

## Notes

- No `[P]` parallel-file opportunities beyond T006 conceptually — T001 and the test file edits are the only two files in scope, and all test edits land in the same file, so treat the test tasks as sequential in execution even where marked independent by story.
- Commit after each task or logical group.
- This feature intentionally does not introduce a shared fixture-builder helper (per research.md's rejected alternative) — keep each test's inline `LinkedHashMap` construction.
