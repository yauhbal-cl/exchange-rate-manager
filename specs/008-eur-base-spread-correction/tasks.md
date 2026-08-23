---

description: "Task list template for feature implementation"
---

# Tasks: EUR Base Currency Spread Correction

**Input**: Design documents from `/specs/008-eur-base-spread-correction/`

**Prerequisites**: plan.md, spec.md, research.md, data-model.md, quickstart.md

**Tests**: Named explicitly as deliverables in plan.md's Project Structure and quickstart.md
(`ExchangeRatePropertiesTest`, `SpreadLookupTest`, `RateCollectionServiceTest`,
`ExchangeControllerIT`) — included below.

**Organization**: Tasks are grouped by user story. This is a backend-only, single-project change
(`backend/`) with no `contracts/` impact — no frontend paths apply.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Can run in parallel (different files, no dependencies)
- **[Story]**: Which user story this task belongs to (US1, US2, US3)
- Paths are relative to repo root, rooted at `backend/`

## Path Conventions

Single backend module per plan.md: `backend/src/main/java/com/exchangerate/manager/`,
`backend/src/test/java/com/exchangerate/manager/`, `backend/src/main/resources/application.yml`.

---

## Phase 1: Setup

No new project scaffolding required — existing Maven module, dependencies
(`spring-boot-starter-validation`) already on the classpath per plan.md Technical Context.

- [X] T001 Confirm `spring-boot-starter-validation` is present in `backend/pom.xml` (no change
      expected; abort and add it first if missing, since `ExchangeRateProperties` validation
      depends on it)

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: The validated config class and its `application.yml` binding that every user story's
tests and logic depend on.

**⚠️ CRITICAL**: No user story work can begin until this phase is complete.

- [ ] T002 Create `ExchangeRateProperties` in
      `backend/src/main/java/com/exchangerate/manager/config/ExchangeRateProperties.java`:
      `@ConfigurationProperties(prefix = "exchange-rates")`, `@Validated`, immutable
      (constructor-bound record or final-field/no-setters class) with fields `baseCurrency`
      (`String`, `@NotBlank` + `@Pattern(regexp = "^[A-Z]{3}$")`), `defaultSpreadPercent`
      (`BigDecimal`, `@NotNull` + `@DecimalMin("0.0")` + exclusive-`100` upper bound),
      `spreads` (`Map<String, BigDecimal>`, `@NotEmpty`, every key matching `^[A-Z]{3}$`,
      every value `>= 0` and `< 100`); add a class-level `@AssertTrue`-annotated method
      (e.g. `isBaseCurrencySpreadZero()`) asserting `spreads.get(baseCurrency)` exists and
      `compareTo(BigDecimal.ZERO) == 0`. Javadoc must state this is "the provider's (Fixer.io's)
      business base currency, used for spread policy" per research.md's naming-separation decision.
- [ ] T003 Register `ExchangeRateProperties` for binding (`@EnableConfigurationProperties` on a
      `@Configuration` class, e.g. alongside the existing classes in
      `backend/src/main/java/com/exchangerate/manager/config/`, or
      `@ConfigurationPropertiesScan` if that's already the project's pattern — check
      `backend/src/main/java/com/exchangerate/manager/ExchangeRateManagerApplication.java` first)
- [ ] T004 Add the `exchange-rates:` block to `backend/src/main/resources/application.yml`:
      `baseCurrency: EUR`, `defaultSpreadPercent: 2.75`, and `spreads:` containing `EUR: 0.00`,
      `JPY: 3.25`, `HKD: 3.25`, `KRW: 3.25`, `MYR: 4.50`, `INR: 4.50`, `MXN: 4.50`, `RUB: 6.00`,
      `CNY: 6.00`, `ZAR: 6.00` (USD intentionally absent — falls through to the 2.75% default per
      FR-002)
- [ ] T005 [P] Create `ExchangeRatePropertiesTest` in
      `backend/src/test/java/com/exchangerate/manager/config/ExchangeRatePropertiesTest.java`:
      plain unit test (no Spring context) using a `jakarta.validation.Validator` directly —
      cover valid config passes, blank/lowercase/4-letter `baseCurrency` fails pattern, negative
      or `>= 100` `defaultSpreadPercent`/`spreads` values fail range, empty `spreads` fails
      `@NotEmpty`, and a `spreads` map missing the base currency or mapping it to a non-zero value
      fails the `@AssertTrue` invariant

**Checkpoint**: Config class validated and bound — user story implementation can now begin.

---

## Phase 3: User Story 1 - EUR is the only zero-spread currency (Priority: P1) 🎯 MVP

**Goal**: `SpreadLookup` reports 0% for EUR and USD's real configured spread for USD, sourced from
`ExchangeRateProperties` instead of the hardcoded map.

