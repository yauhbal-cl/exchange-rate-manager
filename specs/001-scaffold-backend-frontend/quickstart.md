# Quickstart: Validate the Scaffold

Proves User Stories 1–3 (SC-001 through SC-004) once implementation tasks land.

## Prerequisites

- Java 21, Maven 3.9.x, Node 22 LTS, npm, Docker + docker compose
- Repo cloned, on branch `001-scaffold-backend-frontend`

## 1. Start local infrastructure

```bash
docker compose up -d
```

**Expect**: PostgreSQL 17 container running and accepting connections on its configured port.

## 2. Start the backend

```bash
cd backend && ./mvnw spring-boot:run
```

**Expect**:
- Build triggers `openapi-generator-maven-plugin` at `generate-sources`, producing server
  interfaces/DTOs from `../contracts/openapi.yaml` under `target/generated-sources/openapi`
  (see [contracts/openapi.yaml](./contracts/openapi.yaml), [data-model.md](./data-model.md)).
- App starts without error.
- `curl http://localhost:8080/actuator/health` → `{"status":"UP", ...}` including a `db` component `UP`.
- `curl http://localhost:8080/api/v1/status` → `ServiceStatus` JSON with `databaseConnected: true`.

Validates User Story 1 (Acceptance Scenarios 1–2), FR-002, FR-005, FR-008, SC-004.

## 3. Generate the frontend client and start the frontend

```bash
cd frontend && npm install && npm run generate:api && npm start
```

**Expect**:
- `generate:api` produces `src/app/api-client/` from the same `contracts/openapi.yaml` — no
  manual edits required afterward.
- `npm start` serves the app at `http://localhost:4200` with a default page, no build/runtime
  errors.

Validates User Story 2 (Acceptance Scenario 1), FR-003, FR-006, FR-009.

## 4. Verify configurable backend address (User Story 2, Scenario 2)

Change `apiBaseUrl` in `frontend/src/environments/environment.ts` (or select a different Angular
build configuration with a different `environment.*.ts`) to point at a different backend host,
rebuild/reserve — confirm no source code (only environment config) changed.

## 5. Verify contract-drives-both-sides (User Story 3)

1. Add a trivial field to `ServiceStatus` in `contracts/openapi.yaml`.
2. Re-run backend `./mvnw generate-sources` (or a full build) — confirm the generated Java
   interface/DTO now includes the field, with zero hand edits.
3. Re-run `npm run generate:api` — confirm the generated TypeScript model now includes the field,
   with zero hand edits.

Validates FR-004, FR-005, FR-006, FR-007, SC-002, SC-003.

## 6. Verify failure edge cases

- Stop the database (`docker compose stop`) then start the backend: expect a clear startup
  failure/log, not a hang or silent success.
- Introduce a malformed `contracts/openapi.yaml` (e.g. break YAML syntax) and run either side's
  generation step: expect the build to fail loudly, not produce partial output.

## Total time budget

All of the above, from a clean checkout, MUST complete in under 15 minutes (SC-001).
