---

description: "Task list for Scaffold Backend and Frontend Repositories with Contract Setup"
---

# Tasks: Scaffold Backend and Frontend Repositories with Contract Setup

**Input**: Design documents from `/specs/001-scaffold-backend-frontend/`

**Prerequisites**: plan.md, spec.md, research.md, data-model.md, quickstart.md, contracts/openapi.yaml

**Tests**: Not explicitly requested in spec.md; a context-load smoke test is included per plan.md
Project Structure (`src/test/java/...` context-load/health smoke test) since it's part of the
committed scaffold structure, not TDD-for-behavior.

**Organization**: Tasks grouped by user story (US1 backend, US2 frontend, US3 contract pipeline)
per spec.md priorities. US3's contract file itself is Foundational (both stories generate from
it), but the generation-pipeline *wiring/verification* is US3.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Can run in parallel (different files, no dependencies)
- **[Story]**: US1 (backend runnable), US2 (frontend runnable), US3 (contract drives both sides)

## Path Conventions

Web app per plan.md: `backend/`, `frontend/`, `contracts/` as top-level siblings, plus
`docker-compose.yml` at repo root.

---

## Phase 1: Setup (Shared Infrastructure)

**Purpose**: Repo-root scaffolding shared by every story.

- [x] T001 Create top-level directories `backend/`, `frontend/` per plan.md Project Structure (empty skeletons, no framework files yet)
- [x] T002 [P] Write `contracts/openapi.yaml` at repo root with the `/status` `GET` operation and `ServiceStatus` schema (`status` enum UP/DOWN, `databaseConnected` boolean, `timestamp` date-time) per data-model.md and the feature-local copy at `specs/001-scaffold-backend-frontend/contracts/openapi.yaml`
- [x] T003 [P] Write `docker-compose.yml` at repo root with a single `postgres:17` service: standard port exposed, named volume for persistence, env-configurable `POSTGRES_USER`/`POSTGRES_PASSWORD`/`POSTGRES_DB` matching what `backend/src/main/resources/application.yml` will consume

**Checkpoint**: Contract file and local infra definition exist; both stories can generate/consume from them.

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: Nothing story-specific — these are prerequisites both US1 and US2 need before their own work is meaningful. No shared code module needed for this scaffolding feature beyond Setup, so this phase is intentionally thin.

- [x] T004 Verify `contracts/openapi.yaml` (T002) parses as valid OpenAPI 3.0.3 (e.g. `npx @redocly/cli lint contracts/openapi.yaml` or equivalent validator) before either side generates from it

**Checkpoint**: Contract validated — backend and frontend scaffolding (US1, US2) can now proceed in parallel.

---

## Phase 3: User Story 1 - Backend service skeleton is runnable (Priority: P1) 🎯 MVP

**Goal**: `backend/` starts, connects to PostgreSQL, and reports health via Actuator + a
contract-generated `/api/v1/status` endpoint.

**Independent Test**: `docker compose up -d && cd backend && ./mvnw spring-boot:run`, then
`curl http://localhost:8080/actuator/health` and `curl http://localhost:8080/api/v1/status` both
succeed with `databaseConnected: true`.

### Implementation for User Story 1

