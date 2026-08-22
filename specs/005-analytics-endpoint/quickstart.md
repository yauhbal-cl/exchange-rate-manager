# Quickstart: Analytics Endpoint

## Prerequisites

- `docker compose up -d` (PostgreSQL running)
- Backend built with rate data already collected for at least two currencies across several
  consecutive dates (run `POST /api/v1/exchange/refresh` once or more, or seed via the daily
  scheduler)

## Run

```
cd backend && ./mvnw spring-boot:run
```

## Validate: historical trend (US1)

```
# Default 30-day window
curl -s 'http://localhost:8080/api/v1/exchange/trend?from=EUR&to=USD' | jq

# Explicit range
curl -s 'http://localhost:8080/api/v1/exchange/trend?from=EUR&to=USD&startDate=2026-08-01&endDate=2026-08-22' | jq

# Invalid range (startDate after endDate) -> 400 ProblemDetail
curl -s -i 'http://localhost:8080/api/v1/exchange/trend?from=EUR&to=USD&startDate=2026-08-22&endDate=2026-08-01'

# Unknown currency -> 400 ProblemDetail
curl -s -i 'http://localhost:8080/api/v1/exchange/trend?from=EUR&to=ZZZ'
```

Expected: `points` array ordered oldest→newest, one entry per date both currencies have data
for; dates missing data are simply absent (never null/interpolated). See
[data-model.md](data-model.md) for the Rate Trend Point shape and
[contracts/analytics-endpoints.yaml](contracts/analytics-endpoints.yaml) for the full schema.

Verify no side effects: compare `GET /exchange/usage` counts for EUR/USD before and after —
must be unchanged (FR-006, SC-005).

## Validate: ranked usage (US2)

```
curl -s 'http://localhost:8080/api/v1/exchange/usage?limit=3' | jq
```

Expected: at most 3 entries, `queryCount` descending, ties broken by `currencyCode` ascending
(FR-008). Omitting `limit` returns all currencies with usage data, same ordering.

## Validate: recency-filtered usage (US3)

```
curl -s 'http://localhost:8080/api/v1/exchange/usage?recentDays=7' | jq
```

Expected: only currencies whose `lastQueriedAt` is within the last 7 days; never-queried
currencies excluded.

## Validate: parameter rejection

```
curl -s -i 'http://localhost:8080/api/v1/exchange/usage?limit=0'
curl -s -i 'http://localhost:8080/api/v1/exchange/usage?recentDays=-1'
```

Expected: `400` with the platform's standard `ProblemDetail` shape for both (FR-010, FR-011).
