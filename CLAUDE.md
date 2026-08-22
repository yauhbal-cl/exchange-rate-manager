# CLAUDE.md

Guidance for Claude Code (and any dev) working in this repo.

## Overview

Exchange Rate Management System. Backend REST API + daily scheduled Fixer.io ingestion, Angular
dashboard, AI-generated trend insight via Spring AI + Ollama.

## Monorepo Layout

```
exchange-rate-manager/
├── backend/           Spring Boot 4.1 API + scheduler + AI insight
├── frontend/          Angular 21 SPA
├── contracts/         openapi.yaml — API contract, source of truth for both sides
├── docker-compose.yml PostgreSQL (+ Ollama) for local dev
└── README.md
```

No Nx/Turborepo. Plain sibling folders, plain npm. `contracts/` holds a hand-maintained
`openapi.yaml`; do not hand-write it from generated output — it drives generation, not the reverse.

- **Backend** generates server interfaces/DTOs from `contracts/openapi.yaml` at build time via
  `openapi-generator-maven-plugin`, and implements those interfaces in controllers.
- **Frontend** generates a typed HTTP client from the same file via `openapi-generator-cli` (npm
  script, run manually or in a `prebuild` step) into `frontend/src/app/api-client/`. Never hand-edit
  generated client code.
- Changing an endpoint shape means editing `contracts/openapi.yaml` first, then regenerating both
  sides.

## Tech Stack & Versions

Pin exact versions below — checked against upstream release notes as of 2026-08-22. Re-verify with
context7 or the project's release-notes/changelog page before bumping any of them.

| Layer | Choice | Version | Notes |
|---|---|---|---|
| Language (backend) | Java | 21 (LTS) | |
| Backend framework | Spring Boot | 4.1.1 | runs on Spring Framework 7.0.x |
| Build tool | Maven | 3.9.x | |
| Persistence | Spring Data JPA / Hibernate | managed by Spring Boot BOM | don't pin separately |
| Database | PostgreSQL | 17 | via `docker-compose.yml` for local dev |
| API docs | springdoc-openapi | 3.x (Spring Boot 4 / Framework 7 compatible line) | serves Swagger UI from `contracts/openapi.yaml`-generated interfaces |
| AI integration | Spring AI | 2.0.1 | `spring-ai-starter-model-ollama` |
| Local LLM | Ollama | latest | model: `llama3.2` (`ollama pull llama3.2`) |
| Frontend framework | Angular | 21 | zoneless by default, Vitest instead of Karma |
| CSS framework | Tailwind CSS | 4.x | CSS-first config (`@theme` in global stylesheet), PostCSS via `@tailwindcss/postcss` — no `tailwind.config.js`, no separate `autoprefixer`/`postcss-nesting` plugins |
| Language (frontend) | TypeScript | 5.9+ | required by Angular 21 |
| Node.js | Node | 22 LTS (`^20.19 \|\| ^22.12 \|\| ^24`) | match Angular 21's supported range |
| Package manager | npm | bundled with Node 22 | plain workspaces, no Nx |
| Contract codegen | openapi-generator | latest 7.x CLI, both Maven plugin and npm CLI | shared source: `contracts/openapi.yaml` |
| Boilerplate reduction | Lombok | 1.18.42 (min 1.18.40 for Java 21 annotation processing) | `provided` scope; annotation processor on backend compile classpath |
| Bean mapping | MapStruct | 1.6.3 (stable; 1.7 is beta, not for use) | requires `mapstruct-processor` as an annotation processor; declare after Lombok on the annotation processor path (`lombok-mapstruct-binding` if both run in the same compile) |

## Architecture Decisions

- **Scheduler correctness across instances**: use `ShedLock` (JDBC lock provider against the same
  PostgreSQL DB) so only one instance actually calls Fixer.io per run. The DB-level unique
  constraint on `(currency_code, rate_date)` with an upsert is the real correctness backstop —
  ShedLock just avoids burning Fixer.io's free-tier quota with redundant calls.
- **Rate date**: persist the date the API reports the rate for, not the fetch date. Fixer.io's
  `date` field in the response, not `LocalDate.now()`.
- **Duplicate rates**: upsert on `(currency_code, rate_date)` — `INSERT ... ON CONFLICT` or a JPA
  `saveOrUpdate` keyed on that composite, not a raw insert.
- **Usage counters**: increment atomically at the DB level (single `UPDATE ... SET count = count +
  1` statement, or `@Version`-free atomic SQL) — do not read-modify-write in Java, that's the
  concurrency bug this requirement is testing for.
- **Money/rates**: `BigDecimal` everywhere a rate or monetary value is stored, computed, or
  serialized. Never `double`/`float` for these fields.
- **Spread table (Appendix B)**: model as a lookup keyed by currency code (or "default"), not a
  giant if/else — new entries should be a data change, not a code change.
- **AI insight**: the historical rate rows for the selected period are serialized into the prompt
  context verbatim (dates + values) — the system prompt constrains the model to a short, data-
  grounded commentary. No RAG, no fine-tuning. If Ollama/the model is unreachable, the insight
  endpoint should degrade to a clear error, not a fabricated insight.
- **Test isolation**: never test against a real/shared database. DB-dependent tests (unit or
  integration) must use Testcontainers to spin up an ephemeral instance per run.

## Commands

Fill in once each module is scaffolded (do not guess — verify against the actual `pom.xml` /
`angular.json` / `package.json` once they exist):

- Backend build/test: `cd backend && ./mvnw verify`
- Backend run: `cd backend && ./mvnw spring-boot:run`
- Frontend run: `cd frontend && npm start` (wraps `ng serve`, backend URL from
  `frontend/src/environments/environment.ts` → `apiBaseUrl`, overridden via env for non-local runs)
- Frontend test: `cd frontend && npm test` (Vitest)
- Contract regen (backend): bound to `generate-sources` phase in `backend/pom.xml`
- Contract regen (frontend): `cd frontend && npm run generate:api`
- Local infra: `docker compose up -d` (Postgres; add Ollama service once the AI module lands)

## Conventions

- Backend package root: `com.exchangerate.manager` (adjust if the actual groupId differs once
  `pom.xml` exists — keep this section in sync).
- Layering: controller → service → repository. Keep controllers thin; validation and the spread
  calculation belong in the service layer, not the controller.
- REST error responses: consistent problem-detail shape (Spring's built-in `ProblemDetail`) for 4xx
  cases (unknown currency, no rate for requested date).
- Exception handling: all exceptions handled via `@ControllerAdvice` (`@RestControllerAdvice`), not
  try/catch in controllers/services. Central handler maps exception types to `ProblemDetail`.
- Lombok: use for boilerplate (`@Getter`/`@Setter`/`@RequiredArgsConstructor`/`@Builder`, etc.) on
  backend Java classes.
- MapStruct: use for all DTO ↔ entity mapping; do not hand-write mapping methods. Mapper
  interfaces live alongside the DTOs/entities they map; when a mapper also needs Lombok-generated
  builders/setters, add `lombok-mapstruct-binding` so the two annotation processors cooperate.
- Frontend: standalone components, signals for state, `httpResource`/`resource` for API calls where
  it fits — follow current Angular idioms, not NgModules-era patterns.
- Frontend styling: Tailwind CSS. No hand-rolled component CSS/SCSS where a utility class covers it.

---

**Doc version**: 1.1 (this file has its own revision counter, separate from the project
constitution's version)
