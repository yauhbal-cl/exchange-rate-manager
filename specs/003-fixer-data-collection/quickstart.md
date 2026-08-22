# Quickstart: Fixer.io Data Collection

## Prerequisites

- Local Postgres up: `docker compose up -d` (repo root).
- Backend migrated to at least spec 002 (`exchange_rates`, `currency_usage`, `shedlock` tables
  exist — verify with `./mvnw -f backend flyway:info` or by checking `flyway_schema_history`).
- A Fixer.io API key (free tier is sufficient — https://fixer.io/, sign up, copy the access key).
- Set the key as an environment variable before starting the backend:
  ```bash
  export FIXER_API_KEY=your-key-here
  ```

## Run

```bash
cd backend
./mvnw spring-boot:run
```

## Validate: scheduled collection (indirect, without waiting for 00:05 GMT)

1. Confirm the app started with no ShedLock/Flyway errors in the log.
2. Trigger the same code path immediately via the manual endpoint (see below) rather than waiting
   for the schedule — the scheduled job and the manual trigger share the same collection logic
   and lock (research.md §4).
3. Query the database directly to confirm rows landed:
   ```bash
   docker compose exec postgres psql -U exchange_user -d exchange_rate_db \
     -c "SELECT currency_code, rate_to_usd, rate_date FROM exchange_rates ORDER BY currency_code LIMIT 10;"
   ```
   Expect: one row per supported currency, `rate_date` equal to the date Fixer.io's response
   reports (check the API response's `date` field matches), `USD` present with `rate_to_usd =
   1.000000`.

## Validate: manual refresh endpoint

```bash
curl -i -X POST http://localhost:8080/api/v1/exchange/refresh
```

Expect: `200 OK` with a body like `{"currenciesCollected": 168, "rateDate": "2026-08-22"}`.
Re-running the same command immediately must not create duplicate rows (re-check the `SELECT`
above — row count per currency stays at one, `rate_date` unchanged, `rate_to_usd` refreshed in
place if the provider's response value differs).

## Validate: usage counters untouched

```bash
docker compose exec postgres psql -U exchange_user -d exchange_rate_db \
  -c "SELECT count(*) FROM currency_usage;"
```

Expect: `0` (or unchanged from before the refresh call) — collection must never write to
`currency_usage` (FR-009).

## Validate: provider failure handling

Temporarily set an invalid key (`export FIXER_API_KEY=invalid`), restart, call
`POST /exchange/refresh` again.

Expect: `502` with a `ProblemDetail` body, an `ERROR`-level log line naming the failure, and the
`exchange_rates` table unchanged from before the call (re-run the row-count `SELECT` above and
confirm no new/altered rows).

## Validate: concurrent-run rejection

Fire two manual refresh requests back-to-back (e.g., in two terminals within the same second).
Expect: exactly one succeeds with fresh data; the other either waits and then no-ops (ShedLock
lock-still-held case) or is rejected — never two concurrent Fixer.io calls in the logs.

## Contract & generated code

After editing `contracts/openapi.yaml` (already done for this feature — `POST /exchange/refresh`
added), regenerate both sides before implementing the controller:

```bash
cd backend && ./mvnw generate-sources   # regenerates the server interface from the contract
cd frontend && npm run generate:api     # regenerates the typed client (not required until frontend consumes this endpoint)
```
