---

description: "Task list for feature implementation"
---

# Tasks: Database Migration Tool, Schema, and Persistence Model

**Input**: Design documents from `/specs/002-db-schema-migration/`

**Prerequisites**: plan.md, spec.md, research.md, data-model.md, quickstart.md

**Tests**: Test tasks included for User Story 3 only, matching the repository test files
(`ExchangeRateRepositoryTest.java`, `CurrencyUsageRepositoryTest.java`) explicitly named in
plan.md's project structure and quickstart.md step 5. User Stories 1 and 2 are validated via the
manual quickstart procedures (schema/constraint behavior is exercised directly against Postgres,
not through JUnit).

**Organization**: Tasks are grouped by user story (US1, US2, US3 from spec.md) to enable
independent implementation and testing of each story.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Can run in parallel (different files, no dependencies)
- **[Story]**: Which user story this task belongs to (US1, US2, US3)
- Include exact file paths in descriptions

## Path Conventions

Web application layout per plan.md: `backend/src/main/java/com/exchangerate/manager/`,
`backend/src/main/resources/`, `backend/src/test/java/com/exchangerate/manager/`. Frontend is
untouched by this feature.

---

## Phase 1: Setup (Shared Infrastructure)

**Purpose**: Add the dependencies this feature needs before any migration or entity code is written

- [X] T001 Add Flyway dependencies (`flyway-core`, `flyway-database-postgresql`) to `backend/pom.xml`
- [X] T002 [P] Add ShedLock dependencies (`shedlock-spring`, `shedlock-provider-jdbc-template`) to `backend/pom.xml`
- [X] T003 [P] Verify `docker-compose.yml` Postgres service (db name, user, password, port) matches what `backend/src/main/resources/application.yml` will use as the datasource

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: Core Flyway/datasource wiring that MUST be complete before any migration file is added

**⚠️ CRITICAL**: No user story work can begin until this phase is complete

- [X] T004 Configure datasource and Flyway properties in `backend/src/main/resources/application.yml` (JDBC URL/credentials matching docker-compose, `spring.flyway.enabled=true`, `spring.flyway.validate-on-migrate=true`, `spring.flyway.baseline-on-migrate=false`)
- [X] T005 Create the default Flyway migration directory `backend/src/main/resources/db/migration/` so `V1`/`V2`/`V3` scripts have a home

**Checkpoint**: Foundation ready — user story implementation can now begin

---

## Phase 3: User Story 1 - Reliable, repeatable database setup (Priority: P1) 🎯 MVP

**Goal**: Schema (tables for exchange rates, currency usage, and scheduler locking) is created
automatically and idempotently at application startup, with no manual SQL.

**Independent Test**: Start the application against an empty database; verify all three tables
exist afterward with zero manual steps, and that a second startup reapplies nothing.

### Implementation for User Story 1

- [X] T006 [US1] Create `backend/src/main/resources/db/migration/V1__create_exchange_rates.sql`: `exchange_rates` table with `id BIGSERIAL PRIMARY KEY`, `currency_code CHAR(3) NOT NULL`, `rate_to_usd NUMERIC(19,6) NOT NULL`, `rate_date DATE NOT NULL`, `created_at TIMESTAMPTZ NOT NULL DEFAULT now()`, and a unique composite index on `(currency_code, rate_date)`
- [X] T007 [US1] Create `backend/src/main/resources/db/migration/V2__create_currency_usage.sql`: `currency_usage` table with `id BIGSERIAL PRIMARY KEY`, `currency_code CHAR(3) NOT NULL UNIQUE`, `query_count BIGINT NOT NULL DEFAULT 0`, `last_queried_at TIMESTAMPTZ NOT NULL DEFAULT now()`
- [X] T008 [US1] Create `backend/src/main/resources/db/migration/V3__create_shedlock.sql`: `shedlock` table per the JDBC-template provider's required shape (`name VARCHAR(64) PRIMARY KEY`, `lock_until TIMESTAMP(3)`, `locked_at TIMESTAMP(3)`, `locked_by VARCHAR(255)`)
- [X] T009 [US1] Validate fresh-schema creation and idempotent restart per `quickstart.md` steps 1-2 (`docker compose down -v && docker compose up -d`, `./mvnw spring-boot:run` twice, confirm `V1`-`V3` apply once and the second run reapplies nothing)