**Independent Test**: Request an EUR/PLN quote (0% EUR spread) and a USD/PLN quote (USD's real
spread, not 0%) per spec.md's Independent Test for this story.

### Implementation for User Story 1

- [ ] T006 [US1] Refactor `SpreadLookup` in
      `backend/src/main/java/com/exchangerate/manager/service/SpreadLookup.java`: remove the
      hardcoded `SPREADS` map and `DEFAULT_KEY` constant; inject `ExchangeRateProperties` via
      constructor (`@RequiredArgsConstructor`); `spreadFor(String currencyCode)` looks up
      `currencyCode` in `properties.spreads()`, falling back to `properties.defaultSpreadPercent()`
      when absent — no re-uppercasing/trimming inside this method per research.md's
      "SpreadLookup input handling" decision (trust the already-validated/uppercased input)
- [ ] T007 [P] [US1] Create `SpreadLookupTest` in
      `backend/src/test/java/com/exchangerate/manager/service/SpreadLookupTest.java`: plain unit
      test constructing `SpreadLookup` with a hand-built `ExchangeRateProperties` instance —
      assert EUR resolves to 0%, USD (absent from `spreads`) resolves to the 2.75% default (not
      0%), and a currency present in `spreads` resolves to its configured value
- [ ] T008 [US1] Update `ExchangeControllerIT` in
      `backend/src/test/java/com/exchangerate/manager/controller/ExchangeControllerIT.java`: fix
      any existing assertions that assumed USD carried a 0% spread; add/adjust cases confirming an
      EUR-involving quote applies 0% for EUR and a USD-involving (non-EUR) quote applies USD's real
      configured spread

**Checkpoint**: User Story 1 fully functional and independently testable — the core defect (USD
wrongly treated as spread-free) is fixed.

---

## Phase 4: User Story 2 - Correct spread group applied to every currency (Priority: P2)

**Goal**: Every Appendix B group (3.25%, 4.50%, 6.00%) and the 2.75% default resolve correctly
through the new config-backed `SpreadLookup`.

**Independent Test**: For one currency per group plus one unlisted currency, confirm the applied
spread matches 3.25% / 4.50% / 6.00% / 2.75% respectively, per spec.md's Independent Test for this
story.

**Note**: The config data for this story (all group entries) was already added in T004, and
`SpreadLookup`'s group-lookup logic is already generic map lookup from T006 — this phase is
primarily the test coverage proving the full table, not new production code.

### Implementation for User Story 2

- [ ] T009 [P] [US2] Extend `SpreadLookupTest`
      (`backend/src/test/java/com/exchangerate/manager/service/SpreadLookupTest.java`) with cases
      for one representative currency from each Appendix B group — JPY or HKD or KRW → 3.25%, MYR
      or INR or MXN → 4.50%, RUB or CNY or ZAR → 6.00% — and a currency in none of the groups and
      not EUR (e.g. GBP) → 2.75% default
- [ ] T010 [US2] Add a `SpreadLookupTest` case proving config-only extensibility (SC-004): build a
      second `ExchangeRateProperties` instance with an added/changed spread entry and confirm
      `SpreadLookup` reflects it with zero changes to `SpreadLookup` itself

**Checkpoint**: User Stories 1 AND 2 both work independently — full spread table matches Appendix B.

---

## Phase 5: User Story 3 - Ingestion rejects inconsistent provider payloads (Priority: P3)

**Goal**: `RateCollectionService.collect()` validates `response.getBase()` against
`ExchangeRateProperties.baseCurrency()` (and the EUR==1 sanity check) before any upsert, throwing
`FixerApiException` on failure.

**Independent Test**: Simulate a non-EUR/missing base currency response and confirm
`FixerApiException` is thrown with zero `upsert` calls; confirm a normal EUR-based payload still
ingests successfully, per spec.md's Independent Test for this story.

### Implementation for User Story 3

