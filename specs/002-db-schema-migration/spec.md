# Feature Specification: Database Migration Tool, Schema, and Persistence Model

**Feature Branch**: `002-db-schema-migration`

**Created**: 2026-08-22

**Status**: Draft

**Input**: User description: "Adding migration tool, DB schema, JPA entities"

## Clarifications

### Session 2026-08-22

- Q: What numeric precision (total digits and decimal places) must exchange rate values support? → A: NUMERIC(19,6) — up to ~13-digit integer part, 6 decimal places
- Q: CurrencyUsage counter: track lifetime cumulative query count, or a count that resets/windows over time (e.g. daily/monthly)? → A: Lifetime cumulative — single count column that only ever increments, no reset/window schema.

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Reliable, repeatable database setup (Priority: P1)

As an operator standing up the system (locally, in CI, or in a new environment), I need the
database schema created and versioned automatically when the application starts, so that every
environment ends up with an identical, known-good schema without anyone running manual SQL.

**Why this priority**: Nothing else in the system (rate ingestion, API, analytics) can function
without a schema that exists and is consistent across environments. This is the foundation every
later feature depends on.

**Independent Test**: Start the application against an empty database and verify all expected
tables, constraints, and indexes exist afterward, with no manual intervention.

**Acceptance Scenarios**:

1. **Given** a fresh, empty database, **When** the application starts, **Then** all required
   tables (exchange rates, currency usage, and the scheduler coordination table) are created
   automatically.
2. **Given** a database that already has the schema applied at the current version, **When** the
   application restarts, **Then** no changes are reapplied and startup succeeds without error.
3. **Given** a database schema at an older known version, **When** the application starts,
   **Then** only the outstanding schema changes are applied, bringing it up to the current
   version, without data loss.

---

### User Story 2 - Data integrity guarantees at the storage layer (Priority: P1)

As the system ingesting and serving exchange rate data, I need the schema itself to enforce the
core correctness rules (no duplicate rate per currency/day, valid numeric precision for rates), so
that data-integrity bugs are caught by the database even if application logic has a defect.

**Why this priority**: These constraints are the last line of defense for the two costliest
failure modes described elsewhere in the project (duplicate rate rows, precision loss in monetary
values). Equal priority to Story 1 because a schema without these constraints is not fit for
purpose even if it "exists."

**Independent Test**: Attempt to insert two exchange-rate rows for the same currency and date, and
verify the database rejects the second one; attempt to insert a rate value that would require
lossy floating-point storage and verify precision is preserved.

**Acceptance Scenarios**:

1. **Given** an existing exchange rate row for a given currency and date, **When** a second row is
   inserted for that same currency and date, **Then** the database rejects the insert (or the
   application-level upsert updates the existing row instead of creating a duplicate).
2. **Given** a rate value with several decimal places of precision, **When** it is stored and then
   read back, **Then** the value returned matches exactly, with no rounding or precision loss.

---

### User Story 3 - Structured access to persisted data from application code (Priority: P2)

As a backend developer implementing the ingestion job, rate API, and analytics endpoint, I need
the persisted entities (exchange rate records, per-currency usage counters) available as typed
objects I can query and update through the persistence layer, so that I don't hand-write SQL for
routine reads/writes.

**Why this priority**: This unlocks the ingestion, API, and analytics work described elsewhere in
the project plan, but is a developer-facing enabler rather than an end-user-facing capability, so
it ranks below the two data-integrity foundations.

**Independent Test**: From an application-layer test, save an exchange rate record and a usage
counter record through the persistence layer, then read each back by its natural lookup key
(currency + date; currency) and confirm the retrieved values match what was saved.

**Acceptance Scenarios**:

1. **Given** no existing record, **When** a new exchange rate is saved for a currency and date,
   **Then** it can subsequently be retrieved by that currency and date.
2. **Given** no existing usage counter for a currency, **When** a query against that currency
   occurs, **Then** a usage counter record for it becomes retrievable with a positive count.

---

### Edge Cases

- What happens when the application starts against a database whose schema was modified outside
  of the migration tool (schema drift)? Startup MUST fail loudly rather than silently applying
  migrations on top of an inconsistent baseline.
