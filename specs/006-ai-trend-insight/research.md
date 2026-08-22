# Phase 0 Research: Backend Spring AI Slice (Trend Insight Endpoint)

## Spring AI integration style

**Decision**: Use Spring AI 2.0.1's `ChatClient` (fluent API) built from the auto-configured
`OllamaChatModel`, via `spring-ai-starter-model-ollama`. A single `ChatClient` bean (default
system prompt attached at construction, or built per-call) is injected into `TrendInsightService`.

**Rationale**: `ChatClient` is the idiomatic Spring AI 2.x entry point for a single-turn,
non-conversational call like this one (no memory, no tools, no RAG). It also gives an easy seam
to mock (`ChatClient.Builder`/`ChatClient`) in `@WebMvcTest`-style slice tests, matching the
existing `ExchangeControllerTest.java` pattern of mocking service-layer collaborators.

**Alternatives considered**: Calling `OllamaChatModel`/`ChatModel` directly — rejected only
because `ChatClient`'s fluent `prompt().system(...).user(...).call().content()` reads more
clearly for this one-shot use case; no functional difference for this slice's needs.

## Grounding technique

**Decision**: Serialize each `RateTrendPoint` (date + spread-adjusted `BigDecimal` rate) verbatim
as plain text lines (`YYYY-MM-DD: <rate>`) into the user message. The system prompt instructs the
model to: (a) act as a financial data summarizer, (b) reference only the values supplied, (c)
never invent a date or figure not present in the input, (d) if given exactly one data point,
describe that single value rather than asserting a trend, (e) keep the response short
(plain-language, a few sentences).

**Rationale**: Matches spec.md's clarification directly — "prompt engineering only, no automated
verification of the output against the source data" — and constitution principle VIII's
requirement to pass the actual underlying data into the model's context verbatim rather than a
summary or embedding.

**Alternatives considered**: RAG/vector retrieval over historical rates — explicitly out of scope
per spec.md's Assumptions ("no RAG, no fine-tuning"). Passing raw JSON instead of plain text lines
— rejected as unnecessary; plain text is more token-efficient and equally parseable by the model
for this small, structured dataset (≤365 rows).

## Range-too-large enforcement (FR-009)

**Decision**: Validate the effective date range **before** querying the database or calling the
AI model. This requires resolving the same default-window logic `ExchangeRateService.getTrend`
already applies (`startDate` defaults to 29 days before today, `endDate` defaults to today) ahead
of the size check, so the check runs against the actual range that will be summarized.

Extract this default-resolution + `startDate > endDate` validation out of
`ExchangeRateService.getTrend` into a small shared package-private helper (e.g.
`DateRangeResolver` or a static method on `ExchangeRateService`) that `TrendInsightService` also
calls, rather than duplicating the two-line default logic in both places.

**Rationale**: The exact same default/validate logic would otherwise be copy-pasted into a second
service; extracting it once avoids drift between the two callers (e.g. one place changing the
29-day default without the other). This is a small, mechanical extraction — not a new abstraction
layer — justified because there are now two real call sites, not a hypothetical future one.

**Alternatives considered**: Checking point *count* after querying instead of date *span* before
querying — rejected because it wastes a DB round-trip (and, if reordered after the AI call, a
model invocation) on a request that will be rejected anyway; checking span up front is a pure
in-memory `LocalDate` computation.

## AI-unavailable detection (FR-004, FR-005, FR-008)

**Decision**: Wrap the `ChatClient` call in a single broad `catch (Exception e)` at the
`TrendInsightService` boundary, log the root cause at `WARN`, and rethrow as a new
`AiInsightUnavailableException` (mapped to HTTP 503 in `GlobalExceptionHandler`, distinct from the
existing `FixerApiException`'s 502, which represents a *third-party* upstream failure rather than
the locally-hosted model being down).

**Rationale**: Spring AI's Ollama transport can fail in several ways (connection refused, timeout,
model-not-pulled 404 from Ollama's own API, mid-generation transport error) surfaced through
different exception types across Spring AI/Spring WebClient's own hierarchy; catching broadly at
this single boundary is simpler and more robust than enumerating every possible transport
exception, and matches FR-005's requirement that *any* failure condition surfaces as an explicit
"unavailable" outcome rather than risking an uncaught exception turning into a generic 500. Because
each request is independent and stateless (no retry/circuit-breaker state kept), recovery on the
next request once Ollama is back (FR-008) falls out naturally — nothing to reset.

**Alternatives considered**: Enumerating specific Spring AI exception types — rejected as brittle
across Spring AI point releases and unnecessary precision for a single "unavailable" outcome.

## Request timeout (FR-004 clarification: configurable, default 30s)

**Decision**: Configure Spring AI's Ollama client read timeout via a new
`spring.ai.ollama.chat.client.read-timeout` (or the equivalent property Spring AI 2.0.1 exposes for
the underlying HTTP client — verify exact key against the starter's `OllamaConnectionProperties`/
`OllamaChatProperties` at implementation time) in `application.yml`, exposed through the repo's
existing `${ENV_VAR:-default}` override convention, e.g.:

