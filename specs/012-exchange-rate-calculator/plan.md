# Implementation Plan: Exchange Rate Calculator View

**Branch**: `012-exchange-rate-calculator` | **Date**: 2026-08-23 | **Spec**: [spec.md](./spec.md)

**Input**: Feature specification from `/specs/012-exchange-rate-calculator/spec.md`

**Note**: This template is filled in by the `/speckit-plan` command; its definition describes the execution workflow.

## Summary

Replace the hardcoded USD→EUR placeholder in the existing `rate-lookup` feature view with a real
form: two currency dropdowns (fixed frontend-maintained list) + optional date, client-side
validated before any request fires, backed by signal state and a request-gated `rxResource` call
to the generated `ExchangeRateLookupService`, with distinct loading/result/error (invalid / no-data
/ unreachable) presentation built with Tailwind utility classes.

## Technical Context

**Language/Version**: TypeScript ~6.0.2 (repo's installed version; CLAUDE.md pins TypeScript
5.9+/Angular 21 — this workspace runs Angular ^22.1.0/TypeScript ~6.0.2, a pre-existing drift from
the doc, not introduced by this feature)

**Primary Dependencies**: Angular 22.1 (standalone components, signals, `rxResource` from
`@angular/core/rxjs-interop`), generated `api-client` → `ExchangeRateLookupService` /
`ExchangeRateResponse` / `ProblemDetail` (openapi-generator `typescript-angular`), Tailwind CSS 4.x

**Storage**: N/A (frontend-only; consumes backend REST API)

**Testing**: Vitest (`ng test`, already configured via `@angular/build`)

**Target Platform**: Browser SPA, served via `ng serve` locally / static build in other
environments

**Project Type**: Web application — frontend half of the existing `backend/` + `frontend/`
monorepo (Option 2 structure); this feature touches only the `rate-lookup` feature folder

**Performance Goals**: Not a performance-sensitive feature; a single on-demand HTTP call per
submit is sufficient (no polling, no debounced live search)

**Constraints**: No hand-rolled `HttpClient` calls (all backend access via generated
`api-client`); rate value is a decimal-precision string per Constitution I and MUST be rendered
verbatim, never parsed to a JS `number` (no arithmetic is performed on it in this view); no
source-code edit required to retarget backend base URL (Constitution IX, already satisfied by
existing `environment.ts`)

**Scale/Scope**: 1 feature view (`rate-lookup`), 1 form (2 currency selects + optional date + 1
submit control), 3 result states (loading / success / error) with 3 error categories (invalid
input, no data, unreachable)

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

- **IX. Environment-Configurable Frontend** — PASS. This feature adds no new backend base URL;
  it calls the already-wired `ExchangeRateLookupService`, which resolves `BASE_PATH` from
  `environment.ts`.
- **VI. Layered Separation of Concerns** (frontend analogue: generated API layer only) — PASS.
  All backend access goes through `ExchangeRateLookupService.getExchangeRate(from, to, date?)`;
  no ad hoc `HttpClient` usage is introduced.
- **I. Monetary Precision** (frontend analogue) — PASS by design constraint above: the `rate`
  string from `ExchangeRateResponse` is displayed as-is, never coerced to `number`.
- Other Core Principles (II–V, VII, VIII, X) are backend/data-collection concerns untouched by
  this frontend view — N/A.
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
│   │   ├── api-client/                        # generated, untouched by this feature
│   │   ├── features/
│   │   │   └── rate-lookup/
│   │   │       ├── rate-lookup.ts              # rewritten: form + signals + rxResource + result/error states
│   │   │       ├── rate-lookup.spec.ts         # new: Vitest component tests
│   │   │       └── currencies.ts               # new: fixed currency-code list for the two dropdowns
│   │   ├── shell/                              # untouched
│   │   ├── not-found/                          # untouched
│   │   ├── app.routes.ts                       # untouched (route already wired to RateLookup)
│   │   └── app.config.ts                       # untouched
│   └── environments/                           # untouched
└── openapitools.json                           # untouched
```

**Structure Decision**: Existing Option 2 web-application layout (`backend/` + `frontend/`
siblings), no new top-level or feature directories. This feature only rewrites the already
route-wired `frontend/src/app/features/rate-lookup/rate-lookup.ts` placeholder and adds a small
co-located `currencies.ts` constant plus its spec file — following the single-file-component,
inline-Tailwind-template convention already used by the `usage-analytics` and `ai-insight`
placeholders in this codebase.

## Constitution Check (post-design)

*Re-checked after Phase 1 design (data-model.md, contracts/ui-contract.md, quickstart.md).*

- **IX. Environment-Configurable Frontend** — PASS, unchanged. No new base-URL logic; confirmed
  in `contracts/ui-contract.md`.
- **VI. Layered Separation of Concerns (generated-API-only)** — PASS, unchanged.
  `data-model.md`'s Request/Response mapping and `quickstart.md`'s verification steps both
  confirm the component calls only `ExchangeRateLookupService.getExchangeRate`.
- **I. Monetary Precision (frontend analogue)** — PASS, unchanged. `data-model.md`'s Result
  Lookup Result entity keeps `rate` typed as `string` end to end; no `Number()`/`parseFloat` on
  it anywhere in the design.
- No violations. Complexity Tracking table below intentionally left empty.

## Complexity Tracking

> **Fill ONLY if Constitution Check has violations that must be justified**

*No violations — table intentionally empty.*
