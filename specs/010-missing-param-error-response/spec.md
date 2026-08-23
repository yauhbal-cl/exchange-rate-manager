# Feature Specification: Consistent Error Response for Missing Required Query Parameters

**Feature Branch**: `010-missing-param-error-response`

**Created**: 2026-08-23

**Status**: Draft

**Input**: User description: "missing required query param (e.g. /exchange without from) returns 400 with empty body instead of ProblemDetail shape — GlobalExceptionHandler has no handler for MissingServletRequestParameterException"

## User Scenarios & Testing *(mandatory)*

### User Story 1 - API consumer omits a required query parameter (Priority: P1)

A client (frontend app, script, or third-party integrator) calls an API endpoint that requires a
query parameter (e.g. `/exchange` without `from`) and forgets to include it. Today the response is
a bare 400 with no body, giving the caller no indication of what went wrong. The caller needs a
response body that tells them which parameter is missing, in the same structured format every other
4xx error already uses.

**Why this priority**: This is the only gap in an otherwise fully consistent error contract. Any
client relying on the error body shape (as they already must for unknown currency, invalid date
range, etc.) breaks unpredictably for this one case. Fixing it closes the last inconsistency in the
API's error contract.

**Independent Test**: Call any endpoint that declares a required query parameter (e.g.
`GET /exchange` without `from`) and verify the response is `400 Bad Request` with a JSON body
matching the same `ProblemDetail` shape (`type`, `title`, `status`, `detail`, `instance`) used by
other validation errors, with `detail` naming the missing parameter.

**Acceptance Scenarios**:

1. **Given** an endpoint that requires query parameter `from`, **When** a client calls it without
   `from`, **Then** the response is `400 Bad Request` with a `ProblemDetail` JSON body whose
   `detail` field states that `from` is required.
2. **Given** an endpoint that requires multiple query parameters, **When** a client omits one of
   them, **Then** the response body identifies the specific missing parameter, not a generic
   message.
3. **Given** an endpoint that requires query parameter `from`, **When** a client supplies `from`
   with an empty value (e.g. `?from=`), **Then** the response behaves the same as today for
   empty/blank values (unchanged — this feature only addresses the case where the parameter is
   absent entirely).

---

### User Story 2 - Existing error consumers see no format change for other error types (Priority: P2)

Frontend code and any existing tests that already parse `ProblemDetail` bodies for other error
cases (unknown currency, invalid date range, etc.) must keep working unmodified — this fix only
adds coverage for a previously-unhandled exception type.

**Why this priority**: Regression protection. Lower priority than the primary fix because it's a
constraint on the fix, not new functionality, but must hold for the fix to be safe to ship.

**Independent Test**: Re-run existing error-response tests for currently-handled exception types
(unknown currency, same currency, invalid date range, trend range too large, rate not found, AI
insight unavailable, constraint violation) and confirm their response shape and status codes are
unchanged.

**Acceptance Scenarios**:

1. **Given** the existing set of handled error cases, **When** each is triggered the same way as
   before this change, **Then** each still returns the same status code and the same
   `ProblemDetail` body shape as before.

---

### Edge Cases

- Multiple required query parameters missing at once: response identifies at least the first
  missing parameter (framework-driven; only one `MissingServletRequestParameterException` is
  raised per request in the underlying stack).
- Required parameter present but wrong type (e.g. a non-numeric value for a numeric parameter):
  out of scope for this feature — covered separately by type-conversion error handling if/when it
  exists.
- Missing parameter on an endpoint that has no required query parameters: not applicable, no
  change in behavior.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: System MUST return `400 Bad Request` when a required query parameter is missing from
  a request (this is existing behavior and MUST be preserved).
- **FR-002**: System MUST return a `ProblemDetail`-shaped JSON body (same shape as every other
  handled 4xx error in this API) when a required query parameter is missing, instead of an empty
  body.
- **FR-003**: The response body's `detail` field MUST name the specific missing query parameter.
- **FR-004**: The fix MUST NOT change the response shape, status code, or body content for any
  error case already handled (unknown currency, same currency, invalid date range, trend range too
  large, rate not found, AI insight unavailable, constraint violation, Fixer API failure,
  collection-in-progress).
- **FR-005**: The fix MUST apply uniformly to every endpoint with required query parameters, not
  just `/exchange`.

### Key Entities

- **Error response body**: The existing `ProblemDetail` structure (`type`, `title`, `status`,
  `detail`, `instance`) already used across the API's other 4xx/5xx responses; this feature extends
  its use to one more trigger condition, it does not introduce a new shape.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: 100% of requests missing a required query parameter receive a `400` response with a
  non-empty, structured JSON error body (currently 0%).
- **SC-002**: The missing-parameter error body identifies the specific parameter name in every
  case tested.
- **SC-003**: All previously-existing error-handling test cases continue to pass unchanged after
  the fix.

## Assumptions

- "Empty body" in the reported bug means the response currently has no JSON payload at all (the
  default behavior when an exception has no matching `@ExceptionHandler` and falls through to
  Spring Boot's default error handling without a body), not a body with an unexpected shape.
- The required query parameters affected are those declared as mandatory in
  `contracts/openapi.yaml` and enforced by Spring MVC's own binding (raising
  `MissingServletRequestParameterException`), not custom validation logic.
- No change to `contracts/openapi.yaml` is implied by this fix — the contract already presumably
  documents these parameters as required; this is a server-side error-handling gap, not a contract
  gap.
- Fixing this is a backend-only change; no frontend changes are needed since a frontend correctly
  calling the API already supplies required parameters.
