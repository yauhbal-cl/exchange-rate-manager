# Feature Specification: AI Trend Insight (Local LLM)

**Feature Branch**: `006-ai-trend-insight`

**Created**: 2026-08-22

**Status**: Draft

**Input**: User description: "AI Trend Insight implementation. Using local LLM"

## Clarifications

### Session 2026-08-22

- Q: What is the maximum date range (or point count) the system should attempt to summarize before telling the user the range is too large instead of generating a narrative? → A: 1 year of daily data (~365 points)
- Q: How should the system guard against an AI response that doesn't actually reference any real value from the supplied data (a fabricated-sounding insight)? → A: Prompt engineering only, no automated verification of the output against the source data
- Q: Should trend insight be its own endpoint, or a field added to the existing trend-points response? → A: Separate endpoint, same currency-pair/date-range parameters as the existing trend endpoint — decouples insight generation latency/availability from the (cheap, already-working) trend-points read
- Q: How long should the system wait for the local AI model before treating it as unavailable (FR-004)? → A: Configurable timeout, default 30 seconds — exceeding it surfaces the FR-004 "unavailable" outcome rather than an indefinite wait
- Q: How long/short should the generated narrative be, concretely, so "short" is testable? → A: 2-4 sentences

## User Scenarios & Testing *(mandatory)*

### User Story 1 - View AI commentary on a currency trend (Priority: P1)

A user viewing historical exchange rates for a currency pair over a date range wants a short,
plain-language explanation of what the trend shows (direction, volatility, notable moves) without
having to interpret the raw numbers themselves.

**Why this priority**: This is the core value of the feature — turning a table/chart of rates into
an understandable narrative. Without it, there is no feature.

**Independent Test**: Select a currency pair and a date range that has historical rate data, request
an insight, and confirm a short narrative summary is returned that references the actual observed
values (e.g., overall direction, high/low points).

**Acceptance Scenarios**:

1. **Given** a currency pair and date range with historical rate data available, **When** the user
   requests a trend insight, **Then** the system returns a short narrative describing the trend
   (e.g., upward/downward/stable, notable volatility) grounded in the actual rate values for that
   range.
2. **Given** the same currency pair and date range is requested again, **When** the user requests
   the insight, **Then** the narrative remains consistent with the underlying data (no contradictory
   claims about direction or magnitude across repeated requests).

---

### User Story 2 - Clear failure when the AI service is unavailable (Priority: P2)

A user requests a trend insight while the local AI model/service is down, still starting up, or
otherwise unreachable. The user needs to know the insight could not be generated, rather than
receiving a made-up or misleading summary.

**Why this priority**: Trust in the feature depends on never fabricating commentary. A visible,
honest failure is safer than a silently wrong one, so this ranks just below the core happy path.

**Independent Test**: With the local AI service stopped/unreachable, request an insight for a
currency pair and date range that has data, and confirm the user receives a clear, specific
"insight unavailable" message rather than a generated narrative or a generic crash.

**Acceptance Scenarios**:

1. **Given** the local AI service is unreachable, **When** the user requests a trend insight,
   **Then** the system responds with a clear message stating the insight could not be generated
   (not a fabricated narrative, not a raw technical error).
2. **Given** the local AI service becomes available again after a prior failure, **When** the user
   retries the same request, **Then** the system successfully returns a generated insight.

---

### User Story 3 - No insight when there is no underlying data (Priority: P3)

A user selects a currency pair and/or date range for which no historical rate data exists, and
requests an insight anyway.

**Why this priority**: Prevents a confusing or fabricated response for a request that has nothing
to summarize; lower priority than the two above because it is a boundary case rather than the main
flow.

**Independent Test**: Request an insight for a currency pair/date range known to have zero stored
rate observations, and confirm the response clearly states there is no data to summarize rather
than attempting to generate a narrative.

**Acceptance Scenarios**:

1. **Given** a currency pair and date range with no stored historical rate data, **When** the user
   requests a trend insight, **Then** the system responds with a clear "no data available for this
   range" message and does not attempt to generate a narrative.
2. **Given** a date range with exactly one stored rate observation, **When** the user requests an
   insight, **Then** the system returns a narrative that describes the single observed value rather
   than claiming a trend (since a trend requires at least two points).

### Edge Cases

- What happens when the requested date range spans more than 1 year of daily observations
  (~365 data points)? The system MUST NOT attempt to summarize it; the user must be told
  explicitly that the range is too large rather than receiving a partial, dropped-point, or
  misleading summary. Ranges of 1 year or fewer are always summarized from the real data without
  silently dropping or fabricating points.
