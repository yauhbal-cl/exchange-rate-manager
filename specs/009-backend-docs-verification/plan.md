# Implementation Plan: Backend Docs & Verification

**Branch**: `009-backend-docs-verification` | **Date**: 2026-08-23 | **Spec**: [spec.md](./spec.md)

**Input**: Feature specification from `/specs/009-backend-docs-verification/spec.md`

## Summary

Audit and correct backend-relevant documentation (root `README.md`, `CLAUDE.md`) and the published
API docs (Swagger UI generated from `contracts/openapi.yaml`) so they match the actual running
system, and establish `./mvnw verify` as the single documented, repeatable verification procedure.
No new backend functionality — this is a doc-accuracy and doc-consolidation pass over
already-implemented capabilities (data collection, exchange rate API, analytics, AI trend insight,
spread correction).

## Technical Context

**Language/Version**: Java 17 (`backend/pom.xml` `<java.version>`) — conflicts with README/CLAUDE.md
which both state Java 21; one of the two is wrong and must be reconciled (see research.md).

**Primary Dependencies**: Spring Boot 4.1.1, springdoc-openapi (Swagger UI from
`contracts/openapi.yaml`-generated interfaces), Spring Boot Actuator (health endpoint), Maven
Failsafe + Testcontainers (integration verification), ShedLock, Flyway.

**Storage**: PostgreSQL 17 (Docker Compose), Ollama (local LLM runtime, Docker Compose) — both are
external local dependencies that setup docs must cover per FR-001.

**Testing**: JUnit 5 (`maven-surefire-plugin`, unit) + `maven-failsafe-plugin` (integration,
Testcontainers-backed per Constitution Principle X) — both bound into the Maven `verify` lifecycle.

**Target Platform**: Local developer machine (Linux/macOS/WSL) for setup + verification; no hosted
CI infrastructure required per spec Assumptions.

**Project Type**: Existing web application (Spring Boot backend + Angular frontend, contract-driven
via `contracts/openapi.yaml`). This feature only edits documentation and, where a documented claim is
provably false (e.g. a version-number mismatch), the smallest config change needed to make the
doc true — not new features.

**Performance Goals**: N/A (documentation feature).

**Constraints**: SC-001 (≤15 min clean-checkout setup), SC-003 (≤10 min verification run,
deterministic across repeated runs on an unchanged checkout).

**Scale/Scope**: 2 controllers / 6 endpoints (`StatusController`, `ExchangeController`); root
`README.md` + `CLAUDE.md` backend sections; `contracts/openapi.yaml`-driven Swagger UI.

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

- **Principle VIII (Grounded AI Output, Honest Degradation)** — setup docs must state the AI
  insight feature's local model dependency (Ollama + `llama3.2`) as an explicit, non-optional setup
  step (FR-001, Acceptance Scenario 2). Currently under-documented in README Quick Start (see
  research.md item R2). Plan resolves this in Phase 1 doc edits, not by changing behavior.
- **Principle X (Test Isolation via Testcontainers)** — the single verification procedure
  (`./mvnw verify`) must not introduce any test path that touches a real/shared database. Already
  satisfied by existing failsafe/Testcontainers wiring; this feature only needs to *document* that
  procedure accurately, not change test isolation behavior.
- **API Documentation requirement (Technology Stack Requirements)** — Swagger/OpenAPI must stay
  "kept consistent with the API's actual request/response contracts" (FR-003/FR-004). This feature
  is exactly that consistency audit; no contract shape changes are in scope unless the audit finds
  actual drift between `contracts/openapi.yaml` and controller behavior.
- No principle requires new code paths, new entities, or new API shapes for this feature.

**Result**: PASS. No complexity or violations to justify; Complexity Tracking table not used.

## Project Structure

### Documentation (this feature)

```text
specs/009-backend-docs-verification/
├── plan.md              # This file (/speckit-plan command output)
├── research.md          # Phase 0 output (/speckit-plan command)
├── data-model.md        # Phase 1 output (/speckit-plan command) — N/A, no new entities
├── quickstart.md        # Phase 1 output (/speckit-plan command)
└── tasks.md             # Phase 2 output (/speckit-tasks command — NOT created by /speckit-plan)
```

No `contracts/` subfolder for this feature: the project-level contract
(`contracts/openapi.yaml`) is the artifact under audit, not something this feature defines fresh.
Any drift found between it and controller behavior is corrected in place and re-verified against
the generated Swagger UI, not modeled as a new contract.

### Source Code (repository root)

```text
exchange-rate-manager/
├── README.md                          # backend Quick Start, env config, verification — audited/corrected
├── CLAUDE.md                          # Commands + Tech Stack tables — audited/corrected
├── contracts/openapi.yaml             # published API contract — audited against controllers, corrected if stale
├── backend/
│   ├── pom.xml                        # java.version reconciled against documented Java 21 (see research.md)
│   ├── src/main/resources/application.yml   # env-configurable settings — cross-checked against README FR-002 table
│   └── src/main/java/com/exchangerate/manager/controller/
│       ├── StatusController.java      # audited endpoint: request/response/error docs
│       └── ExchangeController.java    # audited endpoints: request/response/error docs (5 endpoints)
└── docker-compose.yml                 # postgres + ollama + ollama-pull — setup doc must reference all three
```

**Structure Decision**: No new modules. Existing `backend/`, `contracts/`, and root docs are edited
in place. Verification of the audit happens by running the real backend (`./mvnw spring-boot:run`),
hitting Swagger UI / actual endpoints, and running `./mvnw verify`.

## Complexity Tracking

*Not applicable — Constitution Check passed with no violations.*
