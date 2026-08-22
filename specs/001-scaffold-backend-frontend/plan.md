# Implementation Plan: Scaffold Backend and Frontend Repositories with Contract Setup

**Branch**: `001-scaffold-backend-frontend` | **Date**: 2026-08-22 | **Spec**: [spec.md](./spec.md)

**Input**: Feature specification from `/specs/001-scaffold-backend-frontend/spec.md`

## Summary

Scaffold two independently buildable projects (`backend/`, `frontend/`) plus a hand-maintained
`contracts/openapi.yaml` that both sides generate code from. Backend: Spring Boot 4.1.1 REST API on
Java 21, Spring Data JPA against PostgreSQL 17, a scheduling dependency stub (for later Fixer.io
ingestion), health endpoint via Spring Boot Actuator, server interfaces/DTOs generated from the
contract at build time via `openapi-generator-maven-plugin`. Frontend: Angular 21 standalone-app
shell, zoneless, Tailwind CSS, an `npm run generate:api` script producing a typed client from the
same contract via `openapi-generator-cli`, backend base URL sourced from `environment.ts` /
environment-specific overrides. Local infra: `docker-compose.yml` running PostgreSQL 17. No domain
endpoints — one sample contract path proves the generation pipeline end-to-end.

## Technical Context

**Language/Version**: Java 21 (backend), TypeScript 5.9+ (frontend)

**Primary Dependencies**: Spring Boot 4.1.1 (spring-boot-starter-web, spring-boot-starter-data-jpa,
spring-boot-starter-actuator, spring-boot-starter-validation), springdoc-openapi 3.x,
openapi-generator-maven-plugin (backend); Angular 21 (@angular/core, @angular/common/http),
Tailwind CSS 4 (`@tailwindcss/postcss`, CSS-first config), @openapitools/openapi-generator-cli
(frontend)

**Storage**: PostgreSQL 17 (via `docker-compose.yml`), no schema/entities yet — this feature only
needs a connectable datasource for the health check

**Testing**: JUnit 5 + Spring Boot Test (backend, via `./mvnw verify`); Vitest (frontend, via
`npm test`)

**Target Platform**: Linux server (backend JAR), browser SPA (frontend)

**Project Type**: web application (backend + frontend, detected from spec)

**Performance Goals**: N/A for this feature — scaffolding only, no load-bearing endpoints yet

**Constraints**: Clean-checkout-to-running in <15 min (SC-001); zero hand-edited generated code
(SC-002, SC-003); backend must fail loudly (not hang) with no DB (Edge Cases)

**Scale/Scope**: One sample contract endpoint (health/status), two project skeletons, one shared
contract file, one docker-compose service (PostgreSQL)

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

This feature only scaffolds runnable skeletons and the contract pipeline — no rate ingestion,
counters, spread logic, or AI insight exist yet, so most principles are not yet exercisable. Gate
evaluated against what *is* in scope:

| Principle | Applicable now? | Status |
|---|---|---|
| I. Monetary Precision | No domain fields yet | N/A — revisit when rate entities land |
| II. Accurate Rate Provenance | No ingestion yet | N/A |
| III. Idempotent Data Collection | No ingestion yet | N/A |
| IV. Multi-Instance Scheduler Safety | No scheduled job body yet | N/A — scheduling dependency only declared |
| V. Concurrency-Safe Usage Counters | No counters yet | N/A |
| VI. Layered Separation of Concerns | Yes — even health check | PASS: controller → service, thin controller |
| VII. Data-Driven Configuration | No lookups yet | N/A |
| VIII. Grounded AI Output | No AI module yet | N/A |
| IX. Environment-Configurable Frontend | Yes | PASS: `environment.ts` + env override for `apiBaseUrl` |
| API contract source of truth (CLAUDE.md) | Yes | PASS: `contracts/openapi.yaml` hand-maintained, both sides generate from it, never reverse |
| API docs via springdoc-openapi | Yes | PASS: springdoc serves Swagger UI from generated interfaces |
| Problem-detail error shape | Partially | PASS: use Spring `ProblemDetail` for any 4xx surfaced now (none required yet, but pattern established) |

No violations requiring Complexity Tracking justification.

## Project Structure

### Documentation (this feature)

```text
specs/001-scaffold-backend-frontend/
├── plan.md              # This file
├── research.md          # Phase 0 output
├── data-model.md         # Phase 1 output
├── quickstart.md         # Phase 1 output
├── contracts/            # Phase 1 output (feature-local copy/reference notes)
└── tasks.md              # Phase 2 output (/speckit-tasks — not created here)
```

### Source Code (repository root)

```text
exchange-rate-manager/
├── contracts/
│   └── openapi.yaml                  # hand-maintained, source of truth
│
├── backend/
│   ├── pom.xml                       # Spring Boot 4.1.1, Java 21, openapi-generator-maven-plugin bound to generate-sources
│   ├── src/main/java/com/exchangerate/manager/
│   │   ├── ExchangeRateManagerApplication.java
│   │   ├── controller/               # thin controllers implementing generated interfaces
│   │   ├── service/                  # business logic (empty/stub this feature)
│   │   └── config/                   # OpenAPI/springdoc, datasource config
│   ├── src/main/resources/
│   │   └── application.yml           # datasource, actuator, springdoc config
│   └── src/test/java/...             # context-load / health smoke test
│
├── frontend/
│   ├── package.json                  # Angular 21, Tailwind CSS 4, openapi-generator-cli, generate:api script
│   ├── angular.json
│   ├── src/app/
│   │   ├── app.ts / app.config.ts / app.routes.ts   # standalone bootstrap, zoneless
│   │   └── api-client/               # generated — never hand-edited
│   └── src/environments/
│       ├── environment.ts            # apiBaseUrl (dev default)
│       └── environment.<target>.ts   # override pattern for other envs
│
└── docker-compose.yml                # postgres:17 service
```

**Structure Decision**: Option 2 (Web application: backend + frontend) from the plan template,
matching the existing monorepo layout already documented in CLAUDE.md. `contracts/` is a third
top-level sibling, not nested under either side, per FR-011.

## Complexity Tracking

*No constitution violations — table not needed.*
