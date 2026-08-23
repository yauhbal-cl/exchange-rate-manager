# Implementation Plan: Historical Trends Full-Width Chart & AI Insights Layout

**Branch**: `014-trend-layout-fix` | **Date**: 2026-08-23 | **Spec**: [spec.md](./spec.md)

**Input**: Feature specification from `/specs/014-trend-layout-fix/spec.md`

## Summary

Fix the layout of the existing Historical Exchange Rate Trends view (013-historical-rate-trends):
the trend chart currently shares a `lg:grid-cols-3` row with the AI Insights panel (chart at
`lg:col-span-2`, panel at `lg:col-span-1`) on wide viewports. This plan replaces that split with a
single-column stack — chart, then AI Insights, then the raw data table — each spanning the full
width of the `historical-rates` container at every viewport width. This is a presentation-only fix
to `historical-rates.ts`'s template: no component inputs/outputs, service calls, API contract, or
data model change.

## Technical Context

**Language/Version**: TypeScript 5.9+ (Angular 21, zoneless, standalone components)

**Primary Dependencies**: Angular 21 (existing `RateTrendChart`, `AiInsightsPanel`,
`HistoricalRatesTable` standalone components, unchanged), Tailwind CSS 4.x utility classes for
layout

**Storage**: N/A — no persistence layer involved in this fix

**Testing**: Vitest + Angular `TestBed` (existing `frontend/src/app/features/historical-rates/historical-rates.spec.ts`)

**Target Platform**: Browser (Angular SPA), all supported viewport widths (the existing responsive
breakpoints already used elsewhere on this page)

**Project Type**: Web application — this fix is frontend-only within the existing monorepo
(`frontend/`); no backend change

**Performance Goals**: N/A — no new performance characteristics introduced; existing render
performance is unaffected by a CSS/template-only change

**Constraints**: Must not change existing component behavior, data flow, or any of the
already-passing behavioral tests in `historical-rates.spec.ts`; must not reintroduce a side-by-side
column split for chart + AI Insights at any viewport width

**Scale/Scope**: Single component template edit (`frontend/src/app/features/historical-rates/historical-rates.ts`);
no new files, no new dependencies, no routing change

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

This fix is a frontend presentation-only change (Tailwind layout classes in one component
template). Reviewed against all ten Core Principles:

| Principle | Applicable? | Status |
|---|---|---|
| I. Monetary Precision | No — no monetary values are computed or reformatted | PASS |
| II. Accurate Rate Provenance | No — no rate/date persistence touched | PASS |
| III. Idempotent Data Collection | No — no ingestion code touched | PASS |
| IV. Multi-Instance Scheduler Safety | No — no scheduler code touched | PASS |
| V. Concurrency-Safe Usage Counters | No — no counter code touched | PASS |
| VI. Layered Separation of Concerns | N/A to frontend template layout; existing controller/service/repository layering on the backend is untouched | PASS |
| VII. Data-Driven Configuration Over Conditionals | No new conditional/lookup logic introduced | PASS |
| VIII. Grounded AI Output, Honest Degradation | The AI Insights panel's data-grounding and error-degradation behavior is explicitly preserved (FR-006); only its position/width changes | PASS |
| IX. Environment-Configurable Frontend | Not affected — no base-URL/config change | PASS |
| X. Test Isolation via Testcontainers | No database-backed test is added or changed by this fix | PASS |

No violations. No entries required in Complexity Tracking.

## Project Structure

### Documentation (this feature)

```text
specs/014-trend-layout-fix/
├── plan.md              # This file (/speckit-plan command output)
├── research.md          # Phase 0 output (/speckit-plan command)
├── data-model.md        # Phase 1 output (/speckit-plan command)
├── quickstart.md        # Phase 1 output (/speckit-plan command)
└── tasks.md             # Phase 2 output (/speckit-tasks command - NOT created by /speckit-plan)
```

No `contracts/` directory is produced for this feature: it changes no API request/response shape
and no `contracts/openapi.yaml` edit is required (per spec Assumptions — presentation-only fix).

### Source Code (repository root)

```text
exchange-rate-manager/
├── backend/                          # untouched by this fix
└── frontend/
    └── src/app/features/historical-rates/
        ├── historical-rates.ts             # MODIFIED: template layout only
        ├── historical-rates.spec.ts        # MODIFIED: add/adjust layout-order assertions
        ├── rate-trend-chart.ts             # unchanged
        ├── ai-insights-panel.ts            # unchanged
        ├── historical-rates-table.ts        # unchanged
        ├── trend-metrics.ts / .spec.ts     # unchanged
        └── period-presets.ts / .spec.ts    # unchanged
```

**Structure Decision**: Web application structure (Option 2: `backend/` + `frontend/`), per
`CLAUDE.md`'s monorepo layout. This fix is scoped entirely to
`frontend/src/app/features/historical-rates/historical-rates.ts` (its inline template) and its
companion spec file; no other file in the monorepo needs to change.

## Complexity Tracking

> Not applicable — Constitution Check reported no violations.
