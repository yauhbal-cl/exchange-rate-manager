# Phase 1 Data Model: Database Migration Tool, Schema, and Persistence Model

## ExchangeRate

Represents a single currency's rate relative to the base currency (USD) on a specific date.

| Field | Type (Java / column) | Constraints |
|---|---|---|
| `id` | `Long` / `BIGSERIAL` | Primary key, surrogate |
| `currencyCode` | `String` / `CHAR(3)` | NOT NULL, `CHECK (currency_code ~ '^[A-Z]{3}$')` |
| `rateToUsd` | `BigDecimal` / `NUMERIC(19,6)` | NOT NULL, `CHECK (rate_to_usd > 0)` |
| `rateDate` | `LocalDate` / `DATE` | NOT NULL — the provider-reported date, not fetch date (Constitution II) |
| `createdAt` | `Instant` / `TIMESTAMPTZ` | NOT NULL, default `now()` (audit only, not business data) |

**Constraints**:
- Unique composite index on `(currency_code, rate_date)` — backs FR-005 and the upsert path a
  later ingestion feature will use.

**Repository**: `ExchangeRateRepository extends JpaRepository<ExchangeRate, Long>` with
`Optional<ExchangeRate> findByCurrencyCodeAndRateDate(String currencyCode, LocalDate rateDate)`.

## CurrencyUsage

Tracks how often a currency has been queried through the rate API.

| Field | Type (Java / column) | Constraints |
|---|---|---|
| `id` | `Long` / `BIGSERIAL` | Primary key, surrogate |
| `currencyCode` | `String` / `CHAR(3)` | NOT NULL, UNIQUE, `CHECK (currency_code ~ '^[A-Z]{3}$')` |
| `queryCount` | `Long` / `BIGINT` | NOT NULL, default `0`, `CHECK (query_count >= 0)` |
| `lastQueriedAt` | `Instant` / `TIMESTAMPTZ` | NOT NULL, default `now()` |

**Repository**: `CurrencyUsageRepository extends JpaRepository<CurrencyUsage, Long>` with
`Optional<CurrencyUsage> findByCurrencyCode(String currencyCode)`. The atomic
`INSERT ... ON CONFLICT (currency_code) DO UPDATE SET query_count = query_count + 1` used by the
rate-API feature is out of scope here — this feature only provides the table, constraint, and
read-path repository method.

## Scheduler Lock (`shedlock`)

Not an application entity — no JPA mapping. Created purely as a table for the ShedLock
JDBC-template provider to read/write directly.

| Column | Type | Notes |
|---|---|---|
| `name` | `VARCHAR(64)` | Primary key — lock name |
| `lock_until` | `TIMESTAMP(3)` | |
| `locked_at` | `TIMESTAMP(3)` | |
| `locked_by` | `VARCHAR(255)` | |

## Relationships

`ExchangeRate` and `CurrencyUsage` are independent tables, both keyed by `currency_code` but with
no foreign-key relationship — usage counting is not scoped to specific rate rows. No relationship
to `shedlock`.

## Migration file plan

- `V1__create_exchange_rates.sql` — table, checks, composite unique index
- `V2__create_currency_usage.sql` — table, checks, unique index
- `V3__create_shedlock.sql` — ShedLock's required table shape
