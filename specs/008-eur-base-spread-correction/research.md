# Phase 0 Research: EUR Base Currency Spread Correction

No `NEEDS CLARIFICATION` markers remain in the Technical Context — the user-supplied implementation
requirements were specific enough to resolve every open question directly. This document records
the resulting decisions and the alternatives considered.

## Decision: Config binding shape

**Decision**: A single immutable `@ConfigurationProperties(prefix = "exchange-rates")` class with
three fields — `baseCurrency` (`String`), `defaultSpreadPercent` (`BigDecimal`), and `spreads`
(`Map<String, BigDecimal>`) — bound via constructor injection (Java `record` or a
`final`-field/no-setters class), registered with `@EnableConfigurationProperties` (or
`@ConfigurationPropertiesScan`) and `@Validated` so Spring fails application startup on a
constraint violation.

**Rationale**: Matches requirement 2 ("immutable, validated `@ConfigurationProperties` class") and
constitution Principle VII (data-driven configuration, not code). Constructor binding gives
immutability for free and plays well with Bean Validation's fail-fast startup behavior — an invalid
`application.yml` becomes a startup error, not a runtime surprise mid-request.

**Alternatives considered**:
- `@Value` injection per field (as `FixerClient` uses for `fixer.api-key`/`fixer.base-url`) —
  rejected because it can't bind a `Map<String, BigDecimal>` cleanly or apply class-level
  cross-field validation (base currency must have an explicit 0 spread).
- A mutable `@ConfigurationProperties` class with setters — rejected; requirement 2 explicitly asks
  for immutability, and mutability would let something in the object graph mutate the "fixed
  reference configuration" at runtime, contradicting the spec's explicit no-runtime-editing scope
  limit.

## Decision: Validation mechanism

**Decision**: Field-level `jakarta.validation` annotations for the simple rules —
`@Pattern(regexp = "^[A-Z]{3}$")` on `baseCurrency`, `@DecimalMin("0.0")`/`@DecimalMax` (exclusive
100) on `defaultSpreadPercent`, and a validated map (`@NotEmpty` + per-key pattern / per-value range
via a small custom validator or `@AssertTrue` method) for `spreads`. The one cross-field rule —
"the configured base currency must have an explicit spread of 0" — is expressed as a class-level
`@AssertTrue`-annotated method (e.g. `isBaseCurrencySpreadZero()`) so it participates in the same
Bean Validation pass and produces the same fail-fast startup behavior as the field-level rules.

**Rationale**: Requirement 3 lists exactly these rules. Bean Validation is already a dependency
(`spring-boot-starter-validation`) and is the idiomatic Spring Boot mechanism for
`@ConfigurationProperties` validation — no new library needed.

**Alternatives considered**:
- A hand-written `@PostConstruct` check throwing a custom exception — rejected; reinvents what Bean
  Validation already does, and loses the standard aggregated-violation-message startup failure
  Spring produces for `@ConfigurationProperties` validation failures.

## Decision: Where the Fixer base-currency check runs

**Decision**: In `RateCollectionService.collect()`, immediately after `fixerClient.getLatestRates()`
returns and before any rate is read or upserted — compare `response.getBase()` against
`ExchangeRateProperties.baseCurrency()` and throw `FixerApiException` on a null/blank/mismatched
value.

**Rationale**: Requirement 5 names this exact check and exception type. Placing it before the
per-currency loop guarantees "no rates from a rejected response are persisted" (spec FR-010) without
needing a transactional rollback trick — the loop simply never starts.

**Alternatives considered**:
- Checking inside `FixerClient` itself — rejected; `FixerClient` is a pure HTTP/deserialization
  boundary (per its own Javadoc) that doesn't know the application's configured base currency, and
  mixing that in would blur the controller→service→repository/client layering (constitution
  Principle VI).

## Decision: Optional EUR == 1 sanity check

**Decision**: Include it. After the base-currency match succeeds, additionally check that
`rates.get(baseCurrency)` is present and numerically equal to `BigDecimal.ONE`
(`compareTo(BigDecimal.ONE) == 0`, not `.equals()`, since Fixer may return `1.0` at a different
scale than a literal `ONE`), throwing the same `FixerApiException` on failure.

**Rationale**: Requirement 5 calls this out as optional; it's included because it's a cheap,
narrowly-scoped extra guard against a provider payload that passes the base-currency label check but
is otherwise internally inconsistent (e.g. a corrupted or truncated `rates` map) — directly serving
the spec's User Story 3 intent ("fail clearly if the provider returns an unexpected or inconsistent
payload").

**Alternatives considered**:
- Skipping it — rejected as strictly weaker for negligible cost; there's no scenario where checking
  costs more than the one extra map lookup and `compareTo`.

## Decision: SpreadLookup input handling

**Decision**: `SpreadLookup.spreadFor(String currencyCode)` keeps its existing signature and keeps
trusting the currency code as already validated/uppercased — the OpenAPI contract already enforces
`^[A-Z]{3}$` on every currency-code request parameter (`contracts/openapi.yaml`), and stored
currency codes are written by `RateCollectionService` directly from Fixer's own (already
uppercase, three-letter) keys. No new normalization step is introduced inside `SpreadLookup`.

**Rationale**: Requirement 4 asks to "normalize or validate input consistently with the existing API
validation rules" — the existing rule is the `^[A-Z]{3}$` pattern already enforced at the API
boundary, so the correct action is to *rely on* that existing boundary validation rather than
duplicate or second-guess it deeper in the call stack.

**Alternatives considered**:
- Re-uppercasing/trimming inside `SpreadLookup` "just in case" — rejected as redundant defensive
  coding against an input shape the API layer already guarantees, and it would silently mask a bug
  upstream if that guarantee were ever violated.

## Decision: Naming/comment separation of business base currency vs. normalization anchor

**Decision**: `ExchangeRateProperties.baseCurrency` and its Javadoc explicitly say this is "the
provider's (Fixer.io's) business base currency, used for spread policy" and is deliberately not the
same concept as the `USD` literal in `RateCollectionService`/the `rate_to_usd` column, which is
called out in a comment as "the internal normalization anchor — has no bearing on which currency is
spread-free." No field, column, or variable is renamed (schema and existing method signatures are
preserved per the no-schema-change constraint); the separation is made through naming and Javadoc on
the new/touched code, not a data-model change.

**Rationale**: Requirement 6 asks for this separation "in names and comments," not in the schema.
This also directly fixes the root defect: `SpreadLookup` no longer derives 0%-spread status from
anything USD-related.

**Alternatives considered**:
- Renaming `rateToUsd`/the `USD` normalization constant to reduce ambiguity — rejected; out of scope
  (would touch the schema/entity and is explicitly preserved per the plan's constraints), and
  Javadoc/comments are sufficient to remove the conceptual conflation the defect was caused by.
