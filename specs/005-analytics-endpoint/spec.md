# Feature Specification: Analytics Endpoint

**Feature Branch**: `005-analytics-endpoint`

**Created**: 2026-08-22

**Status**: Draft

**Input**: User description: "Analytics Endpoint implementation"

## Clarifications

### Session 2026-08-22

- Q: Scope of this feature, given the platform already has a working per-currency usage-count
  endpoint. → A: Both — add a new historical exchange-rate trend endpoint (feeding trend charts
  and the AI insight prompt context), and extend the existing usage analytics with ranking/
  filtering capabilities.

## User Scenarios & Testing *(mandatory)*

### User Story 1 - View historical rate trend for a currency pair (Priority: P1)

As a consumer of the platform (frontend dashboard or the AI insight feature), I want to retrieve
the sequence of stored exchange rates for a currency pair over a date range, so that I can render
a trend chart or generate data-grounded commentary about how the rate has moved over time.

**Why this priority**: This is the core new capability the feature adds — trend charts and the
AI insight feature both depend on a historical series existing; without it, "analytics" is limited
to point-in-time usage counts already available today.

**Independent Test**: With rate data stored across several consecutive dates for a currency pair,
request the trend for that pair over a date range spanning those dates, and verify the response
returns one spread-adjusted rate value per date in the range that has data, in chronological
order.

**Acceptance Scenarios**:

1. **Given** rate data exists for a currency pair across a range of dates, **When** a client
   requests the historical trend for that pair with a start and end date, **Then** the response
   returns one entry per date within the range that has stored data for both currencies, each
   entry showing the date and the spread-adjusted rate for that date, ordered oldest to newest.
2. **Given** a requested date range includes dates with no stored data for one or both
   currencies, **When** the trend is retrieved, **Then** those dates are omitted from the result
   rather than causing the whole request to fail or containing a fabricated/interpolated value.
3. **Given** a client requests a trend with no date range specified, **When** the request is
   processed, **Then** the system returns the trend over a sensible default recent window (see
   Assumptions).
4. **Given** a trend request names an unknown currency code, **When** the request is validated,
   **Then** the system returns a 400 error identifying the unknown currency, with no data
   returned.

---

### User Story 2 - Rank currencies by usage (Priority: P2)

As an operator, I want to retrieve the most (or least) queried currencies ranked by usage count,
so that I can quickly identify which currencies matter most to users without scanning the full
per-currency list myself.

**Why this priority**: Builds directly on the existing usage-count data; valuable for operators
monitoring the platform, but the platform is fully usable without it since the full unranked list
already exists.

**Independent Test**: With usage counts recorded across several currencies with different totals,
request the top-N ranked currencies and verify they come back sorted by usage count descending,
limited to N entries.

**Acceptance Scenarios**:

1. **Given** several currencies have distinct usage counts, **When** a client requests the
   ranked usage list with a limit, **Then** the response returns at most that many currencies,
   sorted by usage count from highest to lowest.
2. **Given** two or more currencies share the same usage count, **When** they are ranked,
   **Then** the tie is broken consistently (see Assumptions) rather than producing
   non-deterministic ordering across repeated calls.
3. **Given** no limit is specified, **When** the ranked list is requested, **Then** the system
   returns all currencies the platform has usage data for, ranked, using the same default
   ordering.

---

### User Story 3 - Filter usage analytics by recency (Priority: P3)

As an operator, I want to filter usage analytics to currencies queried within a given time
window, so that I can distinguish currencies with recent, active demand from ones only queried
long ago (or never).

**Why this priority**: A refinement on top of User Stories 1 and 2's data; useful for spotting
trends in demand but not required for the baseline analytics views to be useful.

**Independent Test**: With currencies last queried at different points in time, request usage
analytics filtered to "queried within the last N days" and verify only currencies whose most
recent query falls inside that window are returned.

**Acceptance Scenarios**:

1. **Given** currencies have varying last-queried dates, **When** a client requests usage
   analytics with a recency filter, **Then** only currencies whose most recent query date falls
   within the requested window are included.
2. **Given** a currency has never been queried, **When** a recency filter is applied, **Then**
   that currency is excluded from the filtered result (it has no last-queried date to fall inside
   any window).

---

### Edge Cases

- What happens when the historical trend's start date is after its end date? The system MUST
  reject this as an invalid request rather than returning an empty or reordered result.
