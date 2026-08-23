# exchange-rate-manager

Exchange Rate Management System — A full-stack application for managing exchange rates with AI-powered trend insights.

## Quick Start

From a clean checkout, complete setup in under 15 minutes:

```bash
# 1. Start local database + Ollama
docker compose up -d

# 2. Build and run backend
cd backend
./mvnw spring-boot:run

# 3. In another terminal: build and run frontend
cd frontend
npm install
npm run generate:api
npm start
```

- Backend: `http://localhost:8080/api/v1/status` (health check)
- Frontend: `http://localhost:4200` (dashboard)
- API Docs: `http://localhost:8080/swagger-ui.html`

`docker compose up -d` also starts the `ollama` service and a one-shot `ollama-pull` container
that pulls the `llama3.2` model. The pull can take a few minutes on first run. Confirm it finished
before relying on the AI insight endpoint:

```bash
docker compose logs ollama-pull
```

Look for a completed pull (e.g. `success`) in the output. Until the pull finishes,
`/exchange/trend/insight` will fail or degrade instead of returning an insight.

## Project Structure

```
exchange-rate-manager/
├── backend/              Spring Boot 4.1.1 REST API (Java 21)
├── frontend/             Angular 22 SPA (TypeScript 6)
├── contracts/            OpenAPI 3.0.3 contract (source of truth)
├── docker-compose.yml    PostgreSQL 17 + Ollama (local dev)
└── README.md            This file
```

## Contract-Driven Development

The shared OpenAPI contract (`contracts/openapi.yaml`) is the single source of truth. Both backend and frontend generate code from it — never hand-edit generated output.

### Regenerate After Contract Changes

1. Update `contracts/openapi.yaml`
2. Regenerate backend:
   ```bash
   cd backend
   ./mvnw generate-sources
   ```
3. Regenerate frontend:
   ```bash
   cd frontend
   npm run generate:api
   ```
4. Rebuild as needed and verify endpoints match on both sides

### Testing Contract Changes

- Test malformed contract: edit `contracts/openapi.yaml` to break YAML syntax, regenerate both sides, expect clear error messages (not hangs or silent failures)
- Test contract propagation: add a field to `ServiceStatus`, regenerate both sides, confirm field appears in generated code with zero hand edits

## Architecture

- **Backend**: Spring Boot with Spring Data JPA, PostgreSQL persistence, Spring Actuator for health checks, springdoc-openapi for Swagger UI
- **Frontend**: Angular standalone components, zoneless change detection, Tailwind CSS for styling
- **Contract**: OpenAPI 3.0.3 + TypeScript/Java codegen ensures type safety across boundaries
- **Database**: PostgreSQL 17 (via Docker Compose)
- **AI**: Ollama + Spring AI power the `/exchange/trend/insight` endpoint, which generates a data-grounded narrative commentary on a requested trend period

## Environment Configuration

### Frontend

Backend URL is configurable via `src/environments/environment.ts`:
- **Dev** (`ng serve`): `http://localhost:8080`
- **Prod** (`ng build --configuration production`): Uses `environment.production.ts`

No source code changes needed to point at a different backend.

### Backend

Database connection via `src/main/resources/application.yml`:
- Host, port, credentials match `docker-compose.yml` defaults
- Configurable via `POSTGRES_USER`, `POSTGRES_PASSWORD`, `POSTGRES_DB` env vars

Fixer.io data collection requires a `FIXER_API_KEY` env var. The daily scheduled job (and the
manual refresh endpoint) call Fixer.io's `/latest` endpoint to collect exchange rates, and this
key authenticates that call. There is no default (`fixer.api-key: ${FIXER_API_KEY}` in
`application.yml`) — the backend fails to start without it.

- Sign up for a free key at [fixer.io](https://fixer.io/) (free tier is sufficient) and copy the
  "Access Key" from the dashboard.
- Set it before running the backend:
  ```bash
  export FIXER_API_KEY=your-key-here
  ```
- `FIXER_BASE_URL` is optional and defaults to `https://data.fixer.io/api`.

The AI trend insight feature (Spring AI + Ollama) is configured via the same
`application.yml`:
- `OLLAMA_BASE_URL` is optional and defaults to `http://localhost:11434`, pointing the backend at
  a local Ollama instance.
- `AI_INSIGHT_TIMEOUT_SECONDS` is optional and defaults to `30`, the read-timeout (in seconds) for
  AI insight calls.

## Prerequisites

- Java 21 + Maven 3.9.x (backend)
- Node.js 22 LTS + npm 11 (frontend)
- Docker + Docker Compose (local database)

## Development

- Backend tests: `cd backend && ./mvnw verify` — runs unit tests (Surefire) and Testcontainers-backed integration tests (Failsafe) in one pass, reporting a single BUILD SUCCESS/BUILD FAILURE result.
- Frontend tests: `cd frontend && npm test`
- Linting: Frontend uses Prettier (scaffold defaults)

## API Documentation

- **Swagger UI**: [http://localhost:8080/swagger-ui.html](http://localhost:8080/swagger-ui.html) (when backend running)
- **Contract file**: `contracts/openapi.yaml`

See CLAUDE.md for architecture decisions and Constitution principles.