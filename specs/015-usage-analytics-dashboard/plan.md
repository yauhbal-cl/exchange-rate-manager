# Implementation Plan: Usage Analytics Dashboard

**Branch**: `015-usage-analytics-dashboard` | **Date**: 2026-08-23 | **Spec**: [spec.md](./spec.md)

**Input**: Feature specification from `/specs/015-usage-analytics-dashboard/spec.md` (including the
2026-08-23 clarification session, which fixed the KPI population, the accessibility approach, the
zero-count exclusion + footnote, relative time display, and the 2 s target / 10 s timeout).

## Summary

Replace the placeholder `usage-analytics` view with a real dashboard, built entirely on the
already-shipped `GET /exchange/usage` endpoint — unchanged, uncapped, called once per page load. The
response (one entry per known currency, never-queried ones included with `queryCount: 0` and
`lastQueriedAt: null`) is the single source for all three sections: three KPI cards computed over
the **complete** set (total queries, distinct queried currencies, most-queried currency with a
deterministic alphabetical tie-break), a left-hand breakdown panel ranking the top 10 queried
currencies as label + proportional bar + count with a never-queried footnote, and a narrower
right-hand recent-activity panel listing the 8 most recently queried currencies with load-time
relative phrases plus machine-readable and local-absolute instants. Derivations live in pure,
dependency-free modules; bars are `aria-hidden` decoration beside text values; the request is
bounded by a 10 s RxJS `timeout` so a stalled backend becomes the single error state instead of a
permanent spinner. No backend change, no contract change, no client regeneration, no new dependency.

## Technical Context

**Language/Version**: TypeScript 5.9.3 (installed; matches the CLAUDE.md 5.9+/Angular 21 pin)

**Primary Dependencies**: Angular 21.2.21 (standalone components, signals, `rxResource` from
`@angular/core/rxjs-interop`), the generated `api-client` →
`ExchangeRateUsageAnalyticsService.getUsageAnalytics()`, RxJS 7.8 (`timeout`), platform `Intl`
(`NumberFormat`, `RelativeTimeFormat`, `DateTimeFormat`). **No new package.** Notably *not* used:
`chart.js` (the bars are CSS widths, not a canvas chart — see `research.md` §6) and `decimal.js`
(counts are integers, not money — see `research.md` §5).

**Storage**: N/A — frontend-only; consumes the existing `/exchange/usage` REST endpoint. No schema,
migration, or entity change.

**Testing**: Vitest (`cd frontend && npm test`), already wired via `@angular/build` + jsdom. Pure
derivation specs plus a `TestBed` component spec with the generated service stubbed, following
`features/historical-rates/*.spec.ts`.

**Target Platform**: Browser SPA (`ng serve` locally, static build elsewhere); modern desktop and
mobile browsers, 320 px–2560 px viewports (SC-005).

**Project Type**: Web application — frontend half of the existing `backend/` + `frontend/` monorepo.
This feature rewrites one existing feature folder,
`frontend/src/app/features/usage-analytics/`, and touches no backend code and no other feature.

**Performance Goals**: Complete content within 2 s of opening against a local-network backend in
≥95 % of loads (SC-009); one HTTP call per page load, no polling, no auto-refresh. All derivation
is O(n log n) over a few hundred entries at most — negligible beside the round trip.

