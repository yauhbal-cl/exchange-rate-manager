# Data Model: Fixer.io Data Collection

This feature does not introduce new persisted tables/entities — it populates the `exchange_rates`
table already created in spec 002 (`db-schema-migration`). No new Flyway migration is required.

## Existing Entity Reused: `ExchangeRate`

Source: `backend/src/main/java/com/exchangerate/manager/entity/ExchangeRate.java` (spec 002,
unchanged by this feature).

| Field | Type | Notes |
|---|---|---|
| `id` | `Long` | Surrogate PK, DB-generated |
| `currencyCode` | `String` (CHAR(3)) | ISO-style 3-letter code; `USD` included |
| `rateToUsd` | `BigDecimal` (`NUMERIC(19,6)`) | Computed via EUR cross-rate (research.md §2); this feature's job is to populate it correctly |
| `rateDate` | `LocalDate` | The date Fixer.io's response reports (`response.date`), not the fetch date |
| `createdAt` | `Instant` | DB-generated, unaffected by upsert `DO UPDATE` |

**Uniqueness**: `(currency_code, rate_date)` — enforced by the existing
`uq_exchange_rates_currency_date` index. This feature's upsert query targets that constraint.

## New Repository Method

Add to `ExchangeRateRepository` (no new file):

- `upsert(String currencyCode, BigDecimal rateToUsd, LocalDate rateDate)` — `@Modifying
  @Query(nativeQuery = true)`, `INSERT ... ON CONFLICT (currency_code, rate_date) DO UPDATE SET
  rate_to_usd = EXCLUDED.rate_to_usd`. Called once per currency within one `@Transactional`
  collection-run method.

## Transient (non-persisted) Shapes

These exist only in memory / on the wire during a collection run — not database entities.

### `FixerLatestResponse` (deserialization target for the provider's `/latest` response)

| Field | Type | Notes |
|---|---|---|
| `success` | `boolean` | Provider-reported call success flag |
| `base` | `String` | Expected to be `EUR` (free-tier constraint, research.md §2) |
| `date` | `LocalDate` | The rate date to persist (FR-002) |
| `rates` | `Map<String, BigDecimal>` | EUR→X rates for every currency the provider returned; drives which currencies get collected (FR-005) |
| `error` | `FixerError` (nullable) | Present only when `success = false`; `{ code, type, info }` per Fixer.io's documented error envelope |

### `RefreshResult` (manual-refresh endpoint response body / internal return value)

| Field | Type | Notes |
|---|---|---|
| `currenciesCollected` | `int` | Count of currencies successfully upserted this run |
| `rateDate` | `LocalDate` | The rate date collected/updated |

## Relationships to Other Features

- **Consumed by** the Exchange Rate API feature (spread-adjusted `GET /exchange`) and the AI
  Trend Insight feature — both read `exchange_rates` rows this feature writes.
- **Does not touch** `CurrencyUsage` — per FR-009/Constitution, collection (scheduled or manual)
  must never increment usage counters; that table is out of scope here entirely.