- How does the system handle a currency pair that is valid but has gaps in the middle of the
  requested date range (e.g., a day the source feed failed)? The narrative should reflect only the
  data points that actually exist and must not imply continuity that isn't there.
- What happens if the user requests an insight for a date range that is entirely in the future
  (no rates could possibly exist yet)? Treated the same as "no data available."
- What happens if the AI service responds, but with output that doesn't reference any real number
  from the supplied data (e.g., a generic or clearly unrelated response)? This is a quality/trust
  risk the system guards against via system-prompt constraints only (instructing the model to stay
  strictly grounded in the supplied data) — there is no automated runtime check that rejects an
  ungrounded response, so grounding quality is validated through prompt design and testing rather
  than enforced per-request.
- What happens if the same request is made twice in quick succession while the first is still
  generating? Each request is handled independently; the user is not blocked from retrying.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: System MUST allow a user to request a trend insight for a specific currency pair and
  date range that has historical rate data, via a request path dedicated to insight generation and
  separate from the trend-points retrieval used to render the chart, using the same currency-pair
  and date-range parameters as that existing retrieval.
- **FR-002**: The generated insight MUST be a short (2-4 sentence), plain-language narrative
  grounded strictly in the actual historical rate values for the requested currency pair and date
  range (no invented figures, dates, or claims not supported by the underlying data).
- **FR-003**: System MUST clearly indicate when no historical rate data exists for the requested
  currency pair/date range, and MUST NOT attempt to generate a narrative in that case.
- **FR-004**: System MUST clearly indicate when the insight cannot be generated because the
  underlying AI capability is unavailable, distinct from the "no data" case in FR-003. A request
  that does not receive a response from the AI capability within a configurable timeout (default
  30 seconds) MUST be treated as this "unavailable" outcome, not left waiting indefinitely.
- **FR-005**: System MUST NOT return a fabricated or guessed insight under any failure condition —
  failure states always surface as an explicit "unavailable" outcome, never as invented commentary.
- **FR-006**: The insight generation MUST run using a locally hosted AI capability (no data about
  currency rates is sent to a third-party AI service over the network).
- **FR-007**: System MUST handle a date range containing exactly one data point by describing that
  single value rather than asserting a trend.
- **FR-008**: System MUST be able to recover automatically (return a successful insight) on the
  next request after a prior AI-unavailable failure is resolved, without requiring any other
  corrective action from the user.
- **FR-009**: System MUST reject a trend insight request whose date range spans more than 1 year
  of daily observations (~365 data points) with a clear "range too large to summarize" message,
  and MUST NOT attempt to generate a partial or fabricated narrative for such a request.

### Key Entities

- **Trend Insight Request**: A currency pair and a date range for which the user wants a narrative
  summary; conceptually derived from the same historical rate data already tracked by the system.
- **Trend Insight Result**: The outcome of a request — either a generated narrative text grounded in
  the requested data, or a clear status explaining why no narrative could be produced (no data /
  AI unavailable).

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: Users can obtain a trend narrative for a chosen currency pair and date range without
  needing to manually interpret the raw rate table themselves.
- **SC-002**: 100% of insight failures (no data, AI unavailable) surface as a clear, specific
  message rather than a fabricated narrative or an unexplained error.
- **SC-003**: A user can successfully retry and obtain an insight within one additional attempt
  after the underlying AI capability recovers from an outage, with no other steps required.
- **SC-004**: Every factual claim in a generated narrative (direction, high/low, notable moves)
  corresponds to a value actually present in the historical rate data supplied for that request.
  Enforced via system-prompt grounding constraints and validated through manual/testing review of
  generated narratives, not by an automated runtime check on each response.

## Assumptions

- The historical rate data itself (currencies, dates, values) already exists in the system
  independent of this feature; this feature only adds narrative summarization on top of it.
- "Local LLM" means the AI model runs within infrastructure the organization controls, so that
  no currency rate data leaves the system boundary — chosen for data-privacy and cost-control
  reasons rather than being specified by the user beyond "using local LLM."
- The insight is generated on demand per request; there is no requirement in this spec for the
  narrative to be pre-generated, scheduled, or permanently stored for later retrieval.
- One insight narrative is returned per request; multi-turn conversation or follow-up questions
  about the insight are out of scope for this feature.
- The language of the generated narrative is English, consistent with the rest of the product.
