# Phase 1 Data Model: EUR Base Currency Spread Correction

This feature introduces no persisted entities and no schema change. The only new "model" is an
in-memory, validated configuration object bound from `application.yml`. This document describes
that config schema and the existing entity it must stay consistent with.

## ExchangeRateProperties (new — config, not persisted)

Immutable, validated, bound from the `exchange-rates` prefix in `application.yml`.

| Field | Type | Validation | Meaning |
|---|---|---|---|
| `baseCurrency` | `String` | `@NotBlank`, `@Pattern(regexp = "^[A-Z]{3}$")` | The exchange rate provider's (Fixer.io's) business base currency — the currency that always carries a 0% spread. Fixed at `EUR`. |
| `defaultSpreadPercent` | `BigDecimal` | `@NotNull`, `>= 0`, `< 100` | Spread percentage applied to any currency not explicitly listed in `spreads`. Fixed at `2.75`. |
| `spreads` | `Map<String, BigDecimal>` | `@NotEmpty`; every key matches `^[A-Z]{3}$`; every value `>= 0` and `< 100` | Explicit per-currency spread overrides — Appendix B's groups (3.25%, 4.50%, 6.00%) plus an explicit `EUR: 0.00` entry. |

**Class-level invariant**: `spreads.get(baseCurrency)` MUST exist and equal `0` (enforced via a
Bean Validation `@AssertTrue` method) — the configured base currency must always be present in the
spread map with a spread of exactly zero. This is what makes "EUR is spread-free" a config-verified
fact rather than an assumption `SpreadLookup` has to hardcode.

**Lifecycle**: Loaded once at application startup from `application.yml`; immutable for the
lifetime of the application context. No setters, no runtime mutation path, no database table — per
the spec's explicit "fixed reference configuration, no runtime editing" scope limit.

**Relationships**: Consumed by `SpreadLookup` (spread-percentage lookups) and by
`RateCollectionService` (`baseCurrency` only, for the Fixer ingestion base-currency check).

## FixerLatestResponse (existing — unchanged shape)

No fields added or removed. Documented here only because this feature adds new *behavior* around
one existing field:

| Field | Type | New behavior in this feature |
|---|---|---|
| `base` | `String` | Compared against `ExchangeRateProperties.baseCurrency()` at ingestion time. A `null`, blank, or non-matching value now causes `RateCollectionService.collect()` to throw `FixerApiException` before any rate is read from `rates` or upserted. |
| `rates` | `Map<String, BigDecimal>` | The entry keyed by the configured base currency (`EUR`) is additionally checked to numerically equal `1` (via `compareTo`, not `equals`) as a payload-consistency sanity check (see research.md). |

## ExchangeRate (existing entity — unchanged)

No changes. Still stores `currencyCode`, `rateToUsd` (the internal USD-normalized value — unrelated
to spread policy after this feature, per the naming separation in research.md), and `rateDate`,
upserted on the existing `(currencyCode, rateDate)` composite key. This feature does not add a
`baseCurrency` column to this table or any other row-level base-currency field — the base currency
is fixed, singular, reference configuration, not per-row data (per the spec's explicit "do not
repeat the base currency on every exchange-rate row" constraint).
