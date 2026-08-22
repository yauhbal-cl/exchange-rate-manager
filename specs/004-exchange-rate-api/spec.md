# Feature Specification: Exchange Rate API

**Feature Branch**: `004-exchange-rate-api`

**Created**: 2026-08-22

**Status**: Draft

**Input**: User description: "Exchange Rate API implementation"

## Clarifications

### Session 2026-08-22

- Q: What HTTP status codes should distinguish the three rejected-lookup cases (unknown currency, same currency on both sides, no rate data for the requested date)? → A: 400 for unknown currency, 400 for same-currency-both-sides, 404 for no rate data for the requested date.
- Q: In the usage analytics response, should a currency that has never been queried be omitted entirely, or included with a zero count? → A: Included, with a zero count, for every currency the system has ever stored a rate record for.

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Look up a spread-adjusted exchange rate (Priority: P1)

As a consumer of the platform (frontend or external client), I want to request the exchange rate
between two currencies, so that I get a spread-adjusted rate calculated from locally stored data
without depending on the external rate provider for every query.

**Why this priority**: This is the core value of the feature — every other capability (analytics,
frontend calculator, AI insight) depends on this lookup existing and returning a correct,
trustworthy number.

**Independent Test**: With rate data already collected for at least one date, request a rate for
a supported currency pair with no date specified; verify the response returns a spread-adjusted
rate computed from the most recent available rates, with the correct spread applied.

**Acceptance Scenarios**:

1. **Given** rate data exists for today for both currencies in the pair, **When** a client
   requests the exchange rate for that pair with no date specified, **Then** the system returns
   the spread-adjusted rate computed from today's stored rates, applying the higher of the two
   currencies' spreads.
2. **Given** a client requests the exchange rate for a specific past date for which rate data
   exists for both currencies, **When** the request is processed, **Then** the system returns the
   spread-adjusted rate computed from that date's stored rates.
3. **Given** a client requests a currency pair using the platform's base currency on one side,
   **When** the rate is calculated, **Then** the base currency's spread of 0% is used for that
   side of the calculation.
4. **Given** a successful rate lookup, **When** the response is returned, **Then** it includes the
   source currency, target currency, the calculated rate, the date the underlying rates apply to,
   and the current usage counts for both currencies.

---

### User Story 2 - Safe handling of missing or invalid lookups (Priority: P1)

As a consumer of the platform, I want a clear, well-structured error when I request a rate that
cannot be calculated, so that I can distinguish a bad request from a temporary problem and react
accordingly (e.g., show a helpful message to an end user).

**Why this priority**: Without well-defined error behavior, an unanswerable query would either
crash, hang, or silently return a wrong/fabricated number — any of which erodes trust in the data
and breaks the frontend calculator's ability to show a clear message.

**Independent Test**: Request a rate for an unsupported currency code, and separately for a date
with no stored rate data, and verify each produces a distinct, well-structured error response
rather than a generic failure or a fabricated rate.

**Acceptance Scenarios**:

1. **Given** a requested currency code is not one the system has ever collected rates for,
   **When** a rate lookup is attempted, **Then** the system returns an error response identifying
   the unknown currency, and no usage counters are incremented.
2. **Given** a requested date has no stored rate data for one or both currencies in the pair,
   **When** a rate lookup is attempted, **Then** the system returns an error response indicating
   no rate data is available for that date, and no usage counters are incremented.
3. **Given** a request omits a required currency or supplies the same currency for both source and
   target, **When** the request is validated, **Then** the system returns an error response
   describing the invalid input before attempting any calculation.

---

### User Story 3 - Track usage per currency (Priority: P2)

As an operator, I want every successful rate lookup to count toward per-currency usage
statistics, so that I can later understand which currencies are queried most and support the
analytics dashboard.

**Why this priority**: Depends on User Story 1 existing (there must be a lookup to count), and
usage data is only consumed downstream by analytics — valuable, but the platform is functional for
a single lookup without it.

**Independent Test**: Perform several successful rate lookups across different currency pairs and
verify each currency involved has its usage count incremented by exactly one per lookup it
appeared in, and that concurrent simultaneous lookups don't lose increments.

**Acceptance Scenarios**:

1. **Given** a successful rate lookup between two currencies, **When** the lookup completes,
   **Then** the usage counter for each of the two currencies is incremented by exactly one.
2. **Given** many simultaneous successful lookups involving the same currency, **When** all of
   them complete, **Then** the final usage count for that currency equals exactly the number of
   lookups it was involved in — no increments are lost to concurrent updates.
3. **Given** a lookup fails validation or finds no data (User Story 2), **When** the failure
   occurs, **Then** no usage counter is incremented as a result of that attempt.

---

### User Story 4 - View usage analytics (Priority: P2)

As an operator, I want to retrieve aggregated usage statistics per currency, so that I can see
which currencies are queried most often and support the usage analytics dashboard in the
frontend.

**Why this priority**: Directly depends on User Story 3's counters existing; delivers operator-
facing value once lookups are happening, but isn't required for the core lookup capability to
work.

**Independent Test**: After performing a known set of rate lookups across several currencies,
call the analytics endpoint and verify the returned counts and last-queried dates match the
lookups actually performed.

**Acceptance Scenarios**:

1. **Given** a mix of rate lookups have been performed across multiple currencies, **When** the
   analytics endpoint is called, **Then** the response includes, for each currency that has been
   queried at least once, its total query count and the most recent date it was queried.
