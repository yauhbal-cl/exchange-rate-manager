# Research: Fixer.io Data Collection

## 1. HTTP client for calling Fixer.io

**Decision**: Use Spring's `RestClient` (synchronous), built into `spring-boot-starter-web`
(already a project dependency) — no new HTTP client library needed.

**Rationale**: The collection job is a single daily batch call, not a high-concurrency or
streaming workload. `RestClient` is the current idiomatic Spring Boot 4.x choice for synchronous
outbound calls (successor to `RestTemplate`), needs no reactive stack, and is trivially testable
with `MockRestServiceServer`.

**Alternatives considered**: `WebClient` (reactive) — rejected, adds reactor dependency/complexity
for a once-a-day sequential call with no benefit. `OpenFeign` — rejected, an extra dependency for
a single external endpoint is unjustified abstraction.

## 2. Fixer.io free-tier base-currency constraint

**Decision**: Fixer.io's free/basic plan only allows `EUR` as the request `base` parameter (paid
plans allow arbitrary base). Call `GET /latest?access_key=...` (no `base` param → defaults to
EUR, or explicitly `base=EUR`) with `symbols=<all target codes + USD>` to get EUR→X rates for
every supported currency in one call, then derive each currency's rate to USD via cross-rate:
`rateToUsd(X) = eurToX / eurToUsd`. `USD` itself stores `rateToUsd = 1.000000`.

**Rationale**: The persistence model (`exchange_rates.rate_to_usd`, per spec 002) requires a
USD-relative rate, but the provider's free tier cannot be queried with `base=USD` directly. The
cross-rate calculation is the standard technique for this constraint and requires only one API
call per run (preserving FR-004/quota constraints).

**Alternatives considered**: Requesting `base=USD` directly — rejected, unavailable on the
free tier (returns a plan-restriction error), confirmed by Fixer.io's published API docs.
Making one call per target currency with `base` set per currency — rejected, would multiply calls
1-per-currency and blow the single-call-per-run budget this feature is built around.

## 3. Response currency set

**Decision**: Use whatever currency set the provider actually returns in the `/latest` response
`rates` object (per spec Assumption: no manual allow-list). Omitting `symbols` returns the full
provider-supported set; the collection job iterates over `response.rates.keySet()` plus computes
`USD` itself.

**Rationale**: Matches FR-005 (full provider-supported set, no hard-coded subset) and Appendix B's
implicit assumption of a comprehensive spread lookup ("all other currencies" catch-all).

## 4. Multi-instance coordination

**Decision**: ShedLock, already wired at the dependency/migration level (V3 migration,
`shedlock-spring` + `shedlock-provider-jdbc-template` in `pom.xml`). This feature adds:
`@EnableSchedulerLock(defaultLockAtMostFor = "PT10M")` on the application/config class, a
`LockProvider` bean (`JdbcTemplateLockProvider` over the existing `DataSource`), and
`@SchedulerLock(name = "fixer-rate-collection")` on the scheduled method. The manual-refresh
service method reuses the same lock name so a manual trigger and a concurrent scheduled run can
never overlap (FR-010).

**Rationale**: This is the constitution's prescribed mechanism (Principle IV) and the
infrastructure is already in place from spec 002 — this feature only needs to consume it.

**Alternatives considered**: A hand-rolled DB advisory lock — rejected, ShedLock already
provisioned specifically for this purpose; reinventing it would duplicate existing infrastructure.

## 5. Upsert mechanism

**Decision**: A native `INSERT ... ON CONFLICT (currency_code, rate_date) DO UPDATE SET
rate_to_usd = EXCLUDED.rate_to_usd` statement, issued once per currency via a `@Modifying
@Query(nativeQuery = true)` method on `ExchangeRateRepository`, inside one transaction per
collection run.

**Rationale**: Matches the DB-level unique constraint from spec 002 (`uq_exchange_rates_currency_date`)
and Constitution Principle III (upsert, not raw insert). A native upsert is atomic and avoids a
read-then-write race between concurrent runs (defense in depth alongside ShedLock).

**Alternatives considered**: `findByCurrencyCodeAndRateDate` then save/update in Java — rejected,
introduces a check-then-act race and duplicates the upsert-safety the DB constraint already
provides for free via `ON CONFLICT`.

## 6. Error handling / partial failure

**Decision**: The Fixer.io client call itself (network error, non-2xx, malformed JSON, or a
provider-reported error payload) is wrapped in a single try/catch at the collection-service level.
On failure, the entire run aborts before any writes happen (the cross-rate table is computed
in-memory first, then persisted), logging at `ERROR` level with the failure cause. If the call
succeeds but `rates` is missing entries for some expected currencies, those currencies are simply
skipped (not present in the map to iterate over) — the run still persists whichever currencies
were present and completes normally, satisfying FR-006/FR-007 without special-casing "partial".

**Rationale**: Because rates are computed from one response, "fetch, compute, then write" leaves
nothing to roll back on total failure — the write step never starts. Missing-currency handling
falls out naturally from iterating the map instead of a hard-coded currency list (FR-005 also
depends on this).

**Alternatives considered**: Row-by-row try/catch during persistence — rejected as unnecessary
complexity; a fetch failure is all-or-nothing before any DB writes occur, so there's no partial
persistence failure mode to guard against beyond the missing-currency case already handled by
iterating the response map.

## 7. Manual refresh endpoint contract

**Decision**: Add `POST /exchange/refresh` to `contracts/openapi.yaml` (per TASK.md §4.4,
optional extension) returning a small summary body (currencies collected count, rate date) on
success, reusing the same `ProblemDetail` error shape as other endpoints on failure. The
controller delegates to the same collection service method as the scheduler, wrapped in the
same `@SchedulerLock`-guarded path so a manual call can't race a scheduled run.

**Rationale**: CLAUDE.md requires contract-first changes; TASK.md explicitly names this endpoint
and shape as optional but describes its behavior (trigger fetch/upsert, don't touch usage
counters — satisfied by simply not calling any usage-counter code, which belongs to a separate
feature).

**Alternatives considered**: No manual endpoint (deferring to a later feature) — rejected, User
Story 3 in the spec calls it P3 in-scope for this feature, not a separate feature slice.

## 8. Testing strategy

**Decision**: `MockRestServiceServer` for the Fixer.io client (contract of the exact request URL
and a canned JSON response), a plain unit test for the EUR-cross-rate math, and a
`@SpringBootTest` (or focused slice test) against the real Postgres (docker-compose) for the
upsert repository method — consistent with `ExchangeRateRepositoryTest`'s existing pattern from
spec 002. A lightweight test confirms the manual-refresh endpoint and the scheduled job share the
same lock name (reflection/config assertion, not a live concurrency test).

**Rationale**: Matches the testing approach already established in this codebase (spec 002 used
real-Postgres repository tests, no Testcontainers yet); keeps the new test surface consistent
rather than introducing a second testing paradigm.
