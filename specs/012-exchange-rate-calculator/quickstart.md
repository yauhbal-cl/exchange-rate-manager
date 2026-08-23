# Quickstart: Exchange Rate Calculator View

Validates the feature end-to-end against a running backend. See `data-model.md` for state shapes
and `contracts/ui-contract.md` for the full behavioral contract this exercises.

## Prerequisites

- Local infra up: `docker compose up -d` (PostgreSQL)
- Backend running with at least one ingested rate date: `cd backend && ./mvnw spring-boot:run`
  (or trigger `/exchange/refresh` once if the DB is empty — see `specs/003-fixer-data-collection`)
- Frontend deps installed: `cd frontend && npm install`
- Generated API client up to date with `contracts/openapi.yaml`: `cd frontend && npm run
  generate:api`

## Run

```bash
cd frontend && npm start
```

Navigate to `http://localhost:4200/rate-lookup` (also the default route).

## Scenario 1 — Successful lookup (User Story 1, FR-006)

1. Select `USD` as source, `EUR` as target, leave date blank.
2. Submit.
3. **Expect**: brief loading state, then a result showing `fromCurrency: USD`, `toCurrency: EUR`,
   a `rate` string, a `rateDate`, and both usage counts.

## Scenario 2 — Historical date (User Story 2, FR-007)

1. With a known ingested past date (check via `GET /exchange/trend?from=USD&to=EUR` in Swagger UI
   at `http://localhost:8080/swagger-ui.html` if unsure which dates have data), enter that date.
2. Submit.
3. **Expect**: `rateDate` in the result equals the entered date exactly.

## Scenario 3 — Client-side validation blocks the backend (FR-002–FR-004, SC-002)

1. Select the same currency for both source and target. Submit.
   **Expect**: inline validation message; Network tab shows no request to `/exchange`.
2. Leave a currency unselected. Submit.
   **Expect**: inline validation message; no request.
3. Enter a date after today (if the date input allows typing past its `max`). Submit.
   **Expect**: inline validation message; no request.

## Scenario 4 — Backend error categories (User Story 3, FR-008, SC-003)

1. Select a valid pair with a date guaranteed to have no stored rate (e.g. a very old date).
   Submit. **Expect**: error block, `no-data` category message.
2. Temporarily stop the backend (`Ctrl+C` on the `spring-boot:run` process). Submit again with
   the same inputs. **Expect**: error block, `unreachable` category message. Restart the backend.
3. Re-submit after restart. **Expect**: error clears, result renders (FR-009 retry).

## Scenario 5 — No duplicate concurrent requests (FR-005, SC-004)

1. Open browser DevTools → Network tab.
2. Submit a valid lookup, then click submit again rapidly several times while loading.
3. **Expect**: only one request in flight at a time (submit control is disabled during loading);
   Network tab shows no overlapping `/exchange` calls from the double-clicks.

## Automated check

```bash
cd frontend && npm test -- rate-lookup
```

Covers the validation rules, the request-gating (no call fires until submit), the three error
categories, and stale-response discarding from `data-model.md` / `contracts/ui-contract.md`.
