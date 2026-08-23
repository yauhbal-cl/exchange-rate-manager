# Feature Specification: Exchange Rate Calculator View

**Feature Branch**: `012-exchange-rate-calculator`

**Created**: 2026-08-23

**Status**: Draft

**Input**: User description: "Frontend View: Exchange Rate Calculator — currency pair + optional date form, validated inputs, loading state, API error display, signals for state"

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Look up current spread-adjusted rate (Priority: P1)

User picks source currency, target currency, submits form, sees spread-adjusted rate and rate date.

**Why this priority**: Core value of the view — without this, nothing else matters.

**Independent Test**: Select two distinct valid currencies, submit, verify a rate and rate date render.

**Acceptance Scenarios**:

1. **Given** the form is empty, **When** user selects "USD" as source and "EUR" as target and submits, **Then** the view shows a spread-adjusted rate, the rate date, and usage counts for both currencies.
2. **Given** a lookup already succeeded, **When** user changes the target currency and resubmits, **Then** the previous result is replaced by the new one (not appended).

---

### User Story 2 - Look up rate for a specific historical date (Priority: P2)

User optionally supplies a date to get the rate as of that date instead of the most recent shared date.

**Why this priority**: Extends the core lookup with a common secondary need (historical rate checks); not required for a minimally useful view.

**Independent Test**: Submit currency pair with a past date that has stored data, verify returned rate date matches the requested date.

**Acceptance Scenarios**:

1. **Given** a valid currency pair, **When** user enters a past date known to have data and submits, **Then** the displayed rate date equals the requested date.
2. **Given** a valid currency pair, **When** user leaves the date field empty and submits, **Then** the system uses the most recent date both currencies share (no date sent).

---

### User Story 3 - Understand invalid input or lookup failure (Priority: P3)

User gets clear, inline feedback when input is invalid or the backend rejects/can't fulfill the request, without the app crashing or showing a blank state.

**Why this priority**: Necessary for a trustworthy tool but the view already delivers value via P1/P2 before this is fully polished.

**Independent Test**: Submit with source == target, submit with an unknown currency code, and submit for a date with no shared data; verify each shows a distinct, human-readable message and the form remains usable.

**Acceptance Scenarios**:

1. **Given** the form, **When** user selects the same currency for source and target, **Then** the view shows a validation message and does not submit.
2. **Given** the form, **When** user submits a currency pair/date with no stored rate, **Then** the view shows an inline "no data" message sourced from the API error, not a generic crash or blank screen.
3. **Given** a submitted lookup, **When** the backend is unreachable or times out, **Then** the view shows a clear "can't reach the service" message and lets the user retry.

---

### Edge Cases

- User submits with one or both currency fields empty → blocked client-side with a validation message before any request fires.
- User selects a date in the future → blocked client-side (no rate can exist yet) with a validation message.
- User double-submits (rapid repeated clicks) while a lookup is in flight → submit control is disabled/ignored until the in-flight request settles; no duplicate concurrent requests.
- API returns a validation problem (400) after client-side checks already passed (e.g., unknown currency code) → the API's problem-detail message is surfaced verbatim, not swallowed.
- User navigates away or changes inputs while a lookup is loading → the stale response, if it later arrives, is discarded and does not overwrite results for the current inputs.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: View MUST provide dropdown selects for source currency and target currency (populated from a fixed, frontend-maintained currency list), and an optional date input.
- **FR-002**: View MUST validate, before submission, that both currencies are selected and are not identical to each other.
- **FR-003**: View MUST validate, before submission, that an entered date is not in the future.
- **FR-004**: View MUST block submission and show an inline message when client-side validation fails, without contacting the backend.
- **FR-005**: View MUST show a distinct loading state while a lookup request is in flight, and disable re-submission during that time.
- **FR-006**: View MUST display, on a successful lookup, the spread-adjusted rate, the rate date used, and the usage counts for both currencies.
- **FR-007**: View MUST omit the date from the request when the user leaves it blank, letting the backend pick the most recent shared date.
- **FR-008**: View MUST display an inline, human-readable error message when the backend returns an error (validation problem, no-data-found, or unreachable/timeout), distinguishing at least "invalid request," "no data for that request," and "service unreachable" cases.
- **FR-009**: View MUST let the user retry a lookup after an error without reloading the page or losing their entered inputs.
- **FR-010**: View MUST discard responses that no longer correspond to the current form inputs (e.g., a slow response arriving after inputs changed and a newer request was sent).
- **FR-011**: View MUST hold all view state (form inputs, loading flag, result, error) in reactive state that a template can read directly, with no manual subscription management left dangling after the component is destroyed.

### Key Entities

- **Rate Lookup Request**: source currency code, target currency code, optional as-of date — the user-entered criteria for a lookup.
- **Rate Lookup Result**: source currency, target currency, spread-adjusted rate, the rate date actually used, usage count for each currency — what's shown on success.
- **Lookup Error**: a category (invalid input / no data / unreachable) plus a human-readable message — what's shown on failure.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: A user can obtain a spread-adjusted rate for a valid currency pair in under 10 seconds from landing on the view.
- **SC-002**: 100% of invalid submissions (empty field, identical currencies, future date) are caught client-side with zero backend requests made.
- **SC-003**: 100% of backend error responses (400, 404, unreachable) result in a visible, distinct inline message rather than a blank view or unhandled crash.
- **SC-004**: Rapid repeated submission of the same form never produces more than one concurrent in-flight request.

## Assumptions

- Currency codes are selected as uppercase 3-letter ISO codes, matching the existing `/exchange` API's `^[A-Z]{3}$` pattern.
- The currency dropdown options come from a fixed, frontend-maintained list (no backend "list currencies" endpoint exists in the current contract; adding one is out of scope for this view).
- This view only performs a single-point-in-time lookup (the existing `/exchange` endpoint); historical trend charting is out of scope (covered by the separate analytics view).
- "Retry" means re-submitting the same, still-visible form inputs — no separate retry history or backoff behavior is required.
- Usage counts returned by the API are shown as read-only informational figures, not something the user can act on from this view.
