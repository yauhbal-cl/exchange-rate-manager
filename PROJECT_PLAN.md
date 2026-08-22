# PROJECT_PLAN.md

Ordered steps for Exchange Rate Management System (see TASK.md, CLAUDE.md). Mark `[x]` when done.

## 0. Repo & Contract Setup

- [ ] Scaffold `backend/` (Spring Boot 4.1.1, Java 21, Maven) and `frontend/` (Angular 21) sibling folders
- [ ] Write `contracts/openapi.yaml` — paths: `GET /exchange`, `GET /analytics`, `GET /exchange/insight`, `POST /exchange/refresh` (optional); schemas per Appendix A
- [ ] Wire `openapi-generator-maven-plugin` in `backend/pom.xml` (generate-sources phase)
- [ ] Wire `openapi-generator-cli` npm script (`generate:api`) in `frontend/package.json`
- [ ] `docker-compose.yml` — PostgreSQL 17 service (add Ollama service later)
- [ ] `docker compose up -d` — bring Postgres up before any migration/entity work

## 1. Backend Foundation

- [ ] Package root `com.exchangerate.manager`, layering: controller → service → repository
- [ ] Migration tool setup (Flyway/Liquibase) — include ShedLock's own lock table migration up front
- [ ] DB schema / JPA entities: `ExchangeRate` (currency_code, rate_to_usd, rate_date, unique constraint on (currency_code, rate_date)), `CurrencyUsage` (currency_code, query_count, last_queried)
- [ ] Spread reference table (Appendix B) — lookup keyed by currency code / "default", not if/else

## 2. Fixer.io Data Collection

- [ ] Fixer.io client (WebClient/RestClient), config for API key via env var
- [ ] Scheduled job: fetch once daily at 00:05 GMT, persist `date` field from response (not fetch date)
- [ ] Upsert on (currency_code, rate_date) — `INSERT ... ON CONFLICT` or JPA saveOrUpdate keyed on composite
- [ ] ShedLock (JDBC provider, same Postgres DB, lock table from section 1) so only one instance calls Fixer.io per scheduled run
- [ ] Manual refresh endpoint (optional, 4.4) — triggers fetch/upsert, must not touch usage counters

## 3. Exchange Rate API (4.2)

- [ ] Implement generated `GET /exchange` interface: from, to, optional date
- [ ] Spread-adjusted calc service: `adjustedRate = (toRateUSD / fromRateUSD) × ((100 − MAX(toSpread, fromSpread)) / 100)`
- [ ] No date → most recent available rates; date with no data → proper HTTP error (ProblemDetail)
- [ ] Usage counter increment: atomic upsert `INSERT ... ON CONFLICT (currency_code) DO UPDATE SET count = count + 1` (no read-modify-write, no plain UPDATE that no-ops on missing row) for both currencies, safe under concurrency
- [ ] Unknown currency / bad input → ProblemDetail 4xx responses

## 4. Analytics Endpoint (4.3)

- [ ] Implement generated `GET /analytics` interface — query count per currency + query dates
- [ ] Design response shape to support frontend Usage Analytics Dashboard (5.3)

## 5. AI Trend Insight (Section 7)

- [ ] Add `spring-ai-starter-model-ollama` 2.0.1, Ollama service in docker-compose, pull `llama3.2`
- [ ] Implement `GET /exchange/insight` — from, to, fromDate, toDate
- [ ] Serialize historical rate rows (dates + values) verbatim into prompt context
- [ ] System prompt constrains output to short, data-grounded commentary
- [ ] Graceful degrade to clear error when Ollama/model unreachable (no fabricated insight)
- [ ] Document local model setup in README (no config guesswork)

## 6. Backend Docs & Verification

- [ ] springdoc-openapi serving Swagger UI from generated interfaces
- [ ] `./mvnw verify` green — unit tests for spread calc, usage counter concurrency, upsert/duplicate handling
- [ ] Manual check: multi-instance scheduler behaves correctly (ShedLock verified)

## 7. Frontend Foundation

- [ ] Angular 21 app scaffold, standalone components, zoneless, routing for 3 views
- [ ] `environment.ts` → `apiBaseUrl` configurable without code changes for `ng serve`
- [ ] Generate typed HTTP client from `contracts/openapi.yaml` into `frontend/src/app/api-client/`

## 8. Frontend Views

- [ ] 5.1 Exchange Rate Calculator — currency pair + optional date form, validated inputs, loading state, API error display, signals for state
- [ ] 5.2 Historical Rates & Trend Chart — currency pair + date range, table of raw rates, line chart, AI insight panel with loading state
- [ ] 5.3 Usage Analytics Dashboard — most-queried currencies, time-period patterns, visualization

## 9. Final Pass

- [ ] Frontend tests (Vitest) for calculator, trend, analytics views
- [ ] End-to-end manual walkthrough: fetch → calculate → analytics → insight
- [ ] README: full setup (Postgres, Ollama/model pull, Fixer.io API key, run commands)
