# UI Contract: Historical Exchange Rate Trends

This feature adds no new backend endpoint and changes no schema in `contracts/openapi.yaml`. The
contract below is the *view's* observable behavior — its inputs, outputs, and the exact backend
calls it's allowed to make — so implementation and tests have a shared reference.

## Backend calls (unchanged, existing contracts)

- **`GET /exchange/trend`** (`contracts/openapi.yaml` lines 105–154), consumed only via
  `ExchangeRateAnalyticsService.getExchangeRateTrend(from, to, startDate, endDate)`. Called
  automatically whenever the resolved pair/range changes (never gated behind a submit button).
- **`GET /exchange/trend/insight`** (`contracts/openapi.yaml` lines 155–215), consumed only via
  `ExchangeRateAIInsightService.getExchangeRateTrendInsight(from, to, startDate, endDate)`. Called
  **only** in response to an explicit "Generate insight" click — never automatically, never on
  pair/period/swap change (FR-011).
- No other generated service/method may be called from this view.

## Layout order (traces to FR-010)

Top to bottom: page title + supporting text → currency pair selectors + swap control → period
presets/custom range picker → summary metrics row → chart (≈65–70% width) beside AI Insights
panel (≈30–35% width) on desktop, stacked (chart above panel) below a `lg` breakpoint → raw
historical rates table.

## Component public surface

`HistoricalRates` (`frontend/src/app/features/historical-rates/historical-rates.ts`) — standalone
component, routed at path `historical-rates`.

| Element | Selector contract | Behavior |
|---|---|---|
| Base currency combobox | `input[name="base-currency"]` (`app-currency-combobox`, reused) | options from `CURRENCIES`; changing it updates `baseCurrency` and re-fetches trend |
| Quote currency combobox | `input[name="quote-currency"]` | same, for `quoteCurrency` |
| Pair validation message | rendered only when `pairError() !== null` | shown when base === quote (FR-002); trend fetch does not fire |
| Swap control | `button[aria-label="Swap currencies"]` | transposes base/quote in one action; clears `aiRequest` (FR-003, FR-014) |
| Preset buttons | `button[data-preset="7D"\|"15D"\|"1M"\|"3M"\|"6M"]` | selecting one sets `period` to `{kind:'preset', id}`, replacing any custom range; visually indicates the active preset |
| Custom range inputs | `input[type="date"][name="range-start"]`, `input[type="date"][name="range-end"]` | selecting either sets `period` to `{kind:'custom', ...}`, deselecting any active preset |
| Range validation message | rendered only when `periodError() !== null` | shown for start-after-end or >6-month span (FR-006); trend fetch does not fire |
| Summary metrics row | rendered when `trend.value()?.points.length` ≥ 1 | shows latest rate, period change (%, signed), period high (+ its date), period low (+ its date); "no data" state otherwise (FR-007, FR-015) |
| Line chart | `app-rate-trend-chart` | x = date (category scale), y = rate (not forced to zero); tooltip shows date, exact rate (verbatim string), daily % change; period-high/low points annotated; renders a "no data" placeholder when `points` is empty (FR-008, FR-015) |
| AI Insights panel | `app-ai-insights-panel`, header text "AI Insights" | "Generate insight" button, disabled when `trend.value()?.points` is empty/absent or `trend.isLoading()`; shows loading/narrative/error states; cleared whenever `pairAndRange()` changes (FR-011–FR-014) |
| Historical rates table | `app-historical-rates-table` | columns: Date, Exchange rate (verbatim string), Daily change (%, signed); most-recent-first by default; renders the same "no data" state as the chart when empty (FR-009, FR-015) |

## Behavioral contract (traces to spec FRs)

1. Selecting identical base/quote currencies → validation message shown, no `/exchange/trend`
   call fires; any previously shown data remains until a valid selection is made, per spec's "no
   blank screen" intent for a mid-edit invalid state (FR-002).
2. Valid base/quote change (with a valid period) → exactly one new `/exchange/trend` call;
   metrics, chart, and table update together from its result (User Story 1 Acceptance Scenario 3).
3. Picking a preset → resolves to concrete `startDate`/`endDate` ending on today; triggers a new
   `/exchange/trend` call; table/chart/metrics reflect exactly that trailing window (FR-004, User
   Story 2 Acceptance Scenario 1).
4. Entering a valid custom range (≤6 months, start ≤ end) → triggers a new `/exchange/trend` call
   with those exact dates (FR-005, User Story 2 Acceptance Scenario 2).
5. Entering a custom range >6 months, or start after end → validation message shown, no call
   fires (FR-006, User Story 2 Acceptance Scenarios 3–4, SC-004).
6. Triggering swap → base/quote exchange places, one new `/exchange/trend` call for the swapped
   pair using the same period, and any visible AI narrative is cleared immediately (not left
   showing the pre-swap pair) (FR-003, User Story 3, SC-002).
7. Empty `points` array in a successful `/exchange/trend` response → metrics, chart, and table all
   show the explicit "no data" state, not a blank/crashed view (FR-015, User Story 1 Acceptance
   Scenario 2, User Story 4 Acceptance Scenario 2).
8. Table rows always equal the chart's plotted points for the current pair/period — both derive
   from the same `trend.value()` (FR-009, SC-003, User Story 4 Acceptance Scenario 3).
9. Clicking "Generate insight" with data present → exactly one `/exchange/trend/insight` call;
   narrative shown grounded in the currently displayed range (FR-011, FR-012, User Story 5
   Acceptance Scenario 1).
10. "Generate insight" is unavailable/disabled when there is no data for the current pair/period
    (FR-013, User Story 5 Acceptance Scenario 3) — the AI endpoint is never called with an empty
    range.
11. AI service `503`/timeout/network failure → "interpretation unavailable" message, not a
    fabricated narrative or raw error (FR-013, User Story 5 Acceptance Scenario 2, SC-005).
12. Any pair, period, or swap change while a narrative is shown (or an insight request is in
    flight) → the narrative/in-flight result is cleared/discarded, never left attached to the new
    selection (FR-014, User Story 5 Acceptance Scenario 4, Edge Cases' in-flight-swap case, SC-006).