- What happens when a trend date range is requested entirely outside any period with stored
  data? The system MUST return a well-formed empty result, not an error.
- What happens when the ranked usage limit requested is larger than the number of currencies with
  data? The system MUST return all available currencies rather than erroring or padding the
  response.
- What happens when a ranking or recency-filter request is combined with an invalid parameter
  (e.g., a negative limit or window)? The system MUST reject it with a validation error before
  attempting to query data.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: The system MUST expose an endpoint that returns the historical spread-adjusted
  exchange rate for a given currency pair across a date range, accepting a source currency, a
  target currency, and optional start/end dates.
- **FR-002**: The historical trend endpoint MUST compute every returned value from locally stored
  rate data only, using the same spread-adjustment formula as the single-date rate lookup — it
  MUST NOT call the external rate provider.
- **FR-003**: The historical trend endpoint MUST omit, rather than error on or interpolate, any
  date in the requested range for which one or both currencies lack stored rate data.
- **FR-004**: The historical trend endpoint MUST reject requests with an unknown currency code, or
  a start date after the end date, with a 400 error identifying the specific problem, before
  querying data.
- **FR-005**: The historical trend endpoint MUST return results ordered chronologically from
  oldest to newest date.
- **FR-006**: Retrieving the historical trend MUST NOT increment any currency's usage counter —
  usage counters track rate lookups (existing single-date endpoint), not analytics/reporting
  reads.
- **FR-007**: The system MUST expose a way to retrieve currencies ranked by usage count in
  descending order, accepting an optional limit on the number of currencies returned.
- **FR-008**: When usage counts tie between currencies, the ranked usage endpoint MUST break the
  tie by currency code in ascending alphabetical order, so repeated calls return a stable order.
- **FR-009**: The system MUST expose a way to filter usage analytics to only currencies whose most
  recent query date falls within a caller-specified number of days from today; currencies never
  queried MUST be excluded from a recency-filtered result.
- **FR-010**: Ranking and recency-filter parameters (limit, window) MUST be validated as positive
  values, rejecting non-positive or malformed values with a 400 error before querying data.
- **FR-011**: All new analytics responses MUST use the platform's existing structured error shape
  for rejected requests, consistent with other endpoints.
- **FR-012**: Rate values in historical trend results MUST be represented with exact decimal
  precision, consistent with the single-date rate lookup.

### Key Entities

- **Rate Trend Point**: A single date paired with the spread-adjusted rate computed for a
  currency pair on that date, one of a chronological series returned by the historical trend
  endpoint.
- **Ranked Usage Entry**: A currency's usage count and last-queried date, as part of an ordered
  list sorted by usage count.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: A client can retrieve a complete, chronologically ordered historical rate series for
  any supported currency pair and date range with locally stored data, with no request to the
  external rate provider occurring as part of that retrieval.
- **SC-002**: 100% of historical trend requests for an unknown currency or an invalid date range
  receive a clear, structured error, with zero fabricated or interpolated rate values ever
  returned for a date lacking data.
- **SC-003**: An operator can identify the top N most-queried currencies, and separately the
  currencies queried within a chosen recent window, without manually sorting or filtering the
  full usage list themselves.
- **SC-004**: Ranked and filtered analytics results are reproducible — repeated calls with the
  same underlying data and parameters return identical ordering and contents every time.
- **SC-005**: Requesting historical trend or ranked/filtered analytics never changes any currency's
  usage counters, verifiable by comparing counters before and after any number of analytics reads.

## Assumptions

- When no date range is supplied to the historical trend endpoint, the system defaults to the
  most recent 30 days of stored data.
- The historical trend endpoint reuses the existing spread reference and calculation logic from
  the single-date exchange rate lookup feature; it introduces no new spread rules.
- "Ranked usage" and "recency-filtered usage" extend the existing usage analytics data (currency,
  total count, last-queried date) already collected by the exchange rate lookup feature; no new
  usage data is introduced.
- The frontend dashboard and the AI insight feature are the primary consumers of the historical
  trend endpoint, per the project's existing AI insight design (historical rows serialized into
  the AI prompt context) — this feature only exposes the data; generating the AI commentary
  itself is a separate feature.
- Contract and generated-client changes (OpenAPI spec, backend interfaces, frontend client) follow
  the project's existing "edit the contract first" workflow and are part of implementation, not
  specified further here.
