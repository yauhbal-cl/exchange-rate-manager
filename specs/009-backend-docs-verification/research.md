# Phase 0 Research: Backend Docs & Verification

No `NEEDS CLARIFICATION` markers remain in the spec (all resolved via its Assumptions section).
This research instead catalogs the concrete doc/behavior drift found by comparing README.md /
CLAUDE.md / contracts/openapi.yaml against the actual running system, and the decisions for
resolving each.

## R1 — Java version mismatch

**Decision**: Treat Java 21 (as stated in `CLAUDE.md`'s Tech Stack table and root `README.md`) as
the intended target; update `backend/pom.xml`'s `<java.version>` from `17` to `21` to match.

**Rationale**: `CLAUDE.md` is the authoritative, deliberately version-pinned spec ("Pin exact
versions below — checked against upstream release notes"). Two independent docs (root README,
CLAUDE.md) agree on 21; only the pom disagrees. Spring Boot 4.1.1 requires Java 17+ so bumping to
21 is safe. FR-004 requires docs to reflect *actual* behavior, but here the actual pom value is the
stale artifact, not the docs — confirmed by cross-referencing two independently-maintained sources
before deciding which side to change.

**Alternatives considered**: Roll README/CLAUDE.md back to "Java 17" instead — rejected because it
contradicts the project's explicit, dated tech-stack decision and would understate the LTS version
actually intended for this project.

## R2 — AI local model dependency under-documented in Quick Start

**Decision**: Add an explicit step to the README Quick Start calling out that `docker compose up -d`
also starts `ollama` and a one-shot `ollama-pull` container that pulls `llama3.2`, and that the AI
trend-insight endpoint will error until that pull completes. Document how to check pull completion
(`docker compose logs ollama-pull`) and the resulting error shape if Ollama is unreachable.

**Rationale**: FR-001 Acceptance Scenario 2 requires the docs to state exactly what to install and
run for the AI feature's local model dependency, with no undocumented prerequisite steps. Today,
`docker-compose.yml` already provisions Ollama + the model pull, but the Quick Start section never
mentions Ollama at all — a newcomer has no way to know the AI insight endpoint depends on it or how
long the first-run model pull takes.

**Alternatives considered**: Requiring a manual `ollama pull llama3.2` step outside Compose —
rejected, `docker-compose.yml` already automates this; the fix is documentation, not new tooling.

## R3 — Verification procedure inconsistency between README and CLAUDE.md

**Decision**: Standardize on `cd backend && ./mvnw verify` as *the* documented verification
procedure in both README.md and CLAUDE.md. Update README's "Backend tests: `./mvnw test`" line to
`./mvnw verify` and add one sentence noting it runs unit tests (Surefire) + Testcontainers-backed
integration tests (Failsafe) in one pass with a single BUILD SUCCESS/FAILURE result.

**Rationale**: FR-005/FR-006/FR-007 require a *single* documented procedure with an unambiguous,
repeatable pass/fail result. `./mvnw test` only runs unit tests and silently skips the
Failsafe-bound integration suite, which would under-verify the system and contradicts CLAUDE.md's
existing (correct) command. `./mvnw verify` is idempotent on an unchanged checkout — Testcontainers
starts and tears down a fresh Postgres container per run (Constitution Principle X), so no shared
state leaks between runs, satisfying FR-007/SC-003's repeatability requirement.

**Alternatives considered**: A custom shell script wrapping both test phases — rejected, adds a
maintenance surface for no benefit since Maven's `verify` lifecycle phase already does this natively.

## R4 — API documentation accuracy audit (FR-003/FR-004)

**Decision**: Diff each of the 6 paths in `contracts/openapi.yaml` (`/status`, `/exchange`,
`/exchange/refresh`, `/exchange/trend`, `/exchange/trend/insight`, `/exchange/usage`) against the
corresponding handler in `StatusController.java` / `ExchangeController.java` for: request
parameters, success response fields, and every distinct error condition the handler can produce
(mapped through the central `@RestControllerAdvice`). Any gap is fixed in `openapi.yaml` (source of
truth per CLAUDE.md) and re-verified by regenerating server interfaces and confirming controllers
still compile against the regenerated contract.

**Rationale**: FR-004 requires zero stale/aspirational documented behavior and zero undocumented
endpoints. Because springdoc-openapi/Swagger UI here is generated *from* the openapi-generator
server interfaces (which are generated *from* `contracts/openapi.yaml`), auditing the YAML directly
against controller logic is equivalent to auditing what Swagger UI will show, without needing to
run the server for this step (a separate quickstart.md scenario covers the live-server
cross-check).

**Alternatives considered**: Rely solely on manually inspecting the rendered Swagger UI at runtime
— insufficient alone since it can't reveal *undocumented* endpoints (a controller method with no
corresponding YAML path wouldn't be generated/registered as an interface at all, but a YAML path
with no controller implementation, or one whose `@ControllerAdvice`-mapped errors aren't listed in
the YAML's `responses`, would only surface by reading both sides).

## R5 — Environment-configurable settings inventory (FR-002)

**Decision**: Enumerate every `${VAR:default}` placeholder in `backend/src/main/resources/
application.yml` and confirm each has a corresponding README "Environment Configuration" entry.
Current inventory: `POSTGRES_USER`, `POSTGRES_PASSWORD`, `POSTGRES_DB` (documented), `FIXER_API_KEY`
(documented, no default — required), `FIXER_BASE_URL` (documented), `OLLAMA_BASE_URL`
(undocumented), `AI_INSIGHT_TIMEOUT_SECONDS` (undocumented).

**Rationale**: FR-001 Acceptance Scenario 3 requires *every* configurable setting to be documented
with its purpose. `OLLAMA_BASE_URL` and `AI_INSIGHT_TIMEOUT_SECONDS` exist in `application.yml` but
have no README entry — a gap to close in Phase 1 doc edits.

**Alternatives considered**: None — this is a straightforward enumeration gap, not a design choice.
