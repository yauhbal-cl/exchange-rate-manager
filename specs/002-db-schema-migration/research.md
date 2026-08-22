# Phase 0 Research: Database Migration Tool, Schema, and Persistence Model

## Migration tool: Flyway vs Liquibase

**Decision**: Flyway (`flyway-core` + `flyway-database-postgresql`), user-directed.

**Rationale**: Plain SQL migrations map directly onto the constraint-heavy schema this feature
needs (composite unique constraints, CHECK constraints, NUMERIC precision) without a DSL
translation layer. Spring Boot auto-configures Flyway on the classpath and runs migrations at
startup by default, satisfying FR-001/FR-002 with no extra wiring. As of Spring Boot 4.1.x,
`flyway-database-postgresql` must be added explicitly alongside `flyway-core` (PostgreSQL support
was split out in Flyway 10+).

**Alternatives considered**: Liquibase — more format flexibility (XML/YAML/JSON) and built-in
rollback tags, but adds a DSL for what are here straightforward DDL statements; team has no
existing Liquibase changelogs to build on. Rejected in favor of the simpler, user-directed choice.

## ShedLock schema provisioning

**Decision**: Add ShedLock's required lock table (`shedlock`) as its own Flyway migration
(`V3__create_shedlock.sql`), using the JDBC-template provider's expected column shape (`name`
varchar PK, `lock_until`, `locked_at`, `locked_by` timestamps/varchar).

**Rationale**: ShedLock's JDBC provider expects a specific table shape it does not create itself;
versioning it through the same migration chain as the rest of the schema keeps one source of
truth for schema state (FR-009) and avoids a separate manual setup step. The scheduled-job code
that actually uses this table is out of scope for this feature (later ingestion feature).

**Alternatives considered**: Let ShedLock auto-create its table via a startup hook — rejected,
bypasses migration versioning and would not be tracked by Flyway's history table, risking drift
detection gaps (FR-003).

## Schema drift / checksum mismatch detection

**Decision**: Rely on Flyway's built-in checksum validation (`flyway.validateOnMigrate=true`,
the Spring Boot default) to fail startup when applied migration checksums don't match the
migration files on disk.

**Rationale**: This is exactly the "fail loudly on drift" behavior FR-003 and the schema-drift
edge case require, and it's Flyway's default behavior — no custom code needed.

**Alternatives considered**: Custom checksum/hash verification in application code — rejected as
unnecessary duplication of what Flyway already guarantees.

## Numeric precision for rate values

**Decision**: `NUMERIC(19,6)` for `ExchangeRate.rateToUsd`, mapped to `java.math.BigDecimal`
(resolved in `/speckit-clarify`).

**Rationale**: 6 decimal places matches typical FX-API precision (Fixer.io reports up to 6
significant decimals); 13 integer digits comfortably covers high-magnitude currencies (e.g.
Vietnamese Dong, Iranian Rial) without realistic overflow risk.

**Alternatives considered**: `NUMERIC(10,4)` (rejected — insufficient integer digits for
high-magnitude currencies); `NUMERIC(20,10)` (rejected — precision beyond what any known rate
provider returns, wastes storage for no correctness benefit).

## Currency code validation at the schema level

**Decision**: `CHAR(3)` column type plus a `CHECK (currency_code ~ '^[A-Z]{3}$')` constraint on
both `exchange_rates.currency_code` and `currency_usage.currency_code`.

**Rationale**: Enforces FR-012 (ISO-4217-style 3-letter code) at the database level, so malformed
codes are rejected regardless of which application code path writes them.

**Alternatives considered**: Application-layer-only validation (Bean Validation `@Pattern`) —
kept as a defense-in-depth addition on the entity, but not relied on alone per FR-012's
"schema MUST constrain" wording.

## Test strategy for repository-layer verification

**Decision**: `@DataJpaTest` (or `@SpringBootTest` where Flyway migrations must run) against the
existing docker-compose PostgreSQL instance, not an in-memory H2 substitute.

**Rationale**: The correctness being tested (unique constraints, CHECK constraints, NUMERIC
precision) is PostgreSQL-specific DDL behavior; an in-memory substitute database would not
reliably reproduce constraint violations or precision handling, defeating the point of the test.

**Alternatives considered**: H2 in PostgreSQL-compatibility mode — rejected, known gaps in CHECK
constraint and NUMERIC precision fidelity versus real PostgreSQL. Testcontainers — a cleaner
long-term fit (ephemeral, CI-friendly Postgres) but not yet a project dependency; introducing it
is a reasonable follow-up, not required to satisfy this feature's acceptance criteria against the
already-running docker-compose database.