2. **Given** a currency has never been queried but the system has stored a rate record for it,
   **When** the analytics endpoint is called, **Then** that currency appears in the response with
   a zero query count.
3. **Given** the analytics endpoint is called with no lookups having occurred yet, **When** the
   response is returned, **Then** it is a well-formed empty result rather than an error.

---

### Edge Cases

- What happens when a client requests a pair where source and target currency are the same? The
  system MUST reject this as an invalid request rather than returning a rate of 1 computed
  through the spread formula.
- What happens when a requested date is in the future? The system MUST treat it the same as any
  other date with no stored rate data — return the "no data available" error, since no future
  rate has been collected.
- What happens when only one side of a requested pair has data for the requested date (e.g., a
  currency added more recently than the other)? The lookup MUST fail with the "no data for date"
  error rather than substituting a different date's rate for the missing side.
- What happens when two currencies fall into the same spread tier? The higher-spread rule still
  applies without ambiguity, since both sides yield the same spread percentage.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: The system MUST expose an endpoint that accepts a source currency code, a target
  currency code, and an optional date, and returns the spread-adjusted exchange rate for that
  pair.
- **FR-002**: The system MUST compute the returned rate exclusively from exchange rate data
  already stored locally — it MUST NOT call the external rate provider as part of answering a
  lookup request.
- **FR-003**: When no date is supplied, the system MUST use the most recent date for which both
  currencies in the pair have stored rate data.
- **FR-004**: When a specific date is supplied and rate data for that date does not exist for
  both currencies in the pair (or when no date is supplied and no common date exists at all), the
  system MUST return a 404 HTTP error response rather than substituting data from a different
  date.
- **FR-005**: The system MUST calculate the returned rate as:
  `(toCurrencyRateToUSD / fromCurrencyRateToUSD) × ((100 − MAX(toSpread, fromSpread)) / 100)`,
  using each currency's configured spread percentage.
- **FR-006**: The system MUST look up each currency's spread percentage from a keyed reference
  lookup (by currency code, with a default tier for currencies not explicitly listed), not from
  code branches that require a code change to add or adjust an entry.
- **FR-007**: The system MUST reject a request where the source and target currency are
  identical, or where either currency code is unknown to the system, with a 400 HTTP error
  response identifying the specific problem, and without performing any calculation or counter
  increment.
- **FR-008**: Every successful rate lookup MUST increment a usage counter for the source currency
  and a usage counter for the target currency, each by exactly one.
- **FR-009**: Usage counter increments MUST remain correct under concurrent simultaneous lookups
  — no increments may be lost or double-counted.
- **FR-010**: A failed or rejected lookup (invalid input, unknown currency, or no data for the
  requested date) MUST NOT increment any usage counter.
- **FR-011**: A successful lookup response MUST include the source currency, target currency, the
  calculated rate, the date the underlying rate data applies to, and the current usage counts for
  both currencies involved.
- **FR-012**: The system MUST expose an endpoint that returns, for every currency the system has
  ever stored a rate record for, its total usage count (zero if never queried) and the date it
  was most recently queried (absent/null if never queried).
- **FR-013**: Error responses for invalid requests (unknown currency, missing data for date,
  malformed input) MUST use a consistent, structured error shape rather than ad hoc bodies.
- **FR-014**: Rate and monetary values MUST be represented and returned with exact decimal
  precision throughout the calculation and response — no floating-point rounding error introduced
  by the calculation path.

### Key Entities

- **Exchange Rate Record**: A previously collected currency rate relative to the base currency,
  for a specific date (produced by data collection, out of scope for this feature — consumed
  here as read-only input).
- **Spread Reference**: A keyed lookup from currency code (or a default tier) to a spread
  percentage, used to determine the adjustment applied in a rate calculation.
- **Currency Usage Counter**: A per-currency running total of how many successful rate lookups
  that currency has been involved in, plus the date of its most recent occurrence.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: A client can retrieve a spread-adjusted rate for any supported currency pair with
  locally stored data, with no request to the external rate provider occurring as part of that
  retrieval.
- **SC-002**: 100% of requests for an unknown currency or a date with no stored data receive a
  clear, structured error identifying the problem, with zero fabricated or interpolated rates
  ever returned.
- **SC-003**: Under concurrent load against the same currency, the recorded usage count exactly
  matches the number of successful lookups performed — zero lost or duplicated increments.
- **SC-004**: An operator can retrieve up-to-date usage statistics per currency that accurately
  reflect all successful lookups performed, with no separate manual reconciliation step needed.
- **SC-005**: Rate values in every response match the documented spread formula to full decimal
  precision, verifiable by independently recomputing the formula from the same stored input data.

## Assumptions

- Rate data collection (fetching from the external provider and persisting daily rates) is
  covered by a separate feature and is a precondition here, not part of this feature's scope.
- The set of valid currency codes is exactly the set for which the system has ever stored a rate
  record; there is no separate manually maintained allow-list.
- The spread reference table (Appendix B tiers: 0% base, 3.25%, 4.50%, 6.00%, 2.75% default) is
  seeded as static reference data and is not user-editable through this feature.
- "Most recent available rates" (when no date is supplied) means the latest date on which both
  currencies in the requested pair have data, which may differ from the latest date any single
  currency has data if collection has gaps.
- The analytics endpoint's response shape only needs to support currency-level total counts and
  last-queried dates, per Appendix A's suggested shape and TASK.md §4.3 — further breakdowns
  (e.g., per-day time series) are out of scope unless a future feature requests them.
