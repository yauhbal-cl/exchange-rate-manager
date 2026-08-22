# Feature Specification: Fixer.io Data Collection

**Feature Branch**: `003-fixer-data-collection`

**Created**: 2026-08-22

**Status**: Draft

**Input**: User description: "Fixer.io Data Collection"

## Clarifications

### Session 2026-08-22

- Q: On first deployment (empty rate history), should collection backfill multiple past days of rates, or only start accumulating data from the first run forward? → A: Forward-only — collection fetches only the current day's rates each run; history accumulates naturally day by day, no bulk historical backfill.
- Q: Should each collection run leave a persisted, queryable record of its outcome, or is application-log visibility enough? → A: Logs only — no dedicated run-history table; failures/successes are recorded via standard application logging.

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Automatic daily rate refresh (Priority: P1)

As an operator of the system, I want exchange rates fetched automatically from the external rate
provider (Fixer.io) once per day, so that the data available to API consumers is always
reasonably current without any manual intervention.

**Why this priority**: This is the core value of the feature — without it, the system has no
rate data at all and every downstream feature (rate queries, spread calculation, AI insight) has
nothing to operate on.

**Independent Test**: Deploy the system with valid provider credentials and an empty rate
history; wait for the scheduled collection time (or trigger it); verify new exchange rate rows
exist for the current day for all supported currencies immediately afterward.

**Acceptance Scenarios**:

1. **Given** the scheduled collection time has arrived, **When** the system runs the collection
   job, **Then** it fetches the latest rates for all supported currencies from the provider and
   persists one rate record per currency for the date the provider reports.
2. **Given** the collection job already ran successfully today, **When** the scheduled time
   arrives again (e.g., due to a restart or re-trigger), **Then** the job runs again without
   creating duplicate rate records for the same currency and date — existing records are
   updated in place if the provider now reports a different value.
3. **Given** the system is deployed as multiple running instances, **When** the scheduled
   collection time arrives, **Then** only one instance performs the actual call to the external
   provider for that run.

---

### User Story 2 - Resilience to provider failures (Priority: P2)

As an operator, I want a failed or partial call to the external rate provider to be handled
safely, so that a temporary outage or bad response doesn't corrupt existing data or silently
leave the system in an inconsistent state.

**Why this priority**: External dependencies fail. Without safe handling, a bad response could
overwrite good historical data with garbage, or leave no record that a run was attempted at all.

**Independent Test**: Simulate the provider being unreachable or returning an error/malformed
response during a scheduled run, then verify existing rate data is untouched and the failure is
observable (e.g., in logs), with the system automatically retrying at the next scheduled run.

**Acceptance Scenarios**:

1. **Given** the external provider is unreachable when the collection job runs, **When** the job
   attempts the call, **Then** no partial or corrupt rate data is persisted, previously stored
   rates remain unchanged, and the failure is recorded so it is visible to operators.
2. **Given** the external provider returns a response missing rates for some currencies,
   **When** the job processes the response, **Then** rates for the currencies that were present
   are persisted normally and the missing currencies are skipped without failing the entire run.
3. **Given** a collection run failed, **When** the next scheduled run occurs, **Then** the system
   attempts collection again normally (no manual reset required).

---

### User Story 3 - Manual on-demand refresh (Priority: P3)

As an operator, I want to trigger a rate collection run on demand (outside the daily schedule),
so that I can recover quickly from a missed or failed scheduled run, or refresh data during
testing, without waiting for the next scheduled time.

**Why this priority**: Useful operational safety valve, but the system is fully functional day to
day on the schedule alone; this is a convenience/recovery capability layered on top of P1.

**Independent Test**: Invoke the manual collection trigger and verify rates are fetched and
persisted immediately, following the same upsert and multi-instance-safety rules as the
scheduled run, and that doing so does not affect per-currency usage counters.

**Acceptance Scenarios**:

1. **Given** an operator triggers collection manually, **When** the run completes successfully,
   **Then** the same upsert-on-(currency, date) behavior applies as for the scheduled run.
2. **Given** an operator triggers collection manually, **When** the run executes, **Then**
   per-currency usage counters (which track query API activity) are not incremented as a result.

---

### Edge Cases

- What happens when the provider reports a rate date the system has never seen before (e.g., a
  backfill or an out-of-order date)? The record MUST be stored under that reported date, not
  today's date.
