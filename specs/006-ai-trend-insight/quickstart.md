# Quickstart: Backend Spring AI Slice (Trend Insight Endpoint)

Validates `GET /exchange/trend/insight` end-to-end: a grounded narrative on real data, an honest
404 on no data, an honest 503 when the AI capability is unreachable, and a 400 when the requested
range is too large to summarize.

## Prerequisites

- This feature's earlier infra planning pass's `tasks.md` (adding the `ollama` service to
  `docker-compose.yml`) has been **implemented** — i.e. `docker compose ps` shows an `ollama`
  service, not just `postgres`. If it hasn't been yet, run that slice's tasks first; this
  quickstart's live steps will fail against a `ConnectionRefused` otherwise.
- `docker compose exec ollama ollama pull llama3.2` has been run once (per that slice's
  quickstart)
- Backend running locally: `cd backend && ./mvnw spring-boot:run`
- Historical rate data exists for at least one currency pair over a recent date range (from
  running the existing Fixer collection scheduler, or a prior slice's seed/manual refresh)

## Scenario 1 — Narrative grounded in real data (User Story 1)

```bash
curl "http://localhost:8080/api/v1/exchange/trend/insight?from=EUR&to=USD&startDate=2026-07-01&endDate=2026-08-01"
```

**Expected**: HTTP 200, JSON body with `fromCurrency`, `toCurrency`, resolved `startDate`/
`endDate`, and a non-empty `narrative` string. Manually confirm the narrative's stated direction
(up/down/stable) and any cited high/low values are consistent with the actual stored rates for
that range — no automated grounding check exists (per spec.md's clarification), so this review is
manual/spot-check, not asserted by the request itself.

Repeat the identical request a second time and confirm the narrative doesn't contradict the first
response's direction/magnitude claims (spec.md Acceptance Scenario 1.2).

## Scenario 2 — AI unavailable (User Story 2)

```bash
docker compose stop ollama
curl -i "http://localhost:8080/api/v1/exchange/trend/insight?from=EUR&to=USD&startDate=2026-07-01&endDate=2026-08-01"
```

**Expected**: HTTP 503 with a `application/problem+json` body whose `detail` clearly states the
insight could not be generated (not a raw stack trace, not a fabricated narrative).

```bash
docker compose start ollama
# wait for the healthcheck to report healthy (see docker-compose.yml's healthcheck interval)
curl "http://localhost:8080/api/v1/exchange/trend/insight?from=EUR&to=USD&startDate=2026-07-01&endDate=2026-08-01"
```

**Expected**: HTTP 200 with a generated narrative — confirms automatic recovery on retry with no
other corrective action (spec.md FR-008, SC-003).

## Scenario 3 — No data for the requested range (User Story 3)

```bash
curl -i "http://localhost:8080/api/v1/exchange/trend/insight?from=EUR&to=USD&startDate=2099-01-01&endDate=2099-01-31"
```

**Expected**: HTTP 404 with a `application/problem+json` body clearly stating no data is
available for the range — not an attempted (and therefore fabricated) narrative.

## Scenario 4 — Range too large to summarize (FR-009 edge case)

```bash
curl -i "http://localhost:8080/api/v1/exchange/trend/insight?from=EUR&to=USD&startDate=2024-01-01&endDate=2026-06-01"
```

**Expected**: HTTP 400 with a `application/problem+json` body clearly stating the range is too
large to summarize (more than ~365 daily observations) — not a partial or dropped-point summary.

## Out of scope for this quickstart

The frontend UI for requesting/displaying this insight is validated by a later slice's own
quickstart once that slice is planned and implemented — see [spec.md](./spec.md) for the
acceptance scenarios that later slice must satisfy.
