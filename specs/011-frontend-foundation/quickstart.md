# Quickstart: Frontend Foundation

## Prerequisites

- Node 22 LTS, npm (bundled)
- Backend running locally on the port matching `frontend/src/environments/environment.ts`'s
  `apiBaseUrl` (default `http://localhost:8080`) — or leave it stopped to validate the
  unreachable-backend edge case below
- `cd frontend && npm install` (already scaffolded; run if `node_modules/` is missing)

## Scenario 1 — Navigate between the three business views (User Story 1)

1. `cd frontend && npm start`
2. Open the app at its base address. **Expected**: default view (`rate-lookup`) renders, nav shows
   it as active — [SC-001](../spec.md).
3. Click each of the other two nav entries in turn. **Expected**: view switches without a full
   page reload (check dev tools Network tab shows no full document reload), nav highlights the
   newly active entry each time — validates [FR-001](../spec.md)/[FR-002](../spec.md), route
   table in [contracts/routes.md](./contracts/routes.md).
4. Navigate directly to `/usage-analytics` (type the address, or refresh on it). **Expected**:
   that view loads directly, not the default — [FR-003](../spec.md).
5. Navigate to a nonsense address, e.g. `/nope`. **Expected**: clear not-found state, nav still
   visible — [FR-005](../spec.md).

## Scenario 2 — Retarget the backend via configuration only (User Story 2)

1. Stop the app. Edit `frontend/src/environments/environment.ts`'s `apiBaseUrl` to point at a
   different backend instance (e.g. a shared dev backend's URL).
2. `git diff --stat` — confirm only `environment.ts` changed, no other source files.
3. `npm start` again. **Expected**: API calls (visible in Network tab) go to the newly configured
   address — [SC-002](../spec.md)/[FR-007](../spec.md)/[FR-008](../spec.md).
4. Revert the edit (or confirm the unmodified file already defaults to
   `http://localhost:8080`) — [FR-009](../spec.md).

## Scenario 3 — Regenerate the frontend API layer from the backend contract (User Story 3)

1. `cd frontend && npm run generate:api`
2. **Expected**: command completes, `src/app/api-client/` is fully rewritten from
   `contracts/openapi.yaml`, with no manual follow-up edits needed to compile —
   [SC-003](../spec.md)/[FR-010](../spec.md)/[FR-011](../spec.md).
3. `git diff --stat src/app/api-client/` — confirm changes are generator output only (no
   hand-authored diffs surviving inside that directory) — [FR-012](../spec.md).
4. `grep -rn "HttpClient" src/app --include=*.ts | grep -v api-client` — expect no matches outside
   `api-client/`, confirming every backend call in application code goes through the generated
   layer — [SC-004](../spec.md)/[FR-013](../spec.md).

## Edge case — backend unreachable

1. Stop the backend (or point `apiBaseUrl` at an address nothing is listening on).
2. Load the app, click through all three nav entries. **Expected**: shell and nav stay usable and
   responsive the whole time; only each view's own data section shows a clear
   error/unavailable message — [FR-014](../spec.md)/[SC-005](../spec.md).

## Automated checks

- `cd frontend && npm test` — Vitest unit tests for shell nav-active-state logic and route
  resolution (default redirect, wildcard) should be added alongside the implementation and pass.