- [x] T005 [US1] Create `backend/pom.xml`: Spring Boot 4.1.1 parent, Java 21, dependencies `spring-boot-starter-web`, `spring-boot-starter-data-jpa`, `spring-boot-starter-actuator`, `spring-boot-starter-validation`, a scheduling stub dependency (`spring-context` is already transitive — just ensure `@EnableScheduling`-capable, no extra dep needed), PostgreSQL JDBC driver, springdoc-openapi 3.x starter
- [x] T006 [US1] Add `openapi-generator-maven-plugin` to `backend/pom.xml` bound to `generate-sources` phase: generator `spring`, `interfaceOnly=true`, input `../contracts/openapi.yaml`, output `target/generated-sources/openapi`, package `com.exchangerate.manager.api` (per research.md decision)
- [x] T007 [P] [US1] Create `backend/src/main/java/com/exchangerate/manager/ExchangeRateManagerApplication.java` with `@SpringBootApplication` main class
- [x] T008 [P] [US1] Create `backend/src/main/resources/application.yml`: datasource config (host/port/db/user/password matching `docker-compose.yml` from T003), `management.endpoint.health.show-details`, actuator health exposed, springdoc path config
- [x] T009 [US1] Create `backend/src/main/java/com/exchangerate/manager/service/StatusService.java`: business logic computing `ServiceStatus` (status UP/DOWN, databaseConnected via a live datasource check, timestamp) — status MUST be DOWN if databaseConnected is false, per data-model.md validation rule (depends on T006 generated DTO, T008 datasource)
- [x] T010 [US1] Create `backend/src/main/java/com/exchangerate/manager/controller/StatusController.java` implementing the generated `StatusApi` interface, delegating to `StatusService`, thin controller per Constitution Principle VI (depends on T006, T009)
- [x] T011 [P] [US1] Create `backend/src/main/java/com/exchangerate/manager/config/OpenApiConfig.java` for springdoc/Swagger UI metadata (title, version from contract)
- [x] T012 [US1] Create `backend/src/test/java/com/exchangerate/manager/ExchangeRateManagerApplicationTests.java`: Spring context-load smoke test
- [X] T013 [US1] Verify edge case: stop DB (`docker compose stop`), start backend, confirm a clear startup failure/log (not a hang) — adjust `application.yml` datasource fail-fast settings (e.g. `spring.datasource.hikari.initialization-fail-timeout`) if needed (confirmed: with no DB, app fails within ~15-20s with a clear Hikari/Hibernate connection error, not a hang; added `hikari.connection-timeout: 5000` to `application.yml` to bound the default 30s wait, and added missing `org.openapitools:jackson-databind-nullable` dependency to `pom.xml` to unblock the build)

**Checkpoint**: `./mvnw verify` passes; backend runs standalone against `docker compose up -d` Postgres and reports health.

---

## Phase 4: User Story 2 - Frontend application skeleton is runnable (Priority: P1)

**Goal**: `frontend/` builds, serves a default page, and has a working `generate:api` script
producing a typed client from the same contract, with backend address configurable via
environment files.

**Independent Test**: `cd frontend && npm install && npm run generate:api && npm start` serves
`http://localhost:4200` without errors; changing `apiBaseUrl` in `environment.ts` requires no
source code changes.

### Implementation for User Story 2

- [x] T014 [US2] Scaffold `frontend/` as an Angular 21 standalone app (zoneless, Vitest, no NgModules) via Angular CLI — `package.json`, `angular.json`, `tsconfig*.json`, `src/main.ts`
- [x] T015 [P] [US2] Add Tailwind CSS to `frontend/`: install deps, `tailwind.config.js`/PostCSS config, import Tailwind directives into the global stylesheet
- [x] T016 [P] [US2] Add `@openapitools/openapi-generator-cli` as a dev dependency in `frontend/package.json` and a `generate:api` npm script: `openapi-generator-cli generate -i ../contracts/openapi.yaml -g typescript-angular -o src/app/api-client`
- [x] T017 [US2] Create `frontend/src/environments/environment.ts` (dev default `apiBaseUrl: 'http://localhost:8080'`) and `frontend/src/environments/environment.production.ts` (or equivalent target) demonstrating the override pattern, wired via `angular.json` `fileReplacements` (depends on T014)
- [x] T018 [US2] Create `frontend/src/app/app.ts`, `app.config.ts`, `app.routes.ts`: standalone bootstrap, zoneless change detection, a default landing component rendering a simple page (depends on T014, T015)
- [x] T019 [US2] Run `npm run generate:api` to produce `frontend/src/app/api-client/` from `contracts/openapi.yaml` (T002/T016) and confirm output is committed as generated (never hand-edited)
- [x] T020 [US2] Wire the default landing component (T018) to call the generated `StatusService`/`DefaultApi` client (T019) against `environment.apiBaseUrl`, displaying the `/api/v1/status` result, proving frontend-to-generated-client wiring end-to-end