**Checkpoint**: At this point, User Story 1 is fully functional and independently testable

---

## Phase 4: User Story 2 - Data integrity guarantees at the storage layer (Priority: P1)

**Goal**: The schema itself rejects duplicate (currency, date) rate rows, non-positive rates, and
malformed currency codes, and preserves exact `NUMERIC(19,6)` precision.

**Independent Test**: Insert a duplicate (currency, date) row and confirm rejection; insert a rate
value with 6 decimal places and confirm it round-trips exactly.

### Implementation for User Story 2

- [X] T010 [US2] Add `CHECK (currency_code ~ '^[A-Z]{3}$')` and `CHECK (rate_to_usd > 0)` constraints to `backend/src/main/resources/db/migration/V1__create_exchange_rates.sql` (depends on T006)
- [X] T011 [US2] Add `CHECK (currency_code ~ '^[A-Z]{3}$')` and `CHECK (query_count >= 0)` constraints to `backend/src/main/resources/db/migration/V2__create_currency_usage.sql` (depends on T007)
- [X] T012 [US2] Validate duplicate-rate rejection and NUMERIC(19,6) precision round-trip per `quickstart.md` steps 3-4
- [X] T013 [US2] Validate non-positive rate and malformed currency-code rejection per `quickstart.md` step 6

**Checkpoint**: User Stories 1 AND 2 both work independently — schema exists and enforces
correctness

---

## Phase 5: User Story 3 - Structured access to persisted data from application code (Priority: P2)

**Goal**: Typed JPA entities and Spring Data repositories for `ExchangeRate` and `CurrencyUsage`,
queryable by their natural keys.

**Independent Test**: From a repository test, save an `ExchangeRate` and a `CurrencyUsage` record,
then read each back by its natural key.

### Tests for User Story 3

- [X] T018 [P] [US3] Write `ExchangeRateRepositoryTest` in `backend/src/test/java/com/exchangerate/manager/repository/ExchangeRateRepositoryTest.java` (`@SpringBootTest` against the docker-compose Postgres): save an `ExchangeRate`, then retrieve it via `findByCurrencyCodeAndRateDate`
- [X] T019 [P] [US3] Write `CurrencyUsageRepositoryTest` in `backend/src/test/java/com/exchangerate/manager/repository/CurrencyUsageRepositoryTest.java` (`@SpringBootTest` against the docker-compose Postgres): save a `CurrencyUsage`, then retrieve it via `findByCurrencyCode`

### Implementation for User Story 3

- [X] T014 [P] [US3] Create `ExchangeRate` JPA entity in `backend/src/main/java/com/exchangerate/manager/entity/ExchangeRate.java` (`id`, `currencyCode`, `rateToUsd` as `BigDecimal`, `rateDate` as `LocalDate`, `createdAt` as `Instant`; add `@Pattern`/`@Positive` Bean Validation as defense-in-depth per research.md)
- [X] T015 [P] [US3] Create `CurrencyUsage` JPA entity in `backend/src/main/java/com/exchangerate/manager/entity/CurrencyUsage.java` (`id`, `currencyCode`, `queryCount`, `lastQueriedAt`)
- [X] T016 [US3] Create `ExchangeRateRepository` in `backend/src/main/java/com/exchangerate/manager/repository/ExchangeRateRepository.java` extending `JpaRepository<ExchangeRate, Long>` with `Optional<ExchangeRate> findByCurrencyCodeAndRateDate(String currencyCode, LocalDate rateDate)` (depends on T014)
- [X] T017 [US3] Create `CurrencyUsageRepository` in `backend/src/main/java/com/exchangerate/manager/repository/CurrencyUsageRepository.java` extending `JpaRepository<CurrencyUsage, Long>` with `Optional<CurrencyUsage> findByCurrencyCode(String currencyCode)` (depends on T015)
- [X] T020 [US3] Run `./mvnw test -Dtest=ExchangeRateRepositoryTest,CurrencyUsageRepositoryTest` and confirm both pass, per `quickstart.md` step 5 (depends on T016, T017, T018, T019)

