# Quickstart: Validating the EUR Base Currency Spread Correction

Prerequisites: local Postgres running (`docker compose up -d` from repo root), `FIXER_API_KEY` set,
backend buildable (`cd backend && ./mvnw -q compile`).

## 1. Automated tests (primary validation)

```bash
cd backend
./mvnw test -Dtest=ExchangeRatePropertiesTest,SpreadLookupTest,RateCollectionServiceTest
```

Expected: all pass. Together these cover the spec's acceptance scenarios —
`SpreadLookupTest` covers User Stories 1 & 2 (EUR → 0%, USD → default 2.75%, every Appendix B group,
an unlisted currency → default), `RateCollectionServiceTest` covers User Story 3 (EUR-based payload
accepted, non-EUR/missing base rejected before any upsert), and `ExchangeRatePropertiesTest` covers
the startup validation rules (requirement 3).

## 2. Startup validation (fail-fast on bad config)

Temporarily break the invariant to prove it's enforced, then restore it:

```bash
cd backend
# Point defaultSpreadPercent (or any spread) out of range, or drop EUR: 0.00 from `spreads`,
# in src/main/resources/application.yml, then:
./mvnw spring-boot:run
```

Expected: the application fails to start with a Bean Validation error identifying the violated
constraint (e.g. "must be equal to 0" on the base-currency-spread invariant, or the pattern/range
violation on the edited field). Revert the edit before continuing.

## 3. End-to-end spread behavior

```bash
cd backend && ./mvnw spring-boot:run &
# once up, and after at least one successful rate collection run (scheduled or manual refresh):
curl -s "http://localhost:8080/exchange?from=EUR&to=PLN" | jq .exchange
curl -s "http://localhost:8080/exchange?from=USD&to=PLN" | jq .exchange
```

Expected: the `EUR/PLN` quote reflects a 0% spread contribution from EUR (only PLN's own spread, if
higher, affects the result). The `USD/PLN` quote reflects USD's real configured spread (the 2.75%
default, unless USD is explicitly listed), not a 0% spread — this is the defect this feature fixes.

## 4. Ingestion rejects a bad provider payload

Covered by `RateCollectionServiceTest` with a mocked `FixerClient` returning a
`FixerLatestResponse` whose `base` is `"USD"` or `null` — no live Fixer.io call needed to validate
this path. Confirm via the test output that `FixerApiException` is thrown and
`ExchangeRateRepository.upsert(...)` is never invoked for that run (verified via mock interaction
verification, e.g. Mockito `verifyNoInteractions`/`never()`).

## Related documents

- Spread/base-currency config schema: [data-model.md](./data-model.md)
- Design decisions and rationale: [research.md](./research.md)
- Full requirements: [spec.md](./spec.md)
