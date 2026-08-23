# Quickstart: Validate the Historical Trends Layout Fix

## Prerequisites

- Node 22 LTS, npm (bundled)
- Backend running (or mocked) so the Historical Rates view has data — see
  `specs/013-historical-rate-trends/quickstart.md` for standing up the backend and seed data if
  needed. This fix can also be visually checked with the frontend alone against a running backend
  from `docker compose up -d`.

## Run the frontend

```bash
cd frontend
npm start
```

Navigate to the Historical Exchange Rate Trends view (default route for this feature) in a
browser.

## Manual validation scenarios

1. **Chart full width (FR-001, SC-001)**
   - With the default USD/EUR pair and 1M period (data present), confirm the trend chart's
     rendered width visually matches the width of the metrics row above it and the raw data table
     below it — not sharing a row with the AI Insights panel.
   - Resize the browser window from narrow (e.g. 375px) to wide (e.g. 1440px+). The chart must
     remain full width at every size — it must never move into a partial-width column.

2. **AI Insights stacked full width between chart and table (FR-002, FR-003, FR-004, SC-002,
   SC-003)**
   - Click "Generate insight". Confirm the AI Insights panel renders directly below the chart and
     directly above the raw data table, at the same width as both.
   - Repeat at the loading state (click generate and observe before the response resolves), the
     error state (stop/misconfigure the AI backend to force a 503, or use the existing test setup
     as a reference), and the idle (pre-generation) state. In every state, the panel stays in the
     same stacked position and full width — it never appears beside the chart in a side column.
   - Resize the browser window from narrow to wide again with an insight showing. The stacked,
     full-width arrangement must hold at every size.

3. **No regression to existing behavior (FR-006, SC-004)**
   - Confirm currency selection, swap, presets, custom range validation, metrics, table contents,
     and the AI insight generate/error/stale-clear behavior all work exactly as before — only
     their width/position changed.

## Automated validation

```bash
cd frontend
npm test -- historical-rates
```

Expect:
- All existing tests in `historical-rates.spec.ts` continue to pass unchanged (no behavioral
  regression).
- New/updated assertions (added during `/speckit-tasks` → implementation) confirming the chart
  renders before the AI Insights panel, which renders before the table, in the component's
  rendered HTML — mirroring the existing `metricsIndex < chartIndex` pattern already used in this
  spec file for FR-010.