```yaml
spring:
  ai:
    ollama:
      base-url: ${OLLAMA_BASE_URL:-http://localhost:11434}
      chat:
        client:
          read-timeout: ${AI_INSIGHT_TIMEOUT_SECONDS:-30}s
```

A timeout expiry surfaces as a transport exception from the `ChatClient` call, which the broad
`catch (Exception e)` above already converts into `AiInsightUnavailableException` → 503 — no
separate timeout-specific exception type or catch branch is needed; the general AI-unavailable
handling described above already covers this case by construction.

**Rationale**: Matches spec.md's clarification directly (configurable timeout, default 30 seconds,
treated as the FR-004 "unavailable" outcome rather than an indefinite wait). Configuring it in
`application.yml` (rather than hard-coding it in `TrendInsightService`) follows the repo's existing
pattern of environment-overridable config (compare `fixer:` block, `spring.datasource.*`).

**Alternatives considered**: A manual `CompletableFuture.get(timeout, ...)`/`@Async` wrapper around
the `ChatClient` call in `TrendInsightService` — rejected as unnecessary; the underlying HTTP
client already supports a read timeout, and adding a second, redundant timeout mechanism in
application code would risk the two disagreeing.

## No-data handling (FR-003) and the future-date edge case

**Decision**: Reuse the existing `RateDataNotFoundException` (already mapped to 404 in
`GlobalExceptionHandler`) when `ExchangeRateService.getTrend(...)` returns an empty list for the
resolved range. No new exception type needed.

**Rationale**: Consistent with the existing `/exchange` single-date lookup's 404 semantics for
"no rate data found" — reusing the type keeps the API's error surface predictable rather than
introducing a second, differently-named 404 cause. The "entirely future date range" edge case
resolves to the same empty-list path automatically (no rows exist for future dates), so it needs
no special-case code — it degrades to the same "no data available" outcome the spec calls for.

## Single-data-point framing (FR-007)

**Decision**: Handled entirely in the system prompt (see Grounding technique above) — no separate
code branch. The prompt explicitly instructs: "if exactly one data point is supplied, describe
that single observed value; do not claim a trend, direction, or volatility, since at least two
points are required for that."

**Rationale**: This is a phrasing/quality concern the spec itself scopes to "prompt engineering
only" (same clarification as grounding) — adding a code branch that pre-writes part of the
narrative would blur the line the spec draws and duplicate what the model is already asked to do
grounded in the real data.

## Usage counters

**Decision**: The insight endpoint does not call `CurrencyUsageRepository.incrementUsage(...)` —
same as the existing `/exchange/trend` analytics endpoint, and distinct from the single-date
`/exchange` lookup endpoint, which does increment.

**Rationale**: Constitution principle V's atomic-increment requirement applies to whatever this
codebase decides counts as a "usage" event; the existing precedent already excludes bulk/analytics
reads from that counter, and the insight endpoint is squarely in that same "read-only analytics
over existing data" category.

## Testing approach for the AI-dependent path

**Decision**: Mock the `ChatClient` (or the underlying `ChatModel`) Spring bean in
`@WebMvcTest`-style/service-level tests to exercise the success narrative path, the AI-unavailable
path (mock throws), the no-data path (repository returns empty), and the range-too-large path (no
mocking needed — rejected before any collaborator is called). `AbstractIntegrationTest`'s
Testcontainers-provisioned Postgres continues to back any test needing real
`exchange_rates` rows. No Testcontainers module exists for Ollama, and constitution principle X's
Testcontainers requirement is scoped to database isolation, not third-party service isolation — a
live Ollama smoke test is covered by `quickstart.md` instead, not by the automated suite.

## Dependency on the prior (infra) planning pass

**Decision**: This slice's code compiles, unit-tests, and slice-tests independently of a running
Ollama container (the AI client is mocked). The **quickstart's** live end-to-end smoke test,
however, requires the `ollama` service from this feature's earlier infra-only planning pass to
actually be running — and as of this plan, that pass's `tasks.md` has been generated but not yet
executed (the root `docker-compose.yml` still only defines `postgres`). This is called out
explicitly in `quickstart.md`'s prerequisites rather than assumed.

**Rationale**: Keeps this slice's own scope (application code) decoupled from whether the earlier
infra slice has been implemented yet, while being honest in the validation guide about what must
be true for the live smoke test to actually pass.

## Java version discrepancy (pre-existing, out of scope)

**Note (not a decision made by this slice)**: CLAUDE.md's tech-stack table pins Java 21, but
`backend/pom.xml`'s `<java.version>` and compiler `source`/`target` are currently `17`. This slice
does not change or rely on any Java 21-only language feature, so it is left as-is; reconciling the
mismatch is a separate concern outside this feature's scope.
