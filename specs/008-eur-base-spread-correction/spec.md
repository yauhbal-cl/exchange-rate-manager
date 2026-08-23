# Feature Specification: EUR Base Currency Spread Correction

**Feature Branch**: `008-eur-base-spread-correction`

**Created**: 2026-08-23

**Status**: Draft

**Input**: User description: "Update the exchange-rate system so that its spread rules correctly reflect the business requirements in TASK.md. Fixer.io always returns EUR as the base currency, and EUR must therefore have a 0% spread. The remaining currencies should use the spread groups defined in Appendix B, with all unlisted currencies receiving the default 2.75% spread. Keep the concept of Fixer's base currency separate from the system's internal rate normalization. Rates may continue to be normalized against USD, but this must not cause USD to be treated as the spread-free base currency. The base currency and spread rules are fixed reference configuration and do not need runtime editing or a database-backed administration feature. The implementation should be easy to understand, validate, test, and change through configuration. Fixer includes EUR in the returned rates map, so no synthetic EUR rate needs to be added. However, ingestion should verify that the returned base currency is EUR and fail clearly if the provider returns an unexpected or inconsistent payload. Update the implementation, tests, and relevant documentation while preserving existing API behavior and historical rate storage."

## User Scenarios & Testing *(mandatory)*

### User Story 1 - EUR is the only zero-spread currency (Priority: P1)

A user requests an exchange rate quote involving EUR. The quote must reflect a 0% spread for EUR,
because EUR is the currency the data provider treats as its base. A user requesting a quote involving
USD (and not EUR) must see USD's correct configured spread, not a zero spread — USD only plays a role
in how rates are normalized internally and must not be mistaken for the provider's base currency.

**Why this priority**: This is the core correctness defect driving the feature — the spread-free
currency is currently misidentified, which directly corrupts every rate quote that includes the
currency that should carry a non-zero spread. This must be fixed before anything else matters.

**Independent Test**: Request a quote for EUR against any other currency and confirm 0% spread is
applied for EUR; separately request a quote for USD against a third currency (no EUR involved) and
confirm USD's own configured spread (not 0%) is applied.

**Acceptance Scenarios**:

1. **Given** stored rates including EUR and PLN, **When** a user requests an EUR/PLN quote, **Then**
   the calculation applies 0% spread for EUR and PLN's configured spread for PLN.
2. **Given** stored rates including USD and PLN, **When** a user requests a USD/PLN quote, **Then**
   the calculation applies USD's own configured (non-zero, unless separately listed as such) spread,
   not a zero spread by virtue of being USD.
3. **Given** internal rates are normalized against USD, **When** any quote is calculated, **Then**
   the choice of normalization currency has no effect on which currency is treated as spread-free.

---

### User Story 2 - Correct spread group applied to every currency (Priority: P2)

A user requests a quote for a currency pair drawn from any of the groups defined in Appendix B (the
3.25% group, the 4.50% group, the 6.00% group) or for a currency not listed in any group. The
calculation must apply the correct group percentage, and any currency absent from every explicit
group must fall back to the 2.75% default.

**Why this priority**: Ensures the full spread table — not just the EUR/USD correction — matches the
documented business rules, since incorrect group membership would silently misprice quotes.

**Independent Test**: For one currency from each Appendix B group and for a currency deliberately
absent from all groups, request a quote and confirm the applied spread matches the expected
percentage for that group (or the 2.75% default).

**Acceptance Scenarios**:

1. **Given** a currency in the {JPY, HKD, KRW} group, **When** it is priced against another currency,
   **Then** its spread is 3.25%.
2. **Given** a currency in the {MYR, INR, MXN} group, **When** it is priced against another currency,
   **Then** its spread is 4.50%.
3. **Given** a currency in the {RUB, CNY, ZAR} group, **When** it is priced against another currency,
   **Then** its spread is 6.00%.
4. **Given** a currency that appears in none of the explicit groups and is not EUR, **When** it is
   priced against another currency, **Then** its spread is the 2.75% default.

---

### User Story 3 - Ingestion rejects inconsistent provider payloads (Priority: P3)

When the daily rate fetch runs, the system checks that the provider's declared base currency is EUR
before accepting the payload. If the provider ever returns a different or missing base currency, or a
payload that is otherwise inconsistent with an EUR base, the fetch must fail clearly rather than
silently storing rates under a wrong assumption.

**Why this priority**: Protects the correctness guarantees of User Stories 1 and 2 going forward — a
silently-accepted bad payload would reintroduce the same class of pricing error the other two stories
fix. Lower priority than the pricing fixes themselves because it addresses a safeguard against a
lower-likelihood provider-side failure, not the currently observed defect.

**Independent Test**: Simulate a provider response whose declared base currency is not EUR (or is
missing) and confirm the ingestion run fails with a clear error and stores no rates from that run;
separately confirm a normal EUR-based payload continues to ingest successfully.

**Acceptance Scenarios**:

1. **Given** a provider response with base currency EUR and a rates map that includes EUR, **When**
   ingestion runs, **Then** all rates are stored as before, with EUR's rate taken directly from the
   provided map (no synthetic EUR entry added).
2. **Given** a provider response whose declared base currency is not EUR, **When** ingestion runs,
   **Then** the run fails with a clear, actionable error and no rates from that response are
   persisted.