- What happens when the daily quota/rate limit on the external provider's API is exhausted before
  the scheduled run? The run MUST fail safely (per User Story 2) rather than partially bill
  against quota it doesn't have.
- What happens when a manual trigger is invoked while a scheduled run is already in progress?
  Only one collection run MUST execute at a time; the second invocation MUST be rejected or
  queued, never run concurrently against the same data.
- What happens when the provider changes its reported base currency or currency set between
  runs? Currencies newly reported are collected going forward; currencies no longer reported are
  left with their last known historical data (not deleted).

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: The system MUST automatically collect exchange rates from the external rate
  provider once per day, at a fixed scheduled time (00:05 GMT), without requiring manual action.
- **FR-002**: The system MUST persist, for each collected currency, the rate value and the date
  the provider reports the rate for (not the date/time of the fetch).
- **FR-003**: The system MUST upsert collected rates keyed on the combination of currency code
  and rate date — a repeated or retried collection for the same currency and date MUST update
  the existing record rather than create a duplicate.
- **FR-004**: When the system runs as multiple instances, exactly one instance MUST perform the
  external provider call for a given scheduled run; the others MUST NOT make redundant calls.
- **FR-005**: The system MUST support collecting rates for the full set of currencies the
  provider supports for the configured base currency, not a hard-coded subset requiring a code
  change to extend.
- **FR-006**: If the external provider is unreachable or returns an error, the collection run
  MUST fail without modifying previously persisted rate data, and the failure MUST be recorded
  in a way visible to operators (e.g., logged).
- **FR-007**: If the external provider's response is missing data for some currencies, the
  system MUST persist the currencies that were present and MUST NOT fail the entire run because
  of the missing ones.
- **FR-008**: The system SHOULD provide a way for an operator to trigger a collection run on
  demand, independent of the daily schedule (optional extension, per TASK.md §4.4 — see User
  Story 3).
- **FR-009**: If a manual trigger (FR-008) is implemented, a manually triggered collection run
  MUST NOT increment per-currency usage counters — those counters reflect query API activity
  only.
- **FR-010**: The system MUST prevent two collection runs (scheduled, manual, or a combination)
  from executing concurrently against the same data.
- **FR-011**: A failed collection run MUST NOT block subsequent scheduled or manual runs from
  attempting collection again.

### Key Entities

- **Exchange Rate Record**: A single currency's rate relative to the configured base currency,
  for a specific date. Identified by (currency code, rate date). Produced/updated by the
  collection process; consumed by rate query and downstream features.
- **Collection Run**: A single execution of the data-collection process (scheduled or manual),
  covering all supported currencies for one point in time. Its outcome (success, partial success,
  failure) is observable via application logs; it is not persisted as its own database record.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: Under normal provider availability, 100% of supported currencies have a rate
  record for the current date within the scheduled collection window, every day.
- **SC-002**: Zero duplicate exchange rate records are ever created for the same currency and
  date, regardless of how many times collection runs for that date.
- **SC-003**: When the system runs with multiple instances, the external provider receives
  exactly one collection call per scheduled run, not one per instance.
- **SC-004**: A single provider outage during a scheduled run results in zero data corruption or
  loss of previously collected rates, and collection resumes automatically at the next
  opportunity with no manual recovery steps.
- **SC-005** *(conditional on User Story 3 / FR-008 being implemented)*: An operator can recover
  from a missed or failed scheduled run and have current rates available within minutes of
  triggering a manual refresh, without any queries against the rate provider being wasted. If
  User Story 3 is skipped, this criterion does not apply — recovery falls back to waiting for the
  next scheduled run (US1).

## Assumptions

- The external rate provider is Fixer.io, accessed via an API key already provisioned for the
  environment; obtaining/rotating that key is outside this feature's scope.
- The base currency for collected rates is fixed and configured ahead of time (per the existing
  project baseline: USD), consistent with the persistence model already defined for exchange
  rate storage.
- "Daily schedule" is once per day at a fixed time: 00:05 GMT.
- Collection is forward-only: each run fetches only the current day's rates. History accumulates
  naturally day by day; there is no bulk historical backfill on first deployment.
- The provider's free/standard tier rate-and-quota limits are the operating constraint driving
  the single-call-per-run requirement (FR-004); this feature does not assume a paid tier with
  higher limits.
- Currencies are whatever set the provider returns for the configured base currency at collection
  time; no separate manually maintained currency allow-list is required for this feature.
