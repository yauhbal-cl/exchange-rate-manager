# Implementation Plan: Frontend Foundation

**Branch**: `011-frontend-foundation` | **Date**: 2026-08-23 | **Spec**: [spec.md](./spec.md)

**Input**: Feature specification from `/specs/011-frontend-foundation/spec.md`

**Note**: This template is filled in by the `/speckit-plan` command; its definition describes the execution workflow.

## Summary

Turn the existing single-screen Angular scaffold into a real app shell: persistent nav across
three lazy-loaded, independently-owned business views (rate lookup, usage analytics, AI insight),
router-driven addressing with a default route and a not-found route, per-view error isolation on
backend failure, and confirm the already-wired environment-based `apiBaseUrl` + generated
`api-client` (from `contracts/openapi.yaml`) are the only paths used for backend calls.

## Technical Context

**Language/Version**: TypeScript ~6.0.2 (repo's installed version; CLAUDE.md pins TypeScript
5.9+/Angular 21 — this workspace was scaffolded with Angular ^22.1.0/TypeScript ~6.0.2, a drift
from the doc, not from this feature; not in scope to downgrade)

**Primary Dependencies**: Angular 22.1 (standalone components, `provideRouter`, zoneless-eligible),
`@angular/common/http`, generated `api-client` (openapi-generator `typescript-angular`), Tailwind
CSS 4.x

**Storage**: N/A (frontend-only; consumes backend REST API)

**Testing**: Vitest (`ng test`, already configured via `@angular/build`)

**Target Platform**: Browser SPA, served via `ng serve` locally / static build in other
environments

**Project Type**: Web application — frontend half of the existing `backend/` + `frontend/`
monorepo (Option 2 structure)

**Performance Goals**: Not a performance-sensitive feature; standard SPA route lazy-loading is
sufficient (no explicit budget beyond existing `angular.json` bundle budgets)

**Constraints**: No source-code edit required to retarget backend base URL (Constitution IX);
generated `api-client/` must never be hand-edited (regeneration overwrites it wholesale)

**Scale/Scope**: 3 business views + shell + not-found view; no auth; no additional environments
pipeline beyond `environment.ts` / `environment.production.ts` file replacement already in
`angular.json`

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

- **IX. Environment-Configurable Frontend** — PASS. `environment.ts` /
  `environment.production.ts` already carry `apiBaseUrl`, wired into `BASE_PATH` in
  `app.config.ts`; this feature adds no hard-coded backend URL anywhere.
- **VI. Layered Separation of Concerns** (frontend analogue: generated API layer only, no ad hoc
  HTTP) — PASS. All backend calls go through generated `api-client` services (`StatusService`
  today; view components will inject the matching generated service). No `HttpClient` calls
  written by hand outside `api-client/`.
- Other Core Principles (I–V, VII, VIII, X) are backend/data concerns not touched by this
  frontend-shell feature — N/A.
- No violations requiring Complexity Tracking.

## Project Structure

### Documentation (this feature)

```text
specs/[###-feature]/
├── plan.md              # This file (/speckit-plan command output)
├── research.md          # Phase 0 output (/speckit-plan command)
├── data-model.md        # Phase 1 output (/speckit-plan command)
├── quickstart.md        # Phase 1 output (/speckit-plan command)
├── contracts/           # Phase 1 output (/speckit-plan command)
└── tasks.md             # Phase 2 output (/speckit-tasks command - NOT created by /speckit-plan)
```

### Source Code (repository root)

```text
frontend/
├── src/
│   ├── app/
│   │   ├── api-client/            # generated, untouched by this feature (openapi-generator output)
│   │   ├── shell/                 # app shell: nav + router-outlet, replaces today's inline App template
│   │   │   ├── shell.ts
│   │   │   ├── shell.html
│   │   │   └── shell.css
│   │   ├── not-found/
│   │   │   └── not-found.ts       # wildcard route target
│   │   ├── features/
│   │   │   ├── rate-lookup/       # business view 1 — own routes/components, lazy-loaded
│   │   │   ├── usage-analytics/   # business view 2 — own routes/components, lazy-loaded
│   │   │   └── ai-insight/        # business view 3 — own routes/components, lazy-loaded
│   │   ├── app.ts                 # root component: hosts <app-shell>
│   │   ├── app.config.ts          # existing providers (BASE_PATH from environment, router, http)
│   │   └── app.routes.ts          # top-level routes: default redirect, 3 lazy feature routes, wildcard
│   └── environments/
│       ├── environment.ts             # existing local default (apiBaseUrl: http://localhost:8080)
│       └── environment.production.ts  # existing (apiBaseUrl: /api)
└── openapitools.json               # existing generator config (contracts/openapi.yaml -> api-client)

tests/ (frontend, via Vitest under src/**/*.spec.ts, existing convention)
```

**Structure Decision**: Existing Option 2 web-application layout (`backend/` + `frontend/`
siblings) — no new top-level directories. Within `frontend/src/app/`, this feature introduces a
`shell/` (nav + outlet, replacing the current inline status-check template in `app.ts`), a
`not-found/` view, and a `features/` directory holding one independently maintainable folder per
business view (FR-006), each lazy-loaded from `app.routes.ts` via `loadComponent`/
`loadChildren`. `api-client/`, `environment.ts`, and `app.config.ts`'s `BASE_PATH` wiring already
satisfy FR-007–FR-011 and are left as-is.

## Constitution Check (post-design)

*Re-checked after Phase 1 design (data-model.md, contracts/routes.md, quickstart.md).*

- **IX. Environment-Configurable Frontend** — PASS, unchanged. Design introduces no new
  environment-branching code; `data-model.md`'s Environment Configuration section confirms the
  existing file-replacement mechanism is the only source of `apiBaseUrl`.
- **VI. Layered Separation of Concerns (generated-API-only)** — PASS, unchanged.
  `contracts/routes.md` and `quickstart.md` Scenario 3 both encode the FR-013 check
  (`grep -rn "HttpClient"` outside `api-client/` must be empty) as a verifiable step, not just an
  intention.
- No violations. Complexity Tracking table below intentionally left empty.

## Complexity Tracking

> **Fill ONLY if Constitution Check has violations that must be justified**

*No violations — table intentionally empty.*