3. **Given** a provider response with a missing or malformed base-currency field, **When** ingestion
   runs, **Then** the run fails with a clear, actionable error and no rates from that response are
   persisted.

---

### Edge Cases

- What happens when a currency code is listed in more than one spread group by mistake? The reference
  configuration must make this detectable (e.g., fail fast or be structurally impossible), since it
  would make the correct spread ambiguous.
- How does the system handle currency codes in different letter casing (e.g., "eur" vs "EUR") when
  looking up spread and validating the base currency?
- What happens on a day when the provider payload passes the EUR base-currency check but is missing
  rates for currencies the system otherwise expects? (Out of scope for this feature beyond the base
  currency check itself — existing missing-rate handling is preserved.)
- What happens if the internal normalization currency (USD) is itself absent from the provider's
  rates map on a given day? (Out of scope for this feature — existing normalization error handling is
  preserved; this feature only governs which currency is spread-free.)

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: System MUST apply a 0% spread to EUR in every spread-adjusted rate calculation,
  reflecting EUR's status as the data provider's base currency.
- **FR-002**: System MUST NOT apply a 0% spread to USD on the basis of USD being used for internal
  rate normalization; USD's spread MUST be governed by the same group/default rules as any other
  non-EUR currency.
- **FR-003**: System MUST apply a 3.25% spread to JPY, HKD, and KRW.
- **FR-004**: System MUST apply a 4.50% spread to MYR, INR, and MXN.
- **FR-005**: System MUST apply a 6.00% spread to RUB, CNY, and ZAR.
- **FR-006**: System MUST apply a 2.75% default spread to any currency that is neither EUR nor a
  member of one of the explicit spread groups.
- **FR-007**: The base currency designation and all spread group memberships and percentages MUST be
  defined as fixed reference configuration, changeable without altering the calculation logic, and
  MUST NOT require a runtime editing capability or database-backed administration feature.
- **FR-008**: The concept of "the provider's base currency" (EUR) MUST be represented separately from
  "the currency used for internal rate normalization" (USD), such that changing the normalization
  currency has no effect on spread assignment.
- **FR-009**: During ingestion, the system MUST verify that the provider's declared base currency is
  EUR before accepting a fetch's data.
- **FR-010**: When the provider's declared base currency is missing, not EUR, or inconsistent with the
  rest of the payload, ingestion MUST fail with a clear, actionable error and MUST NOT persist any
  rates from that fetch.
- **FR-011**: Ingestion MUST continue to persist EUR's rate directly from the provider's rates map and
  MUST NOT synthesize a separate EUR entry.
- **FR-012**: Existing exchange-rate and historical-rate API request/response contracts MUST remain
  unchanged by this feature.
- **FR-013**: Existing historical rate storage (schema, upsert-on-duplicate behavior, rate-date
  semantics) MUST be preserved unchanged by this feature.

### Key Entities

- **Spread Reference Configuration**: The fixed mapping used to price a currency — identifies EUR as
  the zero-spread base currency, lists each spread group's member currencies and percentage, and
  defines the default percentage applied to any currency not otherwise listed. Read-only at runtime;
  changed only by updating the configuration source itself.
- **Provider Ingestion Payload**: The daily data received from the exchange rate provider — includes
  a declared base currency and a map of currency codes to rate values. Its declared base currency is
  checked against the expected value (EUR) before any rates from it are accepted.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: 100% of exchange rate quotes involving EUR apply a 0% spread for EUR.
- **SC-002**: 100% of exchange rate quotes involving USD (without EUR in the pair) apply USD's
  correctly configured spread rather than a zero spread.
- **SC-003**: For a representative currency drawn from each Appendix B spread group and for at least
  one unlisted currency, 100% of test quotes apply the expected percentage (3.25%, 4.50%, 6.00%, or
  the 2.75% default, respectively).
- **SC-004**: Adding a new currency to a spread group or changing the default percentage can be done
  by editing only the reference configuration, with zero changes to calculation logic — verified by a
  test that exercises the configuration directly.
- **SC-005**: 100% of simulated ingestion runs with a non-EUR or missing base currency are rejected
  with a clear error and result in zero newly persisted rates.
- **SC-006**: All pre-existing exchange-rate and historical-data API behaviors continue to pass their
  existing regression tests unchanged after this feature ships.

## Assumptions

- Per Appendix B and Fixer.io's documented behavior, the provider always returns EUR as the base
  currency under the plan this system uses; EUR is therefore the correct fixed zero-spread currency,
  not a value that needs to vary per deployment.
- The currency used for internal rate normalization (USD) remains an implementation detail of how
  rates are computed and stored; it is not itself a business rule and this feature does not change it,
  beyond ensuring it can no longer be conflated with the spread-free base currency.
- "Configuration" in this context means a fixed, version-controlled reference source (e.g. a config
  file or in-code data structure reviewed like any other change) — not an admin UI or database table,
  per the explicit scope limit in the feature description.
- No currencies beyond those already listed in Appendix B need to be pre-populated into spread
  groups; any newly encountered currency code is expected to fall back to the 2.75% default until a
  future business decision adds it to a group.
- The scope of this feature is the correctness of spread assignment and ingestion validation; it does
  not change the exchange-rate calculation formula itself (Section 6.1 of TASK.md), the API contracts,
  or historical storage semantics.