- [ ] T011 [US3] Update `RateCollectionService` in
      `backend/src/main/java/com/exchangerate/manager/service/RateCollectionService.java`: inject
      `ExchangeRateProperties`; at the top of `collect()`, immediately after
      `fixerClient.getLatestRates()` returns and before reading `rates`, compare
      `response.getBase()` against `properties.baseCurrency()` (null/blank/case-sensitive
      mismatch → throw `FixerApiException` with a clear message per FR-010); then check
      `rates.get(properties.baseCurrency())` is present and `compareTo(BigDecimal.ONE) == 0`,
      throwing `FixerApiException` on failure (research.md's optional EUR==1 sanity check) — both
      checks must run before the per-currency upsert loop starts
- [ ] T012 [US3] Update `RateCollectionServiceTest` in
      `backend/src/test/java/com/exchangerate/manager/service/RateCollectionServiceTest.java`: add
      cases for a response with `base = "USD"` (rejected), `base = null` (rejected), and a
      response with `base = "EUR"` but a `rates` map where `EUR` is missing or not numerically `1`
      (rejected) — each asserting `FixerApiException` is thrown and
      `verify(exchangeRateRepository, never()).upsert(any(), any(), any())`; keep the existing
      EUR-based-success case passing to confirm normal ingestion is unaffected

**Checkpoint**: All three user stories independently functional — ingestion now guards the
correctness the first two stories established.

---

## Phase 6: Polish & Cross-Cutting Concerns

- [ ] T013 [P] Update any developer-facing documentation referencing the old hardcoded USD-as-base
      spread behavior (e.g. `README.md`, module-level Javadoc in `SpreadLookup` or
      `RateCollectionService`) to describe the new `exchange-rates.*` config-driven behavior
- [ ] T014 Run `cd backend && ./mvnw verify` — full build, all existing and new tests pass,
      confirming FR-012/FR-013 (unchanged API contracts and historical storage) and SC-006
      (existing regression tests unaffected)
- [ ] T015 Execute quickstart.md steps 2–4 manually (fail-fast config break/restore, live
      EUR/PLN vs USD/PLN curl comparison, ingestion-rejection confirmation) to validate end-to-end
      behavior beyond the automated test suite

---

## Dependencies & Execution Order

### Phase Dependencies

- **Setup (Phase 1)**: No dependencies — can start immediately.
- **Foundational (Phase 2)**: Depends on Setup — BLOCKS all user stories (T006, T011 both need
  `ExchangeRateProperties` to exist and be bound).
- **User Stories (Phase 3+)**: All depend on Foundational phase completion.
  - US1 (T006–T008) has no dependency on US2/US3.
  - US2 (T009–T010) extends `SpreadLookupTest` created in US1 (T007) — sequential with US1 on that
    file, but adds no new production code, so it can start as soon as T006/T007 land.
  - US3 (T011–T012) touches a different file (`RateCollectionService`/its test) — independent of
    US1/US2 production code, but shares the Foundational config class.
- **Polish (Phase 6)**: Depends on all three user stories being complete.

### Within Each User Story

- Production code before/alongside its test in the same task pair (T006 before T007 can extend it;
  T011 before T012).
- Story complete before moving to next priority (recommended sequential order given shared files;
  see Parallel Opportunities for the actual file-level parallelism available).

### Parallel Opportunities

- T005 (`ExchangeRatePropertiesTest`) can be written in parallel with T003/T004 once T002 exists.
- T007 (`SpreadLookupTest`, new file) and T011 (`RateCollectionService`, different file) can run in
  parallel once Phase 2 is done — US1's test file and US3's production file don't conflict.
- T009/T010 must come after T007 creates `SpreadLookupTest` (same file, sequential edits).
- T012 must come after T011 (test asserts behavior T011 implements).
- T013 (docs) can run in parallel with T014/T015 (verification) once all story phases are done.

---

## Parallel Example: Foundational + User Story 1

```bash
# After T002 (ExchangeRateProperties class exists):
Task: "Register ExchangeRateProperties for binding"                # T003
Task: "Add exchange-rates block to application.yml"                # T004
Task: "Create ExchangeRatePropertiesTest"                            # T005

# After Phase 2 checkpoint:
Task: "Refactor SpreadLookup to read from ExchangeRateProperties"   # T006
Task: "Update RateCollectionService with base-currency validation"  # T011 (different file, parallel-safe)
```

---

## Implementation Strategy

### MVP First (User Story 1 Only)

1. Complete Phase 1: Setup (T001).
2. Complete Phase 2: Foundational (T002–T005) — CRITICAL, blocks all stories.
3. Complete Phase 3: User Story 1 (T006–T008).
4. **STOP and VALIDATE**: EUR/PLN → 0% EUR spread, USD/PLN → real USD spread. This alone fixes the
   spec's headline defect.

### Incremental Delivery

1. Setup + Foundational → config ready.
2. US1 → the core mispricing defect is fixed → validate → this is the MVP.
3. US2 → full Appendix B table proven correct → validate.
4. US3 → ingestion now guards against a bad provider payload undoing US1/US2's correctness →
   validate.
5. Polish → docs, full `mvnw verify`, quickstart end-to-end pass.

---

## Notes

- No `contracts/` impact and no frontend paths — this is a backend-only internal correction
  (plan.md, FR-012).
- No database schema change — `ExchangeRateProperties` is in-memory config only (data-model.md).
- Commit after each task or logical group; stop at any checkpoint to validate a story
  independently.
