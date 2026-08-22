<!--
Sync Impact Report
Version change: N/A (template) → 1.0.0
Modified principles: none (initial ratification)
Added sections:
  - Core Principles (I–IX)
  - Technology Stack Requirements
  - Development & Quality Standards
  - Governance
Removed sections: none
Deferred items: none (RATIFICATION_DATE set to today per repo convention; no prior date exists)
-->

# Exchange Rate Management System Constitution

## Core Principles

### I. Monetary Precision
All monetary and rate values MUST be represented with `BigDecimal` (or equivalent
arbitrary-precision type) wherever a rate is stored, computed, or serialized. `double`/`float`
MUST NOT be used for these values.

**Rationale**: Floating-point binary types introduce rounding error in financial calculations;
exchange rate math must be exact and reproducible.

### II. Accurate Rate Provenance
Persisted exchange rate records MUST store the date the external rate provider reports the rate
for, not the date/time the system happened to fetch it.

**Rationale**: The rate's business meaning is tied to the date it was calculated upstream; using
the fetch timestamp would silently corrupt historical data and any date-based queries against it.

### III. Idempotent Data Collection
Ingestion of rate data MUST handle duplicate records for the same currency and date gracefully —
via an upsert keyed on the composite of currency and rate date, never a raw insert that can fail
or duplicate rows.

**Rationale**: Scheduled and manual fetches can overlap or retry; the storage layer must converge
to one correct record per currency/date regardless of how many times a fetch runs.

### IV. Multi-Instance Scheduler Safety
Scheduled data-collection jobs MUST behave correctly when the service runs as multiple instances
in production — only one instance actually calls the external rate provider per scheduled run.

**Rationale**: Without coordination, redundant instances would multiply calls to a rate-limited
external API and risk exhausting quota or producing conflicting writes.

### V. Concurrency-Safe Usage Counters
Usage counters MUST be incremented atomically at the data-store level (e.g., a single atomic
upsert/update statement). Read-modify-write increments in application code MUST NOT be used.

**Rationale**: Counters are updated on every successful query and are a direct concurrency
hazard; atomic increments are the only way to guarantee correctness under concurrent load.

### VI. Layered Separation of Concerns
Backend code MUST follow a controller → service → repository layering. Controllers stay thin;
validation and business calculations (including rate adjustment logic) belong in the service
layer, not the controller or the data layer.

**Rationale**: Keeping business rules out of controllers and repositories keeps them testable in
isolation and prevents logic from leaking into the transport or persistence layers.

### VII. Data-Driven Configuration Over Conditionals
Business rules that vary by a lookup key (such as a per-currency adjustment percentage) MUST be
modeled as a keyed lookup/reference table, not as branching conditional code. Adding or changing
an entry MUST be a data change, not a code change.

**Rationale**: Hard-coded conditional branches for what is fundamentally reference data make the
system brittle to extend and easy to get wrong when new entries are added.

### VIII. Grounded AI Output, Honest Degradation
Any AI-generated insight MUST be produced by passing the actual underlying historical data into
the model's context verbatim — the model must respond to real data, not produce a generic or
fabricated answer. When the model or its serving infrastructure is unreachable, the system MUST
degrade to a clear, explicit error rather than fabricating or guessing an insight.

**Rationale**: An insight feature is worthless, or actively misleading, if it can silently
produce plausible-sounding text disconnected from the real data behind it.

### IX. Environment-Configurable Frontend
The frontend MUST be runnable against a backend whose base URL is set via configuration/an
environment variable, without requiring code changes to switch targets.

**Rationale**: The same build must run locally and against other environments without a rebuild
tied to a hard-coded endpoint.

## Technology Stack Requirements

- **Backend**: Java 17 or later, Spring Boot, Maven, Hibernate/Spring Data JPA, any relational
  database.
- **Frontend**: Angular v15 or later, TypeScript throughout.
- **AI Integration**: Spring AI (preferred) or LangChain4j, connected to any open-source LLM
  (a local model via Ollama, or an OpenAI-compatible endpoint).
- **API Documentation**: Swagger/OpenAPI, generated from and kept consistent with the API's
  actual request/response contracts.

Exact versions and libraries beyond these minimums are implementation decisions, not governance
constraints, and may be tracked separately from this document.

## Development & Quality Standards

- REST error responses MUST use a consistent problem-detail shape for 4xx conditions (e.g.,
  unknown currency, no rate data for a requested date) rather than ad hoc error bodies.
- When no date is supplied for a rate query, the system MUST use the most recent available
  rates; when a requested date has no data, the system MUST return an appropriate HTTP error
  rather than a fabricated or interpolated value.
- Manual/administrative operations that trigger a data fetch (e.g., a manual refresh) MUST NOT
  alter usage counters — usage counters reflect query activity only.
- Project structure, internal libraries, and design patterns beyond the layering and data-driven
  rules above are open decisions, but MUST demonstrate good separation of concerns and idiomatic
  use of the chosen frameworks.

## Governance

This constitution supersedes ad hoc conventions where the two conflict. All feature plans, task
breakdowns, and reviews MUST verify compliance with the Core Principles above before merging.

Amendments require: a documented rationale for the change, an explicit version bump per the
policy below, and an update to any dependent guidance the amendment invalidates.

Versioning policy (semantic versioning applied to governance content):
- **MAJOR**: Backward-incompatible removal or redefinition of a principle.
- **MINOR**: A new principle or materially expanded section is added.
- **PATCH**: Clarifications, wording, or non-semantic refinements.

Any complexity that appears to violate a principle (e.g., bypassing atomic counters, hard-coding
a lookup as conditionals) MUST be justified in the relevant design/plan artifact or corrected.

**Version**: 1.0.0 | **Ratified**: 2026-08-22 | **Last Amended**: 2026-08-22