**Checkpoint**: `npm test` and `npm start` succeed; frontend independently serves and (when backend from US1 is running) displays live status.

---

## Phase 5: User Story 3 - Shared API contract drives both sides (Priority: P2)

**Goal**: Prove that a single contract change propagates to both generated backend and frontend
code with zero hand edits, and that malformed-contract or version-drift failures surface loudly.

**Independent Test**: Add a trivial field to `ServiceStatus` in `contracts/openapi.yaml`, regenerate
both sides, confirm both reflect the change with no manual edits; introduce a malformed contract and
confirm both generation steps fail loudly.

### Implementation for User Story 3

- [X] T021 [US3] Confirm backend build fails loudly on malformed `contracts/openapi.yaml` (e.g. temporarily break YAML syntax, run `./mvnw generate-sources`, confirm non-zero exit with a clear generator error, then restore the file) — validates FR-005/FR-007 edge case (confirmed: exit code 1, openapi-generator-maven-plugin threw MojoExecutionException from a SnakeYAML ParserException; restored file re-ran with exit code 0)
- [x] T022 [US3] Confirm frontend `npm run generate:api` fails loudly on the same malformed contract (repeat the temporary-break/restore from T021) — validates FR-006/FR-007 edge case
- [X] T023 [US3] Add a trivial optional field to `ServiceStatus` in `contracts/openapi.yaml` (and its feature-local copy), regenerate backend (`./mvnw generate-sources`) and frontend (`npm run generate:api`), confirm the field appears in both generated outputs with zero hand edits, then decide whether to keep or revert the field for this feature's scope — validates FR-004, FR-007, SC-002, SC-003 (confirmed: added `uptimeSeconds: integer/int64` to `ServiceStatus`; appeared as `Long uptimeSeconds` (NOT_REQUIRED) in the generated backend DTO and `uptimeSeconds?: number` in the generated frontend model, with zero hand edits on either side; reverted the field from both contract copies since this feature has no domain endpoints yet — `git diff` on the contract files is empty and both regenerations reproduce the original generated shape)
- [x] T024 [P] [US3] Add a repo-root README section (or update existing `README.md`) documenting: "contract changes go in `contracts/openapi.yaml` first, then regenerate both sides" and the two regeneration commands, so version drift between sides is a documented, catchable process step (supports Edge Case: detecting one side forgot to regenerate) (duplicate of Phase 6 entry below — content already done, see there)

**Checkpoint**: Contract-drives-both-sides pipeline is proven end-to-end in both directions (success and failure cases).

---

## Phase 6: Polish & Cross-Cutting Concerns

**Purpose**: Final validation against the spec's success criteria.

- [x] T025 [P] Add root-level `README.md` (if not fully covered by T024) with clean-checkout setup steps matching quickstart.md, targeting SC-001 (<15 min clean-checkout-to-running)
- [x] T024 [P] [US3] Add a repo-root README section (or update existing `README.md`) documenting: "contract changes go in `contracts/openapi.yaml` first, then regenerate both sides" and the two regeneration commands, so version drift between sides is a documented, catchable process step (supports Edge Case: detecting one side forgot to regenerate)
- [X] T026 Run the full quickstart.md validation script end-to-end from a clean checkout (steps 1–6) and confirm SC-001 through SC-004 all pass (confirmed: `docker compose up -d` → healthy Postgres, backend `./mvnw spring-boot:run` → `/actuator/health` UP with `db` UP and `/api/v1/status` returning `databaseConnected: true` (SC-004), `npm install && npm run generate:api && npm run build` clean with a smoke-tested `npm start` serving 200 at localhost:4200, environment fileReplacements confirmed wired for configurable `apiBaseUrl`; contract-drives-both-sides (SC-002/SC-003) already proven in T023; fixed two real bugs found during this run — invalid `spring.jackson.serialization.write-dates-as-timestamps` property for the Jackson 3/Spring Boot 4.1 stack, and missing `/api/v1` request-mapping prefix on `StatusController` — total wall-clock ~4 min including a fresh `postgres:17` image pull, well under the 15-minute SC-001 budget; all processes/containers cleaned up afterward)
- [x] T027 [P] Configure linting/formatting: backend (e.g. Maven checkstyle/spotless if desired) and frontend (ESLint/Prettier defaults from Angular CLI scaffold) — keep minimal, don't over-engineer for a scaffolding feature (Frontend: Prettier/ESLint via Angular CLI defaults; Backend: Maven setup ready)

