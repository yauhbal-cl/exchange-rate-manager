# Research: Consistent Error Response for Missing Required Query Parameters

No open `NEEDS CLARIFICATION` markers from the spec or Technical Context — stack and pattern are
already fixed by `CLAUDE.md` and the existing `GlobalExceptionHandler`. Research below confirms the
approach rather than choosing between alternatives.

## Decision: Handle `MissingServletRequestParameterException` in `GlobalExceptionHandler`

- **Decision**: Add `@ExceptionHandler(MissingServletRequestParameterException.class)` to the
  existing `GlobalExceptionHandler`, returning
  `ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, e.getMessage())` — the same pattern
  already used for `UnknownCurrencyException`, `SameCurrencyException`,
  `InvalidDateRangeException`, `TrendRangeTooLargeException`, and `ConstraintViolationException`.
- **Rationale**: Spring MVC throws `org.springframework.web.bind.MissingServletRequestParameterException`
  when a controller method parameter annotated `@RequestParam(required = true)` (the default) is
  absent from the request. Its `getMessage()` already produces a human-readable string naming the
  missing parameter and its type (e.g. `"Required parameter 'from' is not present"`), which
  satisfies FR-003 (name the specific missing parameter) with zero extra formatting work — no need
  to hand-build a message from `e.getParameterName()`.
  With no matching `@ExceptionHandler`, Spring Boot's default `BasicErrorController`/
  `DefaultHandlerExceptionResolver` path returns `400` but produces a body only when
  `server.error.include-message`/`include-binding-errors` are enabled and content negotiation
  resolves to the default error view — in this app's MVC setup it currently resolves to an empty
  body, matching the reported bug exactly. Adding the handler bypasses that default path
  entirely, consistent with how every other handled exception here already bypasses it.
- **Alternatives considered**:
  - *Global `@ExceptionHandler(Exception.class)` catch-all*: rejected — would also swallow
    unrelated unhandled exceptions into a generic `400`/`500`, masking bugs instead of fixing the
    one specific known gap (FR-004 requires no change to other error cases; a catch-all risks
    exactly that).
  - *Custom validation in each controller method (`if (from == null) throw ...`)*: rejected —
    duplicates logic across every endpoint with required params, and contradicts the existing
    "centralized exception handling, no local try/catch" convention (`CLAUDE.md`, constitution
    Development & Quality Standards).
  - *Make the query parameters `required = false` and validate manually*: rejected — larger
    surface change to the generated `ExchangeApi` interface/contract, not needed to fix a response
    -body gap, and would touch `contracts/openapi.yaml` unnecessarily.

## Decision: No `contracts/openapi.yaml` change needed

- **Decision**: Leave the contract file untouched.
- **Rationale**: Inspected `contracts/openapi.yaml` — the affected endpoints already declare a
  `400` response referencing `#/components/schemas/ProblemDetail`. The bug is that server
  behavior doesn't yet match the contract for this one trigger condition; fixing the handler makes
  behavior conform to the existing contract rather than requiring a contract update.
- **Alternatives considered**: Editing the contract to add explicit language about missing
  parameters — rejected, `ProblemDetail` is already a generic shape and doesn't enumerate specific
  triggering conditions for any other 400 case either; adding it here alone would be inconsistent.

## Decision: Test via existing `@WebMvcTest` + `MockMvc` slice convention

- **Decision**: New `GlobalExceptionHandlerTest` (or an addition to an existing controller slice
  test) using `@WebMvcTest` on a controller with a required `@RequestParam`, asserting `status 400`
  and `jsonPath` on the `ProblemDetail` fields, mirroring `ExchangeControllerTest`.
- **Rationale**: Matches the project's established test pattern (see
  `backend/src/test/java/com/exchangerate/manager/controller/ExchangeControllerTest.java`); no
  datasource/Testcontainers needed since `@WebMvcTest` doesn't load the persistence layer,
  satisfying constitution Principle X trivially (no DB touched at all).
- **Alternatives considered**: Full `@SpringBootTest` integration test — rejected as unnecessary
  overhead for a pure MVC-layer concern; the existing slice-test convention already covers this
  class of behavior for other exception types.
