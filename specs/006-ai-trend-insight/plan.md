# Implementation Plan: AI Trend Insight (Local LLM) — Backend Spring AI Slice

**Branch**: `006-ai-trend-insight` | **Date**: 2026-08-22 | **Spec**: [spec.md](./spec.md)

**Input**: Feature specification from `/specs/006-ai-trend-insight/spec.md`

**Scope of this planning pass**: The backend insight endpoint — Spring AI + Ollama wiring, a new
`TrendInsightService` that grounds narrative generation in the existing spread-adjusted historical
rate series, and the controller/exception-handling surface for the three user stories in spec.md
(narrative on data, honest AI-unavailable failure, honest no-data failure) plus the range-too-large
edge case (FR-009). Frontend consumption of this endpoint is out of scope and remains tracked
against spec.md for a later planning/tasks cycle.

**Depends on**: The prior planning pass for this feature (local Ollama service in
`docker-compose.yml`, `research.md`/`quickstart.md` already written) — its `tasks.md` has been
generated but **not yet implemented** as of this plan (the `ollama` service is not yet present in
the root `docker-compose.yml`). This slice's code changes do not require the running container to
compile or unit-test (the AI client is mocked in slice tests), but the quickstart's live smoke test
does require that prior slice's `tasks.md` to be executed first.

## Summary

Add a new `GET /exchange/trend/insight` endpoint that reuses the existing spread-adjusted trend
data (`ExchangeRateService.getTrend`) and passes it verbatim into a Spring AI `ChatClient` backed
by a local Ollama `llama3.2` model, returning a short grounded narrative. Failure modes are
distinct and explicit per the constitution's Grounded AI Output principle: unknown
currency/invalid range → 400, no stored data for the range → 404 (reusing the existing
`RateDataNotFoundException`), range spanning more than ~365 daily points → 400 (new
`TrendRangeTooLargeException`), and the AI capability being unreachable → 503 (new
`AiInsightUnavailableException`). No new persistence, no RAG, no fine-tuning — data is serialized
into the prompt context per request and never stored.

## Technical Context

**Language/Version**: Java 17 (as currently pinned in `backend/pom.xml`'s
`<java.version>`/compiler `source`/`target`). **Note**: CLAUDE.md's tech-stack table states Java
21; this is a pre-existing discrepancy in the repository, not something this slice introduces or
is in scope to reconcile — carried forward as-is.

**Primary Dependencies**: Spring Boot 4.1.1 (existing), `spring-ai-starter-model-ollama` 2.0.1 (new
— not yet in `backend/pom.xml`), Lombok 1.18.42 (existing), MapStruct 1.6.3 (existing)

**Storage**: PostgreSQL 17 (existing `exchange_rates` table, read-only for this slice — no schema
change, no new migration)

**Testing**: JUnit 5 + Spring Boot Test (existing conventions); `AbstractIntegrationTest`'s
singleton Testcontainers `PostgreSQLContainer` for any DB-backed assertion; the Spring AI
`ChatClient`/`ChatModel` bean is mocked in slice tests (no Testcontainers module exists for Ollama
— it is a third-party local service, not a database, so Testcontainers isolation per constitution
principle X does not apply to it)

**Target Platform**: Same backend server process as the rest of the API (Linux container / local
JVM); Ollama reachable at `http://ollama:11434` from other containers or `http://localhost:11434`
from the host, per the prior slice's `research.md`

**Project Type**: Web application monorepo (existing `backend/` + `frontend/`) — this slice touches
only `backend/`

**Performance Goals**: N/A — no explicit latency SLA in spec.md; LLM generation latency is
inherently variable and not benchmarked in this slice

**Constraints**: No currency-rate data may leave the local Ollama instance (FR-006); the narrative
MUST NOT fabricate figures not present in the supplied data (FR-002, FR-005, constitution
principle VIII); failures MUST be explicit and distinguishable by cause (FR-003 vs FR-004);
requests spanning more than ~365 daily points MUST be rejected before generation is attempted
(FR-009); a request that gets no AI response within a configurable timeout (default 30s, via
`application.yml`) MUST be treated as AI-unavailable rather than left waiting indefinitely
(FR-004 clarification)