**Constraints**:
- No hand-rolled `HttpClient` calls — only `ExchangeRateUsageAnalyticsService.getUsageAnalytics()`,
  with `limit` and `recentDays` both omitted (supplying either would break FR-005a's system-wide
  KPIs or FR-009a's footnote — see `research.md` §1).
- Retrieval bounded by `timeout({ each: 10_000 })`; timeout and HTTP failure render the *same*
  single error message (FR-014, FR-015a).
- Display caps (top 10 rows, 8 recent entries) are presentation-only and MUST NOT narrow the KPI
  inputs (FR-005a, data-model INV-2).
- Ordering and tie-breaks are computed client-side, on copies of the response array, so repeat
  renders are byte-identical (FR-005, FR-006, SC-006).
- Bars are decorative: `aria-hidden`, no `role="progressbar"`, every value duplicated as text
  (FR-022, FR-023).
- Relative phrases are computed against a `now` captured once at component creation and never
  advanced; future instants clamp to the just-now phrase (FR-012, clock-skew edge case).
- Counts formatted via `Intl.NumberFormat` — no rounding, abbreviation, or truncation (FR-019).
- The route address `usage-analytics` and its nav entry are unchanged (FR-020).
- Zero interactive controls on the page; nothing focusable, nothing pointer-only (FR-026).

**Scale/Scope**: 1 rewritten routed view = 3 components (page + 2 presentational panels), 2 pure
helper modules, 3 scoped stylesheets, 3 spec files; 1 existing endpoint consumed; ≤ a few hundred
currency entries per response; display caps 10 rows / 8 entries.

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

- **I. Monetary Precision** — N/A / PASS. This page displays no rate or monetary value: every
  number is an integer event counter (`queryCount`) or a derived count of currencies. `Intl`
  integer formatting introduces no rounding (FR-019); `decimal.js` is deliberately unused, with the
  reasoning recorded in `research.md` §5 so the omission is a decision, not an oversight.
- **II. Accurate Rate Provenance** — N/A. No rate ingestion or persistence in scope.
- **III. Idempotent Data Collection** — N/A. No writes.
- **IV. Multi-Instance Scheduler Safety** — N/A. No scheduled job.
- **V. Concurrency-Safe Usage Counters** — PASS (by non-interference). The page only reads
  `/exchange/usage`; rendering it increments nothing, and no counter logic is added or changed
  (FR-021, SC-007, data-model INV-1). Also satisfies the Development Standard that
  non-query operations must not alter usage counters.
- **VI. Layered Separation of Concerns** — PASS (frontend analogue). Transport stays in the
  generated API client; derivation lives in pure modules (`usage-metrics.ts`, `relative-time.ts`);
  components only orchestrate and render. No business logic in templates beyond formatting calls,
  no HTTP in components beyond the single `rxResource` `stream`.
- **VII. Data-Driven Configuration Over Conditionals** — PASS. The relative-time unit ladder is a
  declared table of thresholds iterated in order, not a branching chain of hand-written `if`s; the
  display caps are two named constants.
- **VIII. Grounded AI Output, Honest Degradation** — N/A for AI (no AI call here), but the honest-
  degradation half is respected in spirit and by FR-014/FR-015a: unavailable or slow data yields an
  explicit error, never zeros or stale values presented as current.
- **IX. Environment-Configurable Frontend** — PASS. No new base-URL logic; the consumed generated
  service already resolves `BASE_PATH` from `environment.ts` via `app.config.ts`.
- **X. Test Isolation via Testcontainers** — N/A. This feature adds no DB-dependent test; all new
  tests are pure-function and `TestBed` specs with the API service stubbed, touching no database.
- **Development & Quality Standards** — PASS. No new REST surface, so no problem-detail or
  `@RestControllerAdvice` obligation; error rendering consumes the existing endpoint's failures.

**Gate result: PASS** — no violation, one documented CLAUDE.md styling deviation (not a
constitution matter) tracked below.

**Post-Phase-1 re-check: PASS** — the Phase 1 design (`data-model.md`, `contracts/ui-contract.md`)
adds no persistence, no endpoint, no counter write, and no monetary value; the derivation-module
split is what keeps Principle VI satisfied, and INV-1/INV-2/INV-6 in `data-model.md` encode the
Principle V and determinism obligations as testable invariants.

## Project Structure

### Documentation (this feature)

```text
specs/015-usage-analytics-dashboard/
├── plan.md              # This file (/speckit-plan command output)
├── spec.md              # Feature specification (input)
├── research.md          # Phase 0 output
├── data-model.md        # Phase 1 output
├── quickstart.md        # Phase 1 output
├── contracts/
│   └── ui-contract.md   # Phase 1 output — view contract; no openapi.yaml change
└── tasks.md             # Phase 2 output (/speckit-tasks — NOT created by /speckit-plan)
```

### Source Code (repository root)

```text
frontend/
└── src/app/
    ├── app.routes.ts                          # unchanged — `usage-analytics` keeps its address (FR-020)
    ├── shell/shell.html                       # unchanged — existing nav link (FR-020)
    ├── api-client/                            # unchanged, not regenerated (no contract change)
    │   ├── api/exchangeRateUsageAnalytics.service.ts   # consumed: getUsageAnalytics()
    │   └── model/currencyUsageEntry.ts                 # source type
    └── features/usage-analytics/
        ├── usage-analytics.ts                 # REWRITTEN: page component (header, KPI row, grid, states)
        ├── usage-analytics.css                # NEW: page layout + KPI cards (design tokens)
        ├── usage-analytics.spec.ts            # NEW: component spec (loading/error/timeout/empty/populated)
        ├── usage-metrics.ts                   # NEW: pure derivations (summary, breakdown, recent)
        ├── usage-metrics.spec.ts              # NEW: unit spec for the derivations
        ├── relative-time.ts                   # NEW: pure elapsed-phrase + absolute-local helpers
        ├── relative-time.spec.ts              # NEW: unit spec (unit ladder, skew clamp)
        ├── usage-breakdown-panel.ts           # NEW: presentational panel (rows, bars, footnote, empty)
        ├── usage-breakdown-panel.css          # NEW
        ├── recent-activity-panel.ts           # NEW: presentational panel (entries, <time>, empty)
        └── recent-activity-panel.css          # NEW

backend/                                       # untouched by this feature
contracts/openapi.yaml                         # untouched by this feature
```

**Structure Decision**: Existing two-module web-application layout (`backend/` + `frontend/`
siblings, per CLAUDE.md's Monorepo Layout). All work lands in the existing
`frontend/src/app/features/usage-analytics/` folder, mirroring how `features/historical-rates/`
is organized (page component + presentational children + pure helper modules + colocated specs +
scoped stylesheets). Nothing outside that folder changes: no route edit, no nav edit, no shared
module, no `api-client` regeneration.

## Complexity Tracking

> Constitution Check passes with no violations. The single entry below is a documented deviation
> from `CLAUDE.md`'s frontend styling default (project guidance, not a constitution principle),
> recorded here so it is an explicit decision rather than drift.

| Violation | Why Needed | Simpler Alternative Rejected Because |
|-----------|------------|-------------------------------------|
| Scoped component CSS (3 stylesheets with the shared design-token block) instead of CLAUDE.md's "Tailwind CSS, no hand-rolled component CSS where a utility class covers it" | The two most recently shipped views (012 rate calculator, 013/014 historical rates) are built this way on the same `--surface`/`--border`/`--muted`/`--accent` token set; this dashboard sits directly beside them in the same nav and must read as the same product (FR-018 explicitly requires a content width and card/spacing treatment consistent with the app's other pages). The one genuinely dynamic value, bar width, is a `[style.width.%]` data binding either way. | Pure Tailwind utilities: closer to the guidance's letter, but would introduce a third visual dialect and guarantee drift in border color, radius, spacing scale, and tabular-numeral treatment against the adjacent pages — the exact inconsistency FR-018 is written to prevent. Restyling the other pages to Tailwind first is out of this feature's scope. Tailwind remains in the project and in use by the app shell; nothing is removed. |