- How does the system handle two application instances starting concurrently against the same
  empty database? Only one instance's migration run may apply the schema; the other MUST wait or
  no-op, never partially apply competing changes.
- How does the system handle an exchange rate value of zero or negative? Schema/entity validation
  MUST reject non-positive rate values.
- What happens when a currency code that doesn't conform to the expected format (e.g., wrong
  length) is persisted? The schema MUST constrain the column so malformed codes cannot be stored.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: The system MUST version-control all database schema changes as an ordered,
  repeatable set of migrations, applied automatically at application startup.
- **FR-002**: The system MUST track which migrations have already been applied to a given
  database, so that startup is idempotent and only outstanding migrations run.
- **FR-003**: The system MUST fail application startup with a clear error if the database's
  migration history does not match what the migration tool expects (e.g., a checksum mismatch
  from manual schema edits), rather than proceeding against an inconsistent schema.
- **FR-004**: The schema MUST persist exchange rate records with, at minimum: currency code, the
  rate relative to the system's base currency, and the date the rate is reported for.
- **FR-005**: The schema MUST enforce uniqueness on the combination of currency code and rate
  date for exchange rate records, at the database level.
- **FR-006**: The schema MUST store rate values using an exact-precision numeric type sized for
  19 total digits with 6 decimal places (NUMERIC(19,6) or equivalent); no floating-point column
  types (e.g., `float`, `double`) are permitted for rate values.
- **FR-007**: The schema MUST persist per-currency usage counters tracking, at minimum: currency
  code, a lifetime cumulative query count (never reset or windowed), and the timestamp of the most
  recent query.
- **FR-008**: The schema MUST enforce uniqueness on currency code for usage counter records, at
  the database level, so counters can be atomically upserted per currency.
- **FR-009**: The schema MUST include the coordination table required for cross-instance
  scheduler locking, versioned through the same migration mechanism as the rest of the schema.
- **FR-010**: The system MUST expose the exchange rate and usage counter tables to application
  code as typed persistence entities, queryable by their natural lookup keys (currency + date for
  rates; currency for usage counters).
- **FR-011**: The schema MUST reject exchange rate values that are zero or negative.
- **FR-012**: The schema MUST constrain currency codes to a fixed, valid format (3-letter ISO-style
  code).
- **FR-013**: Migrations MUST be reversible in the sense of being forward-only and safe to
  re-run across environments — no migration may depend on manual, undocumented steps.

### Key Entities

- **ExchangeRate**: A single currency's rate relative to the base currency on a specific date.
  Key attributes: currency code, rate value, rate date. Uniquely identified by (currency code,
  rate date).
- **CurrencyUsage**: A lifetime cumulative counter (never reset or windowed) of how often a
  currency has been queried through the rate API. Key attributes: currency code, query count,
  last-queried timestamp. Uniquely identified by currency code.
- **Scheduler Lock**: Coordination record used to ensure only one running instance performs a
  given scheduled task at a time. Not directly exposed to application business logic.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: A new environment goes from an empty database to a fully-schema'd, ready-to-use
  database automatically on first application startup, with zero manual SQL steps.
- **SC-002**: 100% of attempts to store a duplicate (currency, date) exchange rate pair are
  prevented from creating a second row.
- **SC-003**: 100% of stored rate values, up to 19 total digits with 6 decimal places, round-trip
  (write then read) with no loss of decimal precision.
- **SC-004**: Restarting the application against an already-migrated database completes with zero
  reapplied schema changes and no startup errors, every time.
- **SC-005**: Running the application as multiple concurrent instances against the same empty
  database results in exactly one successful schema initialization, with no partial or conflicting
  schema state.

## Assumptions

- A single relational database (PostgreSQL, per the project's technology stack) is the only
  persistence target; no multi-database-vendor support is required.
- The base currency for exchange rate values is fixed and known ahead of time (assumed USD, per
  existing project context); this feature does not need to make the base currency configurable.
- This feature covers schema and persistence-entity setup only; the data-ingestion job, rate API,
  and analytics endpoint that consume these entities are separate, already-planned work and out of
  scope here.
- The scheduler coordination table's exact shape is dictated by the chosen locking library's own
  required schema, not a custom design.
- No historical data migration/backfill is needed — this is a greenfield schema for a system with
  no pre-existing data.
