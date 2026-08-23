# Implementation Plan: Historical Exchange Rate Trends

**Branch**: `013-historical-rate-trends` | **Date**: 2026-08-23 | **Spec**: [spec.md](./spec.md)

**Input**: Feature specification from `/specs/013-historical-rate-trends/spec.md`, plus a
detailed UI/visual brief supplied directly to this planning command (page structure, summary
metrics layout, chart requirements, AI panel placement, table columns, restrained
financial-product visual language). The brief is additive design guidance for *how* to build
what `spec.md` already scopes functionally; it introduces no new functional requirement that
contradicts `spec.md`'s FRs/acceptance scenarios.

**Note**: This template is filled in by the `/speckit-plan` command; its definition describes the execution workflow.

## Summary

New routed view (`historical-rates`) built entirely on two already-shipped, unchanged backend
endpoints (`GET /exchange/trend`, `GET /exchange/trend/insight`): a currency-pair + period filter
bar (presets 7D/15D/1M/3M/6M or a 6-month-capped custom range) drives a reactive `rxResource` that
feeds a summary-metrics row, a Chart.js line chart, and a raw-data table — all three always in
sync because they share one derived data source. A separate, explicitly-triggered `rxResource`
drives an AI Insights panel beside the chart, reset to empty whenever the pair/period changes or
a swap occurs (FR-011–FR-014). No backend or contract changes.

## Technical Context

