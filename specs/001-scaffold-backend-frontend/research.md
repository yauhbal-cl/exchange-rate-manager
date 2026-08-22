# Phase 0 Research: Scaffold Backend and Frontend

All Technical Context fields were resolvable from `CLAUDE.md` (pinned stack table) and the
constitution — no NEEDS CLARIFICATION markers remain.

## Backend generation: openapi-generator-maven-plugin

- **Decision**: Bind `openapi-generator-maven-plugin` to the `generate-sources` phase, generator
  `spring`, `interfaceOnly=true`, input `../contracts/openapi.yaml`, output under
  `target/generated-sources/openapi`. Controllers `implements` the generated interface.
- **Rationale**: `interfaceOnly` keeps generated code to interfaces + DTOs only — hand-written
  controllers stay in `src/main/java` and are never overwritten by regeneration. Matches
  CLAUDE.md's "implements those interfaces in controllers."
- **Alternatives considered**: Generating full controller stubs (rejected — collides with
  hand-written controller code on every build); contract-first via springdoc annotations instead
  of a generator (rejected — CLAUDE.md mandates `contracts/openapi.yaml` as the one source of
  truth, not annotation-derived).

## Frontend generation: openapi-generator-cli

- **Decision**: `npm run generate:api` invokes `openapi-generator-cli generate -i
  ../contracts/openapi.yaml -g typescript-angular -o src/app/api-client`, run manually / as an
  explicit `prebuild` step (not silently on every `ng serve`, so contract errors surface as a
  visible, intentional step per Edge Cases).
- **Rationale**: `typescript-angular` generator produces injectable Angular services + typed
  models consistent with HttpClient usage; matches CLAUDE.md's explicit instruction.
- **Alternatives considered**: `typescript-fetch` (rejected — doesn't integrate with Angular DI);
  auto-regeneration via a file watcher (rejected — adds tooling complexity not required by spec).

## Health/status check for User Story 1

- **Decision**: Spring Boot Actuator `/actuator/health` with the DB health indicator enabled
  (default when `spring-boot-starter-data-jpa` + a DataSource are present); expose a project
  status endpoint documented in `contracts/openapi.yaml` (e.g. `GET /api/v1/status`) implemented
  by a thin controller/service that reports DB connectivity, satisfying FR-008 and Acceptance
  Scenario 1.2 without hand-rolling health-check logic.
- **Rationale**: Actuator's DB indicator already probes the datasource; reusing it avoids
  duplicating connectivity-check logic, and a documented contract endpoint keeps the "verify
  health" surface part of the single source of truth rather than an undocumented side-channel.
- **Alternatives considered**: A hand-written `SELECT 1` health check (rejected — Actuator already
  does this correctly and is idiomatic Spring Boot); exposing only `/actuator/health` with nothing
  in the OpenAPI contract (rejected — spec's sample contract endpoint requirement (Assumptions)
  is best satisfied by a documented, generator-covered endpoint).

## Frontend environment configuration

- **Decision**: `apiBaseUrl` in `src/environments/environment.ts` (dev default,
  e.g. `http://localhost:8080`), overridden per Angular's `fileReplacements` build configurations
  for other targets; no code changes needed to point at a different backend (FR-009, Constitution
  Principle IX).
- **Rationale**: Standard Angular idiom, already named in CLAUDE.md's Commands section.
- **Alternatives considered**: Runtime-fetched `config.json` (rejected — unnecessary complexity
  for a scaffolding feature; can be revisited if runtime reconfiguration without rebuild becomes a
  real requirement later).

## Tailwind CSS version and integration

- **Decision**: Tailwind CSS 4.x, wired via the single `@tailwindcss/postcss` PostCSS plugin.
  CSS-first configuration (`@import "tailwindcss";` + `@theme` block in the global stylesheet) —
  no `tailwind.config.js`. Drop separate `autoprefixer` and `tailwindcss/nesting` PostCSS plugins;
  v4 handles vendor prefixing and native CSS nesting internally, and stacking them alongside
  `@tailwindcss/postcss` is redundant/incorrect for v4.
- **Rationale**: User directive to use Tailwind v4 for this feature; v4's PostCSS pipeline
  replaced the v3 `tailwindcss` + `autoprefixer` + `tailwindcss/nesting` trio with one plugin, so
  keeping the v3-era plugin list alongside a v4 dependency is a broken/inconsistent setup.
- **Alternatives considered**: Tailwind v3 (rejected — superseded, no longer the decision here);
  keeping v3-style PostCSS config with a v4 package (rejected — unsupported combination).

## Local infrastructure

- **Decision**: `docker-compose.yml` at repo root with a single `postgres:17` service, exposing
  the standard port, with a named volume for persistence and env-configurable
  credentials/db-name matching `application.yml`'s datasource config.
- **Rationale**: FR-010 requires single-command local infra startup; matches CLAUDE.md's `docker
  compose up -d` command.
- **Alternatives considered**: Testcontainers-only setup (rejected — spec requires a
  developer-startable local instance, not just test-scoped infra); adding Ollama service now
  (deferred — AI module is out of scope per spec Assumptions, add when that feature lands).
