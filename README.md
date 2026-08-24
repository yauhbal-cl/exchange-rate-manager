# Exchange Rate Management System

Full-stack exchange-rate collection, calculation, historical analysis, usage analytics, and
AI-assisted trend commentary built for the Marcura Full Stack Developer technical assessment.

## Contents

- [Quick start](#quick-start)
- [Configuration](#configuration)
- [Using the application](#using-the-application)
- [Architecture](#architecture)
- [Business rules](#business-rules)
- [API](#api)
- [Testing](#testing)
- [AI Workflow](#ai-workflow)
- [Assessment compliance](#assessment-compliance)
- [Assumptions](#assumptions)
- [Known trade-offs](#known-trade-offs)
- [Pre-submission checklist](#pre-submission-checklist)

## Quick start

### Prerequisites

| Tool | Required version or role |
|---|---|
| Java | 21 (the brief requires 17+) |
| Node.js | A version supported by Angular 21; Node 22 LTS is recommended |
| npm | 11.x |
| Docker + Docker Compose | PostgreSQL, Ollama, and Testcontainers-backed backend tests |
| Fixer.io key | Free subscription is sufficient |

### Option A: everything in Docker Compose

`docker-compose.yml` also builds and runs the backend and frontend, so nobody needs a local
Java/Node toolchain just to try the app.

```bash
export FIXER_API_KEY=replace-with-your-fixer-access-key
docker compose up -d --build
docker compose ps
docker compose logs ollama-pull
```

The backend image is built from `backend/Dockerfile` and the frontend from `frontend/Dockerfile`
(build context is the repo root, since both need `contracts/openapi.yaml` for codegen). The
frontend container is nginx serving the built Angular app and reverse-proxying `/api/v1/*` to the
`backend` service, so no source changes are needed between local and containerized runs. Open
<http://localhost:4200>. The one-shot `ollama-pull` container downloads `llama3.2`; wait for a
successful pull before testing AI insights.

Docker Compose activates the Spring `dev` profile. On backend startup, Flyway therefore loads the
repeatable development seeds, including Fixer historical rates for the 14 calendar days from
2026-08-11 through 2026-08-24. The rate seed uses an upsert, so it is safe with both a fresh and an
existing PostgreSQL volume.

### Option B: backend/frontend on the host

Useful for local development with hot reload. PostgreSQL and Ollama still run in Docker.

### 1. Configure the Fixer key

The backend deliberately has no default API key and will not start without one.

```bash
export FIXER_API_KEY=replace-with-your-fixer-access-key
```

### 2. Start PostgreSQL and Ollama

From the repository root:

```bash
docker compose up -d postgres ollama ollama-pull
docker compose ps
docker compose logs ollama-pull
```

The one-shot `ollama-pull` container downloads `llama3.2`. Wait for a successful pull before
testing AI insights. PostgreSQL is exposed on port `5432`; Ollama is exposed on `11434`.

### 3. Start the backend

```bash
cd backend
./mvnw spring-boot:run
```

Confirm it is ready:

```bash
curl http://localhost:8080/api/v1/status
```

The database is empty on a clean checkout. The scheduled collection runs daily at 00:05 GMT; to
populate it immediately, invoke the optional manual refresh:

```bash
curl -X POST http://localhost:8080/api/v1/exchange/refresh
```

This fetches Fixer data, uses the provider-reported rate date, and does not change usage counters.

### 4. Start the frontend

In a separate terminal from the repository root:

```bash
cd frontend
npm ci
npm run generate:api
npm start
```

Open <http://localhost:4200>. Development configuration already targets
`http://localhost:8080/api/v1`, so local review requires no source changes.

### Service URLs

| Service | URL |
|---|---|
| Angular SPA | <http://localhost:4200> |
| Backend status | <http://localhost:8080/api/v1/status> |
| Swagger UI | <http://localhost:8080/swagger-ui.html> |
| OpenAPI JSON | <http://localhost:8080/v3/api-docs> |
| Ollama | <http://localhost:11434> |

## Configuration

### Backend environment variables

| Variable | Required | Default | Purpose |
|---|---:|---|---|
| `FIXER_API_KEY` | Yes | None | Authenticates Fixer `/latest` requests |
| `FIXER_BASE_URL` | No | `https://data.fixer.io/api` | Fixer-compatible endpoint |
| `FIXER_CONNECT_TIMEOUT_SECONDS` | No | `3` | Fixer connection timeout |
| `FIXER_READ_TIMEOUT_SECONDS` | No | `10` | Fixer response timeout |
| `FIXER_RETRY_MAX_ATTEMPTS` | No | `3` | Total attempts for transport, 408, 429, and 5xx failures |
| `FIXER_RETRY_INITIAL_DELAY_MILLIS` | No | `500` | Initial retry delay |
| `FIXER_RETRY_MULTIPLIER` | No | `2.0` | Retry backoff multiplier |
| `FIXER_RETRY_MAX_DELAY_MILLIS` | No | `1000` | Maximum retry delay |
| `POSTGRES_DB` | No | `exchange_rate_db` | Database name |
| `POSTGRES_USER` | No | `exchange_user` | Database user |
| `POSTGRES_PASSWORD` | No | `exchange_password` | Database password |
| `OLLAMA_BASE_URL` | No | `http://localhost:11434` | Spring AI Ollama endpoint |
| `AI_INSIGHT_CONNECT_TIMEOUT_SECONDS` | No | `5` | Ollama connection timeout |
| `AI_INSIGHT_TIMEOUT_SECONDS` | No | `30` | Ollama read timeout |
| `CORS_ALLOWED_ORIGINS` | No | `http://localhost:4200` | Allowed Angular origin |

The model name is `llama3.2` in `backend/src/main/resources/application.yml`. To use a different
model, update that property and ensure the model is present in Ollama.

### Frontend API base

- `ng serve` uses `frontend/src/environments/environment.ts` and calls
  `http://localhost:8080/api/v1`.
- Production builds replace it with `environment.production.ts`


Angular environment files are compile-time configuration, not runtime process environment
variables. Local `ng serve` works without changes, as required by the review workflow, but a
runtime-injected API URL is not implemented.

## Using the application

The SPA has three lazy-loaded, navigable views:

1. **Rate calculator** (`/rate-lookup`) selects source and target currencies, accepts an optional
   date, validates the pair/date, and shows loading, success, no-data, invalid-request, and service
   failure states.
2. **Historical rates** (`/historical-rates`) selects a pair and date range, and shows the raw
   cross-rate series in a Chart.js line chart and table. It also requests and displays a separate
   AI narrative with its own loading/error state.
3. **Usage analytics** (`/usage-analytics`) ranks lifetime currency usage and shows activity
   patterns over selectable 7-, 30-, and 90-day windows.

Example API walkthrough after the first refresh:

```bash
# Latest spread-adjusted EUR/PLN lookup
curl 'http://localhost:8080/api/v1/exchange?from=EUR&to=PLN'

# A lookup for an exact provider rate date
curl 'http://localhost:8080/api/v1/exchange?from=EUR&to=PLN&date=2026-08-24'

# Raw historical cross-rates
curl 'http://localhost:8080/api/v1/exchange/trend?from=EUR&to=PLN&startDate=2026-08-01&endDate=2026-08-24'

# AI commentary grounded in the same raw points
curl 'http://localhost:8080/api/v1/exchange/trend/insight?from=EUR&to=PLN&startDate=2026-08-01&endDate=2026-08-24'

# Usage totals plus query timestamps
curl 'http://localhost:8080/api/v1/exchange/usage?limit=10&recentDays=30'
```

Replace example dates with dates actually returned by the refresh endpoint.

## Architecture

```text
Angular 21 SPA
  ├─ calculator ───────────────┐
  ├─ historical chart/table ──┼─ generated TypeScript client ─┐
  └─ usage dashboard ─────────┘                               │
                                                              ▼
contracts/openapi.yaml ── generates ──> Spring API interfaces/DTOs
                                      │
                                      ▼
                             Spring Boot 4.1.1 / Java 21
                              controller → service → repository
                                  │          │          │
                                  │          │          └─ PostgreSQL 17
                                  │          ├─ Fixer client
                                  │          └─ Spring AI ChatClient → Ollama llama3.2
                                  └─ ProblemDetail error mapping
```

### Backend responsibilities

- `RateCollectionScheduler` triggers at `0 5 0 * * *` in GMT.
- ShedLock uses PostgreSQL and a ten-minute maximum lock to prevent duplicate multi-instance
  collection; the unique database key and upsert remain the correctness backstop.
- `RateCollectionService` converts Fixer's EUR-based response into the internally stored
  USD-normalized values and synthesizes the EUR self-rate.
- `ExchangeRateService` calculates spread-adjusted point lookups, raw historical cross-rates, and
  transactional usage updates.
- PostgreSQL-native upserts atomically increment counters and avoid Java read-modify-write races.
- Query events record a timestamp for each currency in every successful lookup. A locked,
  batch-oriented daily purge applies a 365-day event-retention policy.
- `TrendInsightService` serializes actual date/value rows into a Spring AI `ChatClient` request.
  AI failure returns HTTP 503; it never fabricates a local fallback narrative.

### Contract-driven API

`contracts/openapi.yaml` is the API source of truth. Maven generates backend interfaces and DTOs;
the npm generator produces the Angular client. Generated code should not be edited manually.

After a contract change:

```bash
cd backend && ./mvnw generate-sources
cd ../frontend && npm run generate:api
```

### Persistence model

| Table | Important fields | Correctness rule |
|---|---|---|
| `exchange_rates` | currency code, decimal rate, provider rate date | Unique `(currency_code, rate_date)` and atomic upsert |
| `currency_usage` | currency code, lifetime query count, last query timestamp | Unique currency; atomic insert-or-increment |
| `currency_query_event` | currency code, query timestamp | Two rows per successful pair lookup; indexed history |
| `shedlock` | name and lock timestamps | Shared multi-instance scheduler lock |

Flyway owns all four schema migrations. JPA validates the schema on startup rather than creating it.

## Business rules

### Spread-adjusted calculation

For a successful point lookup:

```text
adjustedRate = (toRateToUSD / fromRateToUSD)
             × ((100 − max(toSpread, fromSpread)) / 100)
```

All persisted values, spreads, and backend calculations use `BigDecimal`; decimal API values are
transported as strings to avoid binary floating-point loss.

| Currency group | Spread |
|---|---:|
| Provider base currency (`EUR`) | 0.00% |
| JPY, HKD, KRW | 3.25% |
| MYR, INR, MXN | 4.50% |
| RUB, CNY, ZAR | 6.00% |
| All others | 2.75% |

The larger spread in the pair is applied. Spread policy is externalized under `exchange-rates` in
`application.yml`, so group/default changes do not require calculation-code changes.

### Date and usage semantics

- A supplied lookup date must have stored rows for both currencies; otherwise the API returns 404.
- An omitted date resolves to the latest date shared by both currencies, not independently latest
  rows and not the system date.
- Only successful calculator lookups increment counters and append events.
- Both currencies increment once per successful request. Currency-code lock ordering plus atomic
  SQL prevents lost updates and opposite-direction deadlocks.
- Historical and insight reads do not increment usage.
- Historical results are raw, unadjusted cross-rates as required for the table/chart.

## API

All application endpoints use the `/api/v1` prefix.

| Method | Path | Purpose | Main responses |
|---|---|---|---|
| `GET` | `/status` | Service/database status | 200, 503 |
| `POST` | `/exchange/refresh` | Optional manual Fixer collection | 200, 409, 502 |
| `GET` | `/exchange` | Spread-adjusted point lookup | 200, 400, 404 |
| `GET` | `/exchange/trend` | Raw historical pair series | 200, 400 |
| `GET` | `/exchange/trend/insight` | LLM-generated trend narrative | 200, 400, 404, 503 |
| `GET` | `/exchange/usage` | Counts, last-query times, and timestamp history | 200, 400 |


Validation and domain failures are returned as RFC Problem Details. Malformed or missing query
parameters, unknown/same currencies, invalid ranges, and non-positive analytics filters return
400. Missing requested rate data returns 404. An unavailable LLM returns 503. Provider collection
failure returns 502.

## Testing

### Commands

```bash
# Complete backend unit + Testcontainers integration suite
cd backend
./mvnw verify

# Frontend Vitest suite
cd frontend
npm test -- --watch=false

# Production frontend build
npm run build
```

Backend `verify` requires access to a working Docker daemon because repository, controller, and
concurrency integration tests use an ephemeral PostgreSQL Testcontainer.

### Coverage evidence

- Backend contains 22 test classes, including spread lookup/calculation, Fixer failures and
  duplicates, scheduler invocation, manual refresh, prompt-context capture, controller HTTP
  semantics, repository upserts, analytics history, and a 20-request opposite-direction
  concurrency test.
- Frontend contains 11 spec files covering routes, shell/navigation, calculator validation and
  request states, currency controls, date presets, metrics, chart configuration, historical view,
  analytics calculations, and analytics UI states.
- Integration tests are wired through Maven Failsafe for `*IT.java`; database-dependent tests use
  Testcontainers rather than a shared database.

### Verification performed during this audit

| Check | Result on 2026-08-24 |
|---|---|
| `npm test -- --watch=false` | **PASS** — 11 files, 174 tests |
| `npm run build` | **PASS** — production bundle generated |
| Backend compile/generate-sources | **PASS** — 64 source files compiled |
| `./mvnw test` | **Environment-blocked** — this restricted runner denied Docker socket access and JVM agent self-attachment; 104 tests were discovered, but the run is not a valid product-result signal |
| `./mvnw verify` in repository history | Commit `ce6cda2` records a successful complete-suite verification; rerun it on the submission machine |

Do not present the backend suite as freshly verified until `./mvnw verify` completes on a normal
Docker-enabled host.

## AI Workflow

### Tool and configuration

Claude Code was used as an agentic development tool across planning, implementation, testing,
review, and documentation. `CLAUDE.md` supplies repository-specific context: monorepo layout,
fixed technology versions, contract-first workflow, architectural decisions, commands, layering,
numeric precision rules, concurrency expectations, generated-code boundaries, and test-isolation
rules. `.claude/settings.json` records the enabled frontend-design plugin.

AI was used as a workflow layer rather than only autocomplete:

- `PROJECT_PLAN.md` was committed before the first implementation commit and decomposed the work
  into contract, persistence, collection, API, analytics, AI, frontend, verification, and final
  review phases.
- Feature folders under `specs/` retain AI-assisted specifications, plans, research, data models,
  contracts, checklists, and task breakdowns for coherent multi-file work.
- Tests were developed alongside features, including generated frontend suites, integration
  coverage, concurrency coverage, and regression tests.
- Commit history is deliberately traceable: 287 of the 337 commits reachable from the audited
  `fix/backend` HEAD use the `[AI]` prefix.
- Human review/refinement commits are left visible rather than relabelled as AI work.

### Example of disagreement and correction

The agent initially applied the calculator spread to the historical trend series. On review, that
was rejected because Section 5.2 explicitly asks the table to show **raw exchange rates**. Commit
`b4ee6e2` (`fix: return raw rates for historical analytics`) removed `SpreadLookup` from historical
calculation, changed the service to return only the raw cross-rate ratio, corrected the OpenAPI
descriptions, regenerated the frontend client, and updated tests. This is a concrete example of
checking agent output against the source brief and overriding it instead of accepting a plausible
but incorrect implementation.

Other unprefixed review commits repaired backend layer boundaries (`0c1a714`), added explicit
network timeout/retry policy (`f645706`), and corrected malformed-query Problem Details
(`350894a`).


## Assumptions

- Fixer's configured account returns `EUR` as its base currency. Collection rejects a different or
  missing base instead of silently persisting inconsistent data.
- Fixer's `/latest` response contains a positive USD rate used for internal normalization.
- Currency identifiers are uppercase ISO-style three-letter codes; unknown codes are rejected.
- A lookup needs both currency rows on one common date. Missing dates are not interpolated.
- Historical ranges are inclusive. Omitted bounds resolve to the most recent 30-day window.
- AI insight ranges are limited to 365 days to bound prompt size and latency.
- Query-event detail is retained for 365 days; lifetime aggregate counters remain available.
- AI output is descriptive only. The raw data remains visible in chart/table form and is the source
  of truth.
- Local review uses the Compose PostgreSQL/Ollama services and the development frontend config.

## Known trade-offs

- PostgreSQL-specific upsert, interval, and `ctid` SQL improve concurrency and batching but reduce
  database portability.
- `NUMERIC(19,6)` bounds stored precision to six fractional digits; calculations use a 20-digit
  `MathContext` after loading.
- ShedLock is a single shared-database coordination mechanism. A database outage prevents
  collection, which is preferable to uncontrolled duplicate provider requests.
- Usage-event analytics can return a large timestamp array for heavily queried currencies. The
  time window bounds it, but pagination/aggregation would be advisable at larger scale.
- Fixer and AI retry/timeout policy favors bounded response time over indefinite recovery.
- Historical and insight endpoints currently perform separate reads; data arriving between those
  calls could produce a small snapshot difference.
- The frontend converts decimal strings to JavaScript numbers for Chart.js rendering. Exact strings
  remain in the table/API, while chart precision is display-oriented.
- Docker images use floating `latest` tags for Ollama. Pin digests for reproducible production
  deployments.
- There is no end-to-end browser test or automated accessibility audit; component and integration
  tests cover the principal states.
- A clean database needs a scheduled or manual refresh before calculator/history views contain
  rate data.


