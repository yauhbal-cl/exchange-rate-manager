# Quickstart: Validate Migration, Schema, and Persistence Model

## Prerequisites

- `docker compose up -d` from repo root (PostgreSQL 17 running)
- `backend/src/main/resources/application.yml` (or `application-local.yml`) pointing at that
  Postgres instance, with Flyway enabled (Spring Boot default once `flyway-core` is on the
  classpath)

## 1. Fresh schema creation (User Story 1, SC-001)

```bash
docker compose down -v && docker compose up -d   # empty database
cd backend && ./mvnw spring-boot:run
```

Expected: startup logs show Flyway applying `V1`, `V2`, `V3` in order; application starts with no
errors. Verify tables exist:

```bash
docker compose exec postgres psql -U postgres -d exchange_rate_manager -c '\d exchange_rates'
docker compose exec postgres psql -U postgres -d exchange_rate_manager -c '\d currency_usage'
docker compose exec postgres psql -U postgres -d exchange_rate_manager -c '\d shedlock'
```

## 2. Idempotent restart (SC-004)

```bash
# stop the app (Ctrl+C), then start it again against the same database
cd backend && ./mvnw spring-boot:run
```

Expected: startup logs show no new migrations applied (`Schema ... is up to date`), no errors.

## 3. Duplicate rate rejection (User Story 2, SC-002)

```bash
docker compose exec postgres psql -U postgres -d exchange_rate_manager -c \
  "INSERT INTO exchange_rates (currency_code, rate_to_usd, rate_date) VALUES ('EUR', 0.9200, '2026-08-20');"
docker compose exec postgres psql -U postgres -d exchange_rate_manager -c \
  "INSERT INTO exchange_rates (currency_code, rate_to_usd, rate_date) VALUES ('EUR', 0.9300, '2026-08-20');"
```

Expected: second insert fails with a unique-constraint violation on `(currency_code, rate_date)`.

## 4. Precision round-trip (SC-003)

```bash
docker compose exec postgres psql -U postgres -d exchange_rate_manager -c \
  "INSERT INTO exchange_rates (currency_code, rate_to_usd, rate_date) VALUES ('JPY', 149.123456, '2026-08-21');"
docker compose exec postgres psql -U postgres -d exchange_rate_manager -c \
  "SELECT rate_to_usd FROM exchange_rates WHERE currency_code = 'JPY' AND rate_date = '2026-08-21';"
```

Expected: returned value is exactly `149.123456`, no rounding.

## 5. Repository access from application code (User Story 3)

Run the repository tests:

```bash
cd backend && ./mvnw test -Dtest=ExchangeRateRepositoryTest,CurrencyUsageRepositoryTest
```

Expected: both test classes pass, covering save-then-find-by-natural-key for each entity (see
`data-model.md` for the exact repository method signatures).

## 6. Non-positive rate / bad currency code rejection (Edge Cases)

```bash
docker compose exec postgres psql -U postgres -d exchange_rate_manager -c \
  "INSERT INTO exchange_rates (currency_code, rate_to_usd, rate_date) VALUES ('USD', 0, '2026-08-22');"
docker compose exec postgres psql -U postgres -d exchange_rate_manager -c \
  "INSERT INTO exchange_rates (currency_code, rate_to_usd, rate_date) VALUES ('usd1', 1.0, '2026-08-22');"
```

Expected: both inserts fail their respective `CHECK` constraints.
