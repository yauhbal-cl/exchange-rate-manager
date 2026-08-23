# Implementation Plan: Consistent Error Response for Missing Required Query Parameters

**Branch**: `010-missing-param-error-response` | **Date**: 2026-08-23 | **Spec**: [spec.md](./spec.md)

**Input**: Feature specification from `/specs/010-missing-param-error-response/spec.md`

## Summary

`GlobalExceptionHandler` has no `@ExceptionHandler` for
`MissingServletRequestParameterException`, so a request missing a required query parameter (e.g.
`GET /exchange` without `from`) falls through to Spring Boot's default error handling: `400` status
but an empty/non-`ProblemDetail` body. Every other 4xx case in this API already returns a
`ProblemDetail` body, and `contracts/openapi.yaml` already documents `400 → ProblemDetail` for the
affected endpoints — so this is a pure gap-fill in the existing centralized exception handler, no
contract change, no new exception type, no controller change.

## Technical Context

**Language/Version**: Java 21

**Primary Dependencies**: Spring Boot 4.1.1 (Spring MVC, `spring-boot-starter-web`), Spring
`ProblemDetail`

**Storage**: N/A (no persistence involved in this fix)

**Testing**: JUnit 5 + `spring-boot-starter-test`, `@WebMvcTest` + `MockMvc` slice tests (existing
convention, see `ExchangeControllerTest`)

**Target Platform**: Linux server (Spring Boot REST API)

**Project Type**: Web application (backend + frontend monorepo, per `CLAUDE.md`) — this feature is
backend-only

**Performance Goals**: N/A — no throughput/latency-sensitive path touched

**Constraints**: Must not alter response shape/status of any already-handled exception type
(FR-004)

**Scale/Scope**: One new `@ExceptionHandler` method in one existing class
(`GlobalExceptionHandler`); no new files besides tests

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

- **Development & Quality Standards — "REST error responses MUST use a consistent problem-detail
  shape for 4xx conditions"**: This feature directly closes the one gap where that standard is
  currently violated. PASS (fix aligns with, not against, the constitution).
- **Development & Quality Standards — "Exception handling MUST be centralized...Controllers and
  services MUST NOT catch exceptions locally"**: Fix adds the handler to the existing
  `@RestControllerAdvice` (`GlobalExceptionHandler`), no local try/catch introduced. PASS.
- **Principle VI (Layered Separation of Concerns)**: No controller/service/repository logic
  touched — change is confined to the cross-cutting exception-handling layer. PASS.
- No other principle (monetary precision, rate provenance, idempotent collection, scheduler
  safety, atomic counters, data-driven config, AI grounding, env-configurable frontend, test
  isolation) is implicated by this change.
- Test Isolation (Principle X): new test is a `@WebMvcTest` slice with no datasource — no real or
  shared DB touched. PASS.

No violations. No entries needed in Complexity Tracking.

## Project Structure

### Documentation (this feature)

```text
specs/010-missing-param-error-response/
├── plan.md              # This file
├── research.md          # Phase 0 output
├── data-model.md         # Phase 1 output (N/A — no data entities)
├── quickstart.md        # Phase 1 output
└── tasks.md             # Phase 2 output (/speckit-tasks — not created here)
```

No `contracts/` subdirectory for this feature: `contracts/openapi.yaml` already declares
`400 → ProblemDetail` for the affected endpoints (verified in Phase 0); this fix makes server
behavior match the existing contract, so the contract itself is unchanged.

### Source Code (repository root)

```text
backend/
├── src/main/java/com/exchangerate/manager/
│   └── exception/
│       └── GlobalExceptionHandler.java   # add one @ExceptionHandler method
└── src/test/java/com/exchangerate/manager/
    └── exception/
        └── GlobalExceptionHandlerTest.java  # new — @WebMvcTest slice covering the new handler
                                              # (and a regression check per User Story 2)
```

**Structure Decision**: Single-project change inside the existing `backend/` module (Option 2 —
Web application layout already established by the repo; `frontend/` is untouched by this
feature). No new packages; the fix and its test both live alongside the existing
`GlobalExceptionHandler` and its sibling exception classes in
`backend/src/main/java/com/exchangerate/manager/exception/`.

## Complexity Tracking

*No violations — table intentionally omitted.*
