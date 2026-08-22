# Quickstart: Exchange Rate API validation

## Prerequisites

- `docker compose up -d` (PostgreSQL running)
- `cd backend && ./mvnw spring-boot:run` (regenerates server interfaces from
  `contracts/openapi.yaml` on `generate-sources` first)
- At least one successful collection run so `exchange_rates` has data:
  `curl -X POST http://localhost:8080/api/v1/exchange/refresh`

## Scenario 1 — happy path lookup (User Story 1, FR-001–FR-006, FR-011)

```bash
curl -s "http://localhost:8080/api/v1/exchange?from=USD&to=EUR" | jq
```

Expected: HTTP 200, body matches `ExchangeRateResponse` (see
`contracts/exchange-rate-api.yaml`) — `fromCurrency`, `toCurrency`, `rate` (exact decimal string),
`rateDate` (today's collected date), `fromCurrencyUsageCount`/`toCurrencyUsageCount` each ≥ 1.
Independently recompute: `rate == (toRateUsd / fromRateUsd) * ((100 - MAX(toSpread, fromSpread)) / 100)`
using the stored `exchange_rates` rows for that date and the Appendix B spread tiers.

Repeat with an explicit past date that has data for both currencies:

```bash
curl -s "http://localhost:8080/api/v1/exchange?from=USD&to=EUR&date=2026-08-20" | jq
```

## Scenario 2 — error paths (User Story 2, FR-004, FR-007, FR-013)

```bash
# Unknown currency
curl -s -o /dev/null -w "%{http_code}\n" "http://localhost:8080/api/v1/exchange?from=USD&to=ZZZ"
# Expected: 400, application/problem+json body

# Same currency both sides
curl -s -o /dev/null -w "%{http_code}\n" "http://localhost:8080/api/v1/exchange?from=USD&to=USD"
# Expected: 400

# Date with no stored data
curl -s -o /dev/null -w "%{http_code}\n" "http://localhost:8080/api/v1/exchange?from=USD&to=EUR&date=1999-01-01"
# Expected: 404
```

After each error call, confirm usage counters were NOT incremented:

```bash
curl -s http://localhost:8080/api/v1/exchange/usage | jq
```

## Scenario 3 — concurrent usage counting (User Story 3, FR-008–FR-010, SC-003)

```bash
for i in $(seq 1 50); do
  curl -s "http://localhost:8080/api/v1/exchange?from=USD&to=EUR" > /dev/null &
done
wait
curl -s http://localhost:8080/api/v1/exchange/usage | jq '.currencies[] | select(.currencyCode=="USD")'
```

Expected: `queryCount` for `USD` increased by exactly 50 over its value before the loop (run the
usage query once before and once after the loop and diff), with no lost or duplicated increments.
This is also covered by an automated concurrent-increment test — see research.md's Testing
approach.

## Scenario 4 — usage analytics (User Story 4, FR-012)

```bash
curl -s http://localhost:8080/api/v1/exchange/usage | jq
```

Expected: HTTP 200, `{"currencies": [...]}` (or `[]` before any lookups) — each entry has
`currencyCode`, `queryCount`, `lastQueriedAt`; entries reflect exactly the lookups performed in
Scenarios 1 and 3.
