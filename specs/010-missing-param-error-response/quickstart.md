# Quickstart: Validate Missing-Query-Parameter Error Response

## Prerequisites

- `docker compose up -d` (Postgres running — required for the backend to start at all; this
  feature's own code path doesn't touch the DB, but the app context does)
- `cd backend && ./mvnw spring-boot:run`

## Validation steps

1. Call an endpoint with a required query parameter omitted, e.g.:

   ```bash
   curl -i "http://localhost:8080/api/v1/exchange"
   ```

   **Before fix**: `400 Bad Request`, empty body.

   **After fix**: `400 Bad Request` with a JSON body shaped like:

   ```json
   {
     "type": "about:blank",
     "title": "Bad Request",
     "status": 400,
     "detail": "Required parameter 'from' is not present",
     "instance": "/api/v1/exchange"
   }
   ```

2. Confirm the `detail` field names the actual missing parameter (SC-002) by testing at least two
   endpoints/parameters with different required query params, per spec Acceptance Scenario 2.

3. Regression check (User Story 2 / FR-004) — re-trigger each previously-handled error case and
   confirm unchanged response shape/status:

   ```bash
   curl -i "http://localhost:8080/api/v1/exchange?from=XXX&to=USD"      # unknown currency → 400
   curl -i "http://localhost:8080/api/v1/exchange?from=USD&to=USD"      # same currency → 400
   # etc. for invalid date range, trend range too large, rate not found, AI insight unavailable
   ```

## Automated verification

Run the backend test suite, which includes the new `@WebMvcTest` slice test covering this handler
alongside the existing exception-handler coverage:

```bash
cd backend && ./mvnw verify
```

Expected: all tests pass, including the new missing-parameter case and every previously-passing
exception-handler test (no regressions per FR-004).