**Language/Version**: TypeScript 5.9.3 (installed; matches CLAUDE.md's 5.9+/Angular 21 pin)

**Primary Dependencies**: Angular 21.2.21 (standalone components, signals, `rxResource` from
`@angular/core/rxjs-interop`), generated `api-client` → `ExchangeRateAnalyticsService`
(`getExchangeRateTrend`) and `ExchangeRateAIInsightService` (`getExchangeRateTrendInsight`),
Tailwind CSS 4.x, **Chart.js (~4.5.x, new dependency)** for the line chart — this repo's first
charting library (see `research.md` §1 for why Chart.js over the alternatives) — and
**`decimal.js` (new dependency)** for Constitution-I-compliant decimal-exact derived-metric
arithmetic (see `research.md` §2).

**Storage**: N/A (frontend-only; consumes the existing `/exchange/trend` and
`/exchange/trend/insight` REST endpoints, no new endpoint or schema)

**Testing**: Vitest (`ng test`, already configured via `@angular/build`)

**Target Platform**: Browser SPA, served via `ng serve` locally / static build elsewhere

**Project Type**: Web application — frontend half of the existing `backend/` + `frontend/`
monorepo (Option 2 structure); this feature adds one new feature folder,
`frontend/src/app/features/historical-rates/`, and touches no backend code

**Performance Goals**: Not a high-throughput feature; one `/exchange/trend` call per
pair/period change (debounced by nothing beyond normal signal-change coalescing — Angular batches
same-tick signal writes) and one `/exchange/trend/insight` call per explicit "Generate insight"
click. No polling.

**Constraints**:
- No hand-rolled `HttpClient` calls — only the generated `ExchangeRateAnalyticsService` /
  `ExchangeRateAIInsightService` methods.
- Raw rate values (`RateTrendPoint.rate`) are decimal-precision strings (Constitution I) and MUST
  be rendered verbatim in the table/summary headline figures — never reparsed and reformatted for
  display of the underlying value itself.
- Derived numeric metrics that spec.md requires computing client-side (latest, period change %,
  period high/low, daily change %) are computed via `Decimal` (`decimal.js`), never JS `number`/
  `parseFloat`, per Constitution I — see `research.md` §2. Derived values are never persisted or
  sent back to any API, and never substituted for the verbatim string shown alongside them.
- Custom date range capped at 6 months, enforced client-side before any request fires (FR-006);
  no equivalent length cap exists server-side on `/exchange/trend` today, so this is a UI-only
  guarantee (documented assumption, not a contract change).
- AI interpretation generation is strictly explicit-button-gated and MUST be cleared whenever the
  pair, period, or a swap changes (FR-011, FR-014) — it never auto-regenerates.

**Scale/Scope**: 1 routed feature view composed of ~5 sub-components (pair + period filter bar,
summary metrics row, line chart, AI Insights panel, historical rates table); 2 existing backend
endpoints consumed; 5 named presets + 1 custom-range mode; reuses the existing
`rate-lookup` feature's `CURRENCIES` list and `CurrencyCombobox` component rather than duplicating
either.

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

- **IX. Environment-Configurable Frontend** — PASS. No new base-URL logic; both consumed services
  already resolve `BASE_PATH` from `environment.ts`.
- **VI. Layered Separation of Concerns** (frontend analogue: generated-API-layer-only) — PASS.
  All backend access goes through `ExchangeRateAnalyticsService.getExchangeRateTrend` and
  `ExchangeRateAIInsightService.getExchangeRateTrendInsight`; no ad hoc `HttpClient` usage.
- **I. Monetary Precision** (frontend analogue) — PASS. Raw `rate` strings are always rendered
  verbatim; all derived comparisons/arithmetic (high/low/% change) use `Decimal` from
  `decimal.js`, never JS floating point. See `research.md` §2.
- **VIII. Grounded AI Output, Honest Degradation** — PASS. This feature adds only the
  explicit-trigger UI around the already-compliant `/exchange/trend/insight` endpoint; it changes
  no backend AI behavior.
- Other Core Principles (II–V, VII, X) are backend/data-collection concerns untouched by this
  frontend-only view — N/A.
- No violations requiring Complexity Tracking.

## Project Structure

### Documentation (this feature)

```text
specs/[###-feature]/
├── plan.md              # This file (/speckit-plan command output)
├── research.md          # Phase 0 output (/speckit-plan command)
├── data-model.md        # Phase 1 output (/speckit-plan command)
├── quickstart.md        # Phase 1 output (/speckit-plan command)
├── contracts/           # Phase 1 output (/speckit-plan command)
└── tasks.md             # Phase 2 output (/speckit-tasks command - NOT created by /speckit-plan)
```

### Source Code (repository root)

```text
frontend/
├── src/
│   ├── app/
│   │   ├── api-client/                              # generated, untouched by this feature
│   │   ├── features/
│   │   │   ├── rate-lookup/
│   │   │   │   ├── currencies.ts                    # reused as-is (CURRENCIES list)
│   │   │   │   └── currency-combobox.ts              # reused as-is (cross-feature import)
│   │   │   └── historical-rates/                     # NEW
│   │   │       ├── historical-rates.ts               # container: filters, both rxResources, layout
│   │   │       ├── historical-rates.spec.ts          # new
│   │   │       ├── period-presets.ts                 # new: preset defs + date-range/formatting helpers
│   │   │       ├── period-presets.spec.ts            # new
│   │   │       ├── trend-metrics.ts                  # new: pure functions deriving summary metrics
│   │   │       ├── trend-metrics.spec.ts             # new
│   │   │       ├── rate-trend-chart.ts               # new: Chart.js line-chart wrapper component
│   │   │       ├── historical-rates-table.ts         # new: raw data table component
│   │   │       └── ai-insights-panel.ts              # new: explicit-trigger AI panel component
│   │   ├── shell/                                    # untouched
│   │   ├── not-found/                                # untouched
│   │   ├── app.routes.ts                             # +1 route: historical-rates
│   │   └── app.config.ts                             # untouched
│   └── environments/                                 # untouched
├── package.json                                      # +2 dependencies: chart.js, decimal.js
└── openapitools.json                                 # untouched
```

**Structure Decision**: Existing Option 2 web-application layout (`backend/` + `frontend/`
siblings), no backend changes and no new top-level directories. This feature adds one new
feature folder, `frontend/src/app/features/historical-rates/`, split into several small
co-located files (chart wrapper, table, AI panel, pure metric/preset helpers) rather than one
large single-file component — justified by this view's materially larger surface area than the
existing single-file placeholders (`rate-lookup`, `ai-insight`, `usage-analytics`), and reuses
`rate-lookup`'s `currencies.ts`/`currency-combobox.ts` via direct relative import rather than
extracting a new `shared/` module (see `research.md` §5).

## Constitution Check (post-design)

*Re-checked after Phase 1 design (`data-model.md`, `contracts/ui-contract.md`,
`quickstart.md`).*

- **IX. Environment-Configurable Frontend** — PASS, unchanged.
- **VI. Layered Separation of Concerns (generated-API-only)** — PASS, unchanged.
  `contracts/ui-contract.md` confirms the only two backend calls are
  `getExchangeRateTrend`/`getExchangeRateTrendInsight`.
- **I. Monetary Precision (frontend analogue)** — PASS, unchanged. `data-model.md`'s Trend
  Metrics entity keeps every raw `rate`/`RateTrendPoint.rate` as `string` end to end for display;
  the derived-numbers boundary from `research.md` §2 is enforced in `trend-metrics.ts`'s pure
  functions, which take strings in and return `{ display: string, value: Decimal }` pairs (all
  comparison/arithmetic via `decimal.js`, never JS `number`) so the verbatim string is never lost
  and no float arithmetic touches a rate value.
- **VIII. Grounded AI Output, Honest Degradation** — PASS, unchanged.
- No violations. Complexity Tracking table below intentionally left empty.

## Complexity Tracking

> **Fill ONLY if Constitution Check has violations that must be justified**

*No violations — table intentionally empty.*