**Checkpoint**: All three user stories are independently functional

---

## Phase 6: Polish & Cross-Cutting Concerns

- [X] T021 [P] Run `./mvnw verify` from `backend/` to confirm build, all migrations, and all tests pass together
- [X] T022 Review `backend/src/main/resources/application.yml` for correct Flyway location settings and remove any leftover placeholder config

---

## Dependencies & Execution Order

### Phase Dependencies

- **Setup (Phase 1)**: No dependencies — start immediately
- **Foundational (Phase 2)**: Depends on Setup — BLOCKS all user stories
- **User Story 1 (Phase 3)**: Depends on Foundational only
- **User Story 2 (Phase 4)**: Depends on Foundational; T010/T011 edit the same files as T006/T007 (US1), so run after US1's migration files exist
- **User Story 3 (Phase 5)**: Depends on Foundational only; independent of US1/US2 migration content beyond the tables existing at test-run time (T020 needs T006-T011 applied against the running database)
- **Polish (Phase 6)**: Depends on all desired user stories being complete

### User Story Dependencies

- **User Story 1 (P1)**: No dependencies on other stories
- **User Story 2 (P1)**: Builds on the same migration files as US1 (T006, T007) — sequenced after, not parallel, but conceptually independent (constraints vs. table shape)
- **User Story 3 (P2)**: No code dependency on US1/US2 output; its repository tests need a real, migrated database to run against

### Within Each User Story

- Migration files before validation tasks
- Entities before repositories before repository tests' execution
- Story complete before moving to next priority

### Parallel Opportunities

- T002, T003 in Setup can run in parallel with T001
- T014, T015 (entities) can run in parallel — different files
- T018, T019 (tests) can run in parallel — different files
- T021 (full verify) can run in parallel with T022 (config review)

---

## Parallel Example: User Story 3

```bash
# Launch entity creation together:
Task: "Create ExchangeRate JPA entity in backend/src/main/java/com/exchangerate/manager/entity/ExchangeRate.java"
Task: "Create CurrencyUsage JPA entity in backend/src/main/java/com/exchangerate/manager/entity/CurrencyUsage.java"

# Launch repository tests together (after repositories exist):
Task: "Write ExchangeRateRepositoryTest in backend/src/test/java/com/exchangerate/manager/repository/ExchangeRateRepositoryTest.java"
Task: "Write CurrencyUsageRepositoryTest in backend/src/test/java/com/exchangerate/manager/repository/CurrencyUsageRepositoryTest.java"
```

---

## Implementation Strategy

### MVP First (User Story 1 Only)

1. Complete Phase 1: Setup
2. Complete Phase 2: Foundational
3. Complete Phase 3: User Story 1 — schema exists, applies automatically, idempotent restarts
4. **STOP and VALIDATE**: run quickstart.md steps 1-2
5. Deploy/demo if ready

### Incremental Delivery

1. Setup + Foundational → Flyway/datasource wired
2. User Story 1 → tables exist, migrations idempotent → validate (MVP!)
3. User Story 2 → constraints enforced → validate
4. User Story 3 → typed entities/repositories → validate
5. Polish → full `./mvnw verify` green

---

## Notes

- [P] tasks = different files, no dependencies
- [Story] label maps task to specific user story for traceability
- T010/T011 are not [P] relative to T006/T007 — same files, must be sequential
- Verify tests fail before implementing (T018/T019 before T016/T017 pass)
- Commit after each task or logical group
- Stop at any checkpoint to validate story independently
</content>
