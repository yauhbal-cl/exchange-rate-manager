# Research: Frontend Foundation

No open `NEEDS CLARIFICATION` markers — all Technical Context fields were resolvable from the
existing scaffold (`frontend/angular.json`, `frontend/package.json`, `frontend/openapitools.json`,
current `app.config.ts`/`app.routes.ts`) plus the feature spec's Assumptions section. The items
below record the choices made and why, per the required Decision/Rationale/Alternatives format.

## Routing strategy: lazy-loaded standalone routes vs. NgModule-per-feature

- **Decision**: Use Angular Router's `loadComponent` (and `loadChildren` for a feature's own
  child routes, if a view later needs sub-routes) against standalone components, one top-level
  route per business view.
- **Rationale**: Angular 21/22 idiom is standalone-first (per `CLAUDE.md` frontend conventions);
  NgModules are legacy. `loadComponent` gives route-level code splitting — each business view
  bundle loads only when navigated to — satisfying FR-006's independence requirement without the
  ceremony of per-feature routing modules.
- **Alternatives considered**: Eager-loaded routes (rejected — no code-splitting benefit and
  couples all three views' bundles together); NgModule-per-feature with `loadChildren` module
  imports (rejected — deprecated pattern for new Angular code).

## App shell vs. current inline `App` component

- **Decision**: Move the current `App` component's inline status-check template out of the way of
  a new `shell/` component that owns the persistent nav + `<router-outlet>`; `App` becomes a thin
  host for `<app-shell>`.
- **Rationale**: FR-001/FR-002 require a persistent nav element surviving view switches; that
  requires the nav to live *outside* the router-outlet, at a fixed point in the tree. The existing
  `App` component currently renders page content directly (a prototype status check), which would
  otherwise have to be reproduced per view.
- **Alternatives considered**: Put nav markup directly in `App` alongside `<router-outlet>`
  (rejected — works but conflates "root bootstrap component" with "shell UI," worse for
  testability and for FR-006's per-view independence once nav grows).

## Not-found handling

- **Decision**: Angular wildcard route (`path: '**'`) at the end of `app.routes.ts`, pointing to a
  `not-found` standalone component rendered inside the shell (nav stays visible).
- **Rationale**: Matches FR-005's "clear not-found state, not a blank page or crash" and keeps the
  shell/nav usable per the Edge Cases section.
- **Alternatives considered**: Server-side 404 handling (N/A — this is a client-side SPA route
  concern; server/static-host fallback config for deep links is an existing deployment concern,
  out of scope here).

## Default view

- **Decision**: Empty-path route (`path: ''`) redirects (`pathMatch: 'full'`) to the rate-lookup
  view's path.
- **Rationale**: FR-004 requires a defined default at the base address; rate lookup is the
  system's primary/first-listed business capability per the spec's view ordering.
- **Alternatives considered**: A dedicated "home/dashboard" landing view (rejected — not in the
  three defined business views per spec Assumptions; would add an undefined fourth view).

## Per-view error isolation on backend failure

- **Decision**: Each feature view manages its own request state (loading/error/data) locally
  (e.g., via `httpResource`/`resource` per Angular idiom) and renders its own error state; the
  shell and nav have no dependency on any view's API call outcome.
- **Rationale**: FR-014 and Edge Cases require the shell/nav to stay usable when the backend is
  unreachable, with only the affected view showing an error. Isolating fetch state per feature
  component, rather than lifting it into the shell, is the only way a shell can be indifferent to
  a view's backend availability.
- **Alternatives considered**: Global HTTP-error interceptor that redirects to an app-wide error
  page (rejected — would take down the shell/nav too, violating FR-014).

## Generated API layer usage

- **Decision**: Keep `contracts/openapi.yaml` → `openapi-generator-cli generate` (already
  configured as `npm run generate:api` in `frontend/package.json`, output to
  `src/app/api-client/`) as the sole source of backend-call code; every feature view injects the
  matching generated `*Service` class instead of using `HttpClient` directly.
- **Rationale**: FR-010–FR-013 mandate generated-only backend access; this is already correctly
  wired (see `StatusService` usage in current `App`) — this feature only needs to continue the
  pattern into the three new views, not introduce new plumbing.
- **Alternatives considered**: None — hand-written HTTP call code is explicitly disallowed by
  FR-013.
