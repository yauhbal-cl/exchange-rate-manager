# Quickstart: Historical Exchange Rate Trends

Validates the feature end-to-end against a running backend. See `data-model.md` for state shapes
and `contracts/ui-contract.md` for the full behavioral contract this exercises.

## Prerequisites

- Local infra up: `docker compose up -d` (PostgreSQL; add Ollama if not already running — the AI
  scenarios below need it)
- Backend running with several months of ingested rate history for at least one pair (e.g.
  USD/EUR): `cd backend && ./mvnw spring-boot:run`; use `POST /exchange/refresh` repeatedly (or
  let the daily scheduler accumulate history) if the DB is thin — see `specs/003-fixer-data-collection`
- Frontend deps installed and Chart.js added: `cd frontend && npm install`
- Generated API client up to date (no contract changes expected, but confirm): `cd frontend && npm
  run generate:api`

## Run

```bash
cd frontend && npm start
```

Navigate to `http://localhost:4200/historical-rates`.

## Scenario 1 — Default view on load (User Story 1, FR-007, FR-008, FR-010)

1. Open the view with no prior selection.
2. **Expect**: USD/EUR pre-selected, 1M preset active, layout order title → filters → summary
   metrics → chart (with AI panel beside it) → table; metrics show latest rate, period change %,
   period high (+ date), period low (+ date); chart y-axis does not start at zero.

## Scenario 2 — Changing the pair updates everything (User Story 1 Acceptance Scenario 3, FR-002)

1. Change the base currency (e.g. USD → GBP).
2. **Expect**: exactly one new `/exchange/trend` call (check Network tab); metrics, chart, and
   table all update to the new pair for the same period; no separate submit action needed.
3. Set the quote currency equal to the base currency.
   **Expect**: a clear validation message is shown; Network tab shows no new `/exchange/trend`
   request (FR-002).

## Scenario 3 — Presets and custom range (User Story 2, FR-004–FR-006, SC-004)

1. Click each preset (7D, 15D, 1M, 3M, 6M) in turn.
   **Expect**: chart/table span changes accordingly each time, ending on the most recent
   available date.
2. Switch to custom range, pick a valid ≤6-month span.
   **Expect**: chart/table/metrics reflect exactly that range.
3. Pick a custom range >6 months.
   **Expect**: validation message shown; Network tab shows no new `/exchange/trend` request.
4. Pick a custom range with start after end.
   **Expect**: validation message shown; no new request.

## Scenario 4 — Swap (User Story 3, FR-003, SC-002)

1. With USD/EUR selected and a trend showing, generate an AI insight (see Scenario 6) so a
   narrative is visible.
2. Click the swap control.
   **Expect**: pair becomes EUR/USD, one new `/exchange/trend` call fires, metrics/chart/table
   refresh for the swapped pair using the same period, and the previously-shown AI narrative is
   immediately cleared (not left attached to the new pair).

## Scenario 5 — No data (User Story 1 Acceptance Scenario 2, User Story 4 Acceptance Scenario 2, FR-015)

1. Pick a custom range with no ingested data (e.g. a far-future range, or a currency never
   collected).
2. **Expect**: metrics, chart, and table all show an explicit "no data" state — no blank screen,
   no crash, no fabricated values. "Generate insight" is disabled.

## Scenario 6 — AI Insights, explicit-only (User Story 5, FR-011–FR-014, SC-005, SC-006)

1. With data showing, confirm no narrative appears until requested (FR-011).
2. Click "Generate insight".
   **Expect**: exactly one `/exchange/trend/insight` call; a short narrative appears grounded in
   the currently displayed dates/values.
3. Change the period (e.g. switch preset).
   **Expect**: the narrative is cleared immediately; a new one only appears after clicking
   "Generate insight" again (FR-014).
4. Stop Ollama (or point the backend at an unreachable model endpoint), then click "Generate
   insight" again.
   **Expect**: a clear "interpretation unavailable" message, not a fabricated narrative or a raw
   technical error (FR-013, SC-005). Restart Ollama afterward.

## Scenario 7 — Table/chart consistency (User Story 4, FR-009, SC-003)

1. With any pair/period showing N chart points, open the table.
2. **Expect**: exactly N rows, most-recent-first, each date/rate matching a chart point exactly;
   each row (except the oldest shown) has a signed daily % change.

## Automated check

```bash
cd frontend && npm test -- historical-rates
```

Covers preset/custom-range resolution and validation (`period-presets.spec.ts`), derived-metric
computation including the high/low/percent-change boundary cases (`trend-metrics.spec.ts`), and
the component-level behavioral contract from `contracts/ui-contract.md` (auto-refresh on
pair/period change, swap clearing AI state, explicit-only AI triggering, no-data states)
(`historical-rates.spec.ts`).