**Scale/Scope**: One new endpoint, one new service, two new exception types, one new MapStruct
mapper, one new Spring AI dependency + config block, one new `contracts/` proposed-schema file for
this feature

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| Principle | Applies? | Assessment |
|---|---|---|
| I. Monetary Precision | Yes | The rate values fed into the prompt are the existing `BigDecimal` spread-adjusted rates from `RateTrendPoint`; no `double`/`float` conversion introduced. PASS. |
| II. Accurate Rate Provenance | No | No new rate ingestion; reads existing `rate_date`-keyed rows unchanged. |
| III. Idempotent Data Collection | No | No data collection in this slice. |
| IV. Multi-Instance Scheduler Safety | No | No scheduled job added. |
| V. Concurrency-Safe Usage Counters | Yes | Decision: the insight endpoint, like the existing `/exchange/trend` analytics endpoint, MUST NOT increment `CurrencyUsage` counters — it is read-only analytics over already-collected data, not a priced lookup. Documented explicitly here to avoid ambiguity; no counter-mutating code added. PASS. |
| VI. Layered Separation of Concerns | Yes | New `TrendInsightService` owns prompt construction, range validation, and AI-unavailable translation; `ExchangeController` stays thin (delegates + maps); repository layer untouched. PASS. |
| VII. Data-Driven Configuration | No | No new per-currency conditional lookup introduced. |
| VIII. Grounded AI Output, Honest Degradation | Yes | Core principle for this slice. Historical rate rows are serialized verbatim into the prompt; system prompt constrains the model to the supplied data only (per spec's clarification: prompt engineering only, no automated grounding check). AI-unreachable degrades to an explicit `AiInsightUnavailableException` → 503, never a fabricated narrative. PASS. |
| IX. Environment-Configurable Frontend | No | Frontend not touched by this slice. |
| X. Test Isolation via Testcontainers | Yes | Any DB-backed test in this slice reuses `AbstractIntegrationTest`'s Testcontainers-provisioned Postgres; the AI client itself is mocked rather than run against a live Ollama container in automated tests (Ollama is not a database, so principle X's Testcontainers requirement doesn't extend to it — the live smoke test lives in `quickstart.md` instead). PASS. |

**Result**: PASS. No violations; no entries needed in Complexity Tracking.

## Project Structure

### Documentation (this feature)

```text
specs/006-ai-trend-insight/
├── plan.md              # This file (/speckit-plan command output) — supersedes the prior
│                         # infra-only-scoped plan.md for this same branch
├── research.md          # Phase 0 output (/speckit-plan command)
├── data-model.md         # Phase 1 output (/speckit-plan command)
├── quickstart.md        # Phase 1 output (/speckit-plan command)
├── contracts/           # Phase 1 output (/speckit-plan command)
│   ├── README.md        # Superseded note added: now points at trend-insight-endpoint.yaml
│   └── trend-insight-endpoint.yaml  # Proposed openapi.yaml additions for this slice
└── tasks.md             # Phase 2 output (/speckit-tasks command - NOT created by /speckit-plan)
```

### Source Code (repository root)

```text
exchange-rate-manager/
├── contracts/openapi.yaml          # MODIFIED (implementation task): add /exchange/trend/insight
│                                    # path + TrendInsightResponse schema, per this feature's
│                                    # contracts/trend-insight-endpoint.yaml draft
├── backend/
│   ├── pom.xml                     # MODIFIED: add spring-ai-bom + spring-ai-starter-model-ollama
│   ├── src/main/java/com/exchangerate/manager/
│   │   ├── config/
│   │   │   └── AiConfig.java              # NEW: ChatClient bean wiring (if not fully auto-configured)
│   │   ├── controller/
│   │   │   └── ExchangeController.java    # MODIFIED: implement new generated operation
│   │   ├── exception/
│   │   │   ├── AiInsightUnavailableException.java   # NEW
│   │   │   ├── TrendRangeTooLargeException.java     # NEW
│   │   │   └── GlobalExceptionHandler.java           # MODIFIED: 2 new @ExceptionHandler methods
│   │   ├── mapper/
│   │   │   └── TrendInsightResponseMapper.java       # NEW (MapStruct)
│   │   └── service/
│   │       ├── TrendInsightService.java              # NEW
│   │       ├── TrendInsightResult.java               # NEW (record)
│   │       └── ExchangeRateService.java              # MODIFIED: extract shared date-range
│   │                                                    default/validation helper reused by
│   │                                                    both getTrend and the new service
│   └── src/main/resources/
│       └── application.yml         # MODIFIED: add spring.ai.ollama.* config block, including
│                                    # a configurable chat read-timeout (default 30s, see
│                                    # research.md "Request timeout")
├── docker-compose.yml               # UNCHANGED by this slice (see "Depends on" above)
└── frontend/                        # Untouched by this slice
```

**Structure Decision**: Existing web-application monorepo layout, extended within
`backend/src/main/java/com/exchangerate/manager/` following the established
controller → service → repository layering and MapStruct/Lombok conventions already used by the
`005-analytics-endpoint` slice (`ExchangeRateTrendResponseMapper`, `GlobalExceptionHandler`
pattern). No new top-level directories.

## Complexity Tracking

*No violations — table not needed.*
