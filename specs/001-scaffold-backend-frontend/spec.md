# Feature Specification: Scaffold Backend and Frontend Repositories with Contract Setup

**Feature Branch**: `001-scaffold-backend-frontend`

**Created**: 2026-08-22

**Status**: Draft

**Input**: User description: "creating front end and backend repositories with needed dependencies & contract setup"

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Backend service skeleton is runnable (Priority: P1)

As a developer joining the project, I need a backend service that starts up, exposes a health/status
endpoint, and connects to its database, so I can begin building business features on a working
foundation instead of empty folders.

**Why this priority**: Nothing else in the system (API endpoints, scheduler, AI insight) can be built
until the backend runs and talks to a database. This is the critical path.

**Independent Test**: Start the backend and its local database dependency; confirm the service starts
without error and answers a basic health check.

**Acceptance Scenarios**:

1. **Given** a clean checkout of the repository, **When** a developer starts local infrastructure and
   the backend service, **Then** the service starts successfully and reports itself healthy.
2. **Given** the backend service is running, **When** a developer queries its status endpoint,
   **Then** it responds confirming the database connection is active.

---

### User Story 2 - Frontend application skeleton is runnable (Priority: P1)

As a developer, I need a frontend application shell that builds, serves locally, and is configured to
call the backend, so I can start implementing screens against real (or contract-defined) endpoints.

**Why this priority**: The dashboard cannot be built until there is a working application shell wired
to a configurable backend location. Equal priority to the backend since both must exist before feature
work starts.

**Independent Test**: Start the frontend application locally and confirm it loads a default page in a
browser without build or runtime errors, using a configurable backend address.

**Acceptance Scenarios**:

1. **Given** a clean checkout of the repository, **When** a developer starts the frontend application,
   **Then** it builds and serves without error.
2. **Given** the frontend is running, **When** the backend location is changed via configuration,
   **Then** the frontend uses the new location without code changes.

---

### User Story 3 - Shared API contract drives both sides (Priority: P2)

As a developer working on either side of the system, I need one authoritative description of the API
shape that both the backend and frontend generate code from, so the two sides cannot silently drift
out of sync.

**Why this priority**: Prevents integration bugs and rework later, but the backend and frontend
skeletons (P1) can exist and be demoed before this generation pipeline is wired end-to-end.

**Independent Test**: Change the shared contract description, regenerate code for both backend and
frontend, and confirm both sides reflect the change without hand-editing generated files.

**Acceptance Scenarios**:

1. **Given** the shared API contract file, **When** a developer runs backend code generation,
   **Then** server-side interfaces/data shapes matching the contract are produced automatically.
2. **Given** the shared API contract file, **When** a developer runs frontend code generation,
   **Then** a typed client matching the contract is produced automatically.
3. **Given** generated code exists on both sides, **When** a developer inspects it, **Then** no part
   of the generated output has been hand-edited.

---

### Edge Cases

- What happens when a developer starts the frontend or backend without local infrastructure (e.g., no
  database) running? System should fail with a clear, actionable error rather than hanging silently.
- What happens when the shared contract file has an invalid or malformed shape? Code generation MUST
  fail loudly at build time rather than producing partial or silently broken output.
- What happens when the backend and frontend are generated from different versions of the contract
  (e.g., one side forgot to regenerate)? This should be detectable (e.g., a build/lint failure or
  version mismatch), not a silent runtime failure discovered by end users.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: The repository MUST contain a separate, independently buildable backend project and
  frontend project.
- **FR-002**: The backend project MUST declare all dependencies required to run a REST API, connect to
  a relational database, and perform scheduled background work.
- **FR-003**: The frontend project MUST declare all dependencies required to build and serve a
  single-page application and to call HTTP APIs.
- **FR-004**: The repository MUST contain a single, hand-maintained API contract file that is the
  source of truth for the shape of the API.
- **FR-005**: The backend build process MUST generate server-side API interfaces/data types from the
  shared contract file automatically, without requiring manual transcription.
- **FR-006**: The frontend MUST provide a way to generate a typed API client from the shared contract
  file automatically, without requiring manual transcription.
- **FR-007**: Generated code on either side MUST NOT be hand-edited; the contract file is the only
  place API shape changes are made.
- **FR-008**: The backend project MUST start successfully against a local database instance and expose
  a way to verify it is healthy and connected.
- **FR-009**: The frontend project MUST start successfully and serve a default page, with the backend
  address configurable without code changes.
- **FR-010**: Local supporting infrastructure (at minimum, the database) MUST be startable with a
  single command for local development.
- **FR-011**: The repository structure MUST keep backend, frontend, and the shared contract as
  clearly separated top-level areas so each can be worked on independently.

### Key Entities

- **API Contract**: The single authoritative description of API resources, operations, and data
  shapes; consumed by both backend and frontend code generation.
- **Backend Project**: The independently buildable service codebase, including its declared
  dependencies and generated server-side API code.
- **Frontend Project**: The independently buildable application codebase, including its declared
  dependencies and generated client-side API code.
- **Local Development Infrastructure**: The supporting services (at minimum a database) needed to run
  the backend locally.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: A new developer can get both the backend and frontend running locally, from a clean
  checkout, in under 15 minutes following only the repository's own instructions.
- **SC-002**: 100% of API data shapes exposed by the backend and consumed by the frontend originate
  from the single shared contract file, with zero hand-written duplicate type/interface definitions.
- **SC-003**: A change to the shared contract file is reflected in both generated backend and frontend
  code with a single regeneration step per side, with zero manual edits to generated files.
- **SC-004**: The backend reports a healthy, database-connected status on 100% of clean local starts
  when local infrastructure is running.

## Assumptions

- "Repositories" refers to top-level backend and frontend project folders within this single
  monorepo, not separate git repositories, consistent with the existing monorepo layout.
- Local development infrastructure runs via a single local orchestration command; no shared/remote
  environment is in scope for this feature.
- No business/domain API endpoints (exchange rate retrieval, conversion, AI insight, etc.) are
  implemented as part of this feature — only the runnable skeletons, dependency setup, and the
  contract generation pipeline. Domain functionality is scoped to later features.
- Authentication/authorization is out of scope for this scaffolding feature.
- The shared contract file's initial content only needs to be complete enough to prove the
  generation pipeline works end-to-end (e.g., one sample endpoint); full domain API definition is a
  later feature.
