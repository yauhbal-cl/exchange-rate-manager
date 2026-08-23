# Data Model: Frontend Foundation

This feature has no persisted data model (no backend/DB changes). The "entities" from the spec's
Key Entities section are frontend structural/config concepts, captured here as route and
configuration shape rather than database schema.

## Business View

A route entry the shell's nav renders and the router resolves.

| Field | Type | Notes |
|---|---|---|
| `path` | string | route segment, e.g. `rate-lookup`, `usage-analytics`, `ai-insight` |
| `label` | string | nav-display name |
| `component` | lazy-loaded standalone component | via `loadComponent`, one per `features/<view>/` folder |

**Validation rules**:
- Exactly 3 business-view routes exist (FR-001); each maps to a distinct `path`.
- The empty path (`''`) redirects to the default view's `path` (FR-004); a wildcard path (`'**'`)
  maps to the not-found view (FR-005).
- No two business views share a `path`.

**State transitions**: none (static route table, defined once in `app.routes.ts`; the router's
"active route" is Angular Router's own runtime state, not app-owned data).

## Environment Configuration

Not a runtime entity — a build-time file-replacement pair, already implemented.

| Field | Type | Notes |
|---|---|---|
| `apiBaseUrl` | string | backend base URL; consumed via `BASE_PATH` DI token in `app.config.ts` |
| `production` | boolean | selects `environment.production.ts` at build time via `angular.json` `fileReplacements` |

**Validation rules**:
- Exactly one `environment.*.ts` file is active per build (Angular CLI file-replacement
  mechanism); no runtime branching on environment inside application code.
- `environment.ts` (local dev default) MUST have a non-empty `apiBaseUrl` (currently
  `http://localhost:8080`) so FR-009's documented default holds without extra setup.

**State transitions**: none — fixed at build time, not mutated at runtime.

## Backend Contract

External input to this feature, not owned by it.

| Field | Type | Notes |
|---|---|---|
| location | file path | `contracts/openapi.yaml`, repo root, hand-maintained per `CLAUDE.md` |
| consumed via | generated code | `frontend/src/app/api-client/` (npm script `generate:api`) |

**Validation rules**:
- Frontend code MUST NOT hand-edit anything under `api-client/` (FR-012) — regeneration replaces
  it wholesale.
- Every feature view's backend call goes through a generated `*Service` class from `api-client/`
  (FR-013), never a raw `HttpClient` call.

**State transitions**: N/A — this feature consumes the contract as-is (per spec Assumptions); it
does not author or version it.