---

## Dependencies & Execution Order

### Phase Dependencies

- **Setup (Phase 1)**: No dependencies — start immediately.
- **Foundational (Phase 2)**: Depends on T002 (contract file) from Setup — BLOCKS US1's T006 and US2's T016/T019 (both generate from the validated contract).
- **User Story 1 (Phase 3)**: Depends on Foundational. Independent of US2.
- **User Story 2 (Phase 4)**: Depends on Foundational. Independent of US1, except T020 (calling live status) benefits from US1 running but the story is still independently testable (build/serve succeeds without backend up; T020's live call can degrade to a visible fetch error, which is itself acceptable per Edge Cases).
- **User Story 3 (Phase 5)**: Depends on US1 (T006, generated backend code) and US2 (T016/T019, generated frontend code) both existing, since it exercises regeneration on both sides.
- **Polish (Phase 6)**: Depends on US1, US2, US3 all complete.

### Parallel Opportunities

- T002 and T003 in parallel (Setup).
- T007, T008, T011 in parallel within US1 (different files).
- T015 and T016 in parallel within US2 (different files).
- Once Foundational (T004) completes, all of US1 (Phase 3) and US2 (Phase 4) can proceed in parallel by different developers.
- T024 in Phase 5, and T025/T027 in Phase 6, are parallelizable with adjacent tasks in their phase.

---

## Parallel Example: Setup + User Story 1

```bash
# Setup, in parallel:
Task: "Write contracts/openapi.yaml with /status operation and ServiceStatus schema"
Task: "Write docker-compose.yml with postgres:17 service"

# User Story 1 implementation, in parallel (after T006 generates the interface):
Task: "Create ExchangeRateManagerApplication.java main class"
Task: "Create application.yml datasource/actuator/springdoc config"
Task: "Create OpenApiConfig.java for springdoc metadata"
```

---

## Implementation Strategy

### MVP First

1. Complete Phase 1 (Setup) and Phase 2 (Foundational).
2. Complete Phase 3 (User Story 1 — backend runnable). This alone proves the critical path per spec.md priority rationale.
3. **STOP and VALIDATE**: `docker compose up -d && cd backend && ./mvnw spring-boot:run`, curl both endpoints.
4. Demo backend health/status independently.

### Incremental Delivery

1. Setup + Foundational → contract and infra definitions ready.
2. Add User Story 1 → backend runs and reports health → demo.
3. Add User Story 2 → frontend runs and serves → demo (parallel-capable with US1 by a second developer).
4. Add User Story 3 → prove contract regeneration drives both sides, including failure-mode verification.
5. Polish → clean-checkout timing validation (SC-001) and linting.

## Notes

- No domain entities in this feature (data-model.md) — all "models" here are contract-generated DTOs, not hand-written.
- Generated code (`backend/target/generated-sources/openapi`, `frontend/src/app/api-client/`) is never hand-edited — tasks only ever *run* generation, never edit output.
- Commit after each task or logical group; stop at either Phase 3 or Phase 4 checkpoint to validate that story independently before continuing.
