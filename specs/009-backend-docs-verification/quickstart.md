# Quickstart: Validating Backend Docs & Verification

Run these on a clean checkout (or a checkout where local state is reset) to confirm the corrected
docs and verification procedure actually hold. Each scenario maps to an acceptance scenario in
[spec.md](./spec.md).

## Prerequisites

- Docker + Docker Compose, Java 21, Maven 3.9.x (per corrected README/CLAUDE.md — see
  [research.md#r1](./research.md#r1--java-version-mismatch))
- No prior `docker compose up` volumes for this project (or run `docker compose down -v` first to
  simulate a clean checkout)

## Scenario 1 — Setup from docs alone reaches a healthy backend (User Story 1)

1. `docker compose up -d` — confirm this also starts `ollama` and the one-shot `ollama-pull`
   container (`docker compose ps`; `docker compose logs ollama-pull` should show a completed pull of
   `llama3.2`).
2. `cd backend && export FIXER_API_KEY=<your-key> && ./mvnw spring-boot:run`
3. `curl http://localhost:8080/api/v1/status` — expect a healthy response with no manual steps
   beyond what README documents.
4. Confirm every env var referenced in README's Environment Configuration section
   (`POSTGRES_USER`, `POSTGRES_PASSWORD`, `POSTGRES_DB`, `FIXER_API_KEY`, `FIXER_BASE_URL`,
   `OLLAMA_BASE_URL`, `AI_INSIGHT_TIMEOUT_SECONDS`) is present in `application.yml` and vice versa —
   no gaps in either direction ([research.md#r5](./research.md#r5--environment-configurable-settings-inventory-fr-002)).

**Pass condition**: Steps 1-3 complete with no undocumented step or unexplained error, matching
SC-001 (≤15 minutes).

## Scenario 2 — Published API docs match live behavior (User Story 2)

1. With the backend running, open `http://localhost:8080/swagger-ui.html`.
2. For each of the 6 documented paths (`/status`, `/exchange`, `/exchange/refresh`,
   `/exchange/trend`, `/exchange/trend/insight`, `/exchange/usage`), send the documented example
   request and confirm the actual response matches the documented shape.
3. Trigger one documented error case per endpoint that can error (e.g. unknown currency on
   `/exchange`, no rate data for a requested date) and confirm the response matches the documented
   error shape (`ProblemDetail`).

**Pass condition**: No undocumented endpoint, no documented endpoint/field/error missing from
actual behavior — matches SC-002 (100% coverage).

## Scenario 3 — Single verification procedure gives a repeatable pass/fail (User Story 3)

1. `cd backend && ./mvnw verify`
2. Confirm output ends with a single unambiguous `BUILD SUCCESS` or `BUILD FAILURE`, and that a
   `BUILD FAILURE` clearly names the failing test class/phase.
3. Run `./mvnw verify` again on the same, unchanged checkout.

**Pass condition**: Step 1 completes in under 10 minutes with a clear result (SC-003); step 3
produces the same pass/fail result as step 1 (FR-007); a deliberately broken test (temporarily) is
identifiable as the failing subsystem within 2 minutes of reading the output (SC-004).

## Scenario 4 — Undocumented dependency / missing infra fails clearly (Edge Cases)

1. `docker compose down` (stop Postgres and Ollama).
2. `cd backend && ./mvnw verify` — confirm the Testcontainers-backed integration tests still pass
   (they provision their own ephemeral Postgres per Constitution Principle X, independent of the
   Compose Postgres) and that the message, if anything fails, is actionable rather than a raw
   connection-refused stack trace with no context.
3. Separately, attempt to start the backend via `./mvnw spring-boot:run` without Ollama running and
   confirm `/exchange/trend/insight` degrades to a clear, explicit error rather than a fabricated
   insight (Constitution Principle VIII) — not a raw stack trace with no explanation.
