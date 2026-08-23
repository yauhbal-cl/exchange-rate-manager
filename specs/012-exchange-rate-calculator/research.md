# Research: Exchange Rate Calculator View

No Technical Context fields were marked `NEEDS CLARIFICATION` (the stack, the target file, and
the backend contract are all already fixed by the existing scaffold). Research below resolves the
open *design* decisions needed to fill in the placeholder view correctly, given the spec's
resolved clarification (dropdown selects, fixed frontend list).

## 1. Form state: plain signals vs Reactive Forms

**Decision**: Plain `signal()`s for `fromCurrency`, `toCurrency`, `date`, plus a derived
`computed()` for client-side validation errors. No `FormGroup`/`FormControl`.

**Rationale**: The feature spec's own vocabulary ("signals for state") and CLAUDE.md's frontend
convention ("signals for state, `httpResource`/`resource` for API calls") point at signal-first
state, not the Reactive Forms module. The form is small (2 selects + 1 date input) — a
`FormGroup` would add an unused dependency (`ReactiveFormsModule`) for no validation-composition
benefit at this size.

**Alternatives considered**: `ReactiveFormsModule` with `Validators.required` +
custom validator — rejected as unnecessary ceremony for 3 fields and inconsistent with the
signals-first convention already set by the other two placeholder views.

## 2. Gating the HTTP call on submit, not on every keystroke

**Decision**: Keep a separate `submittedRequest = signal<{from,to,date} | undefined>(undefined)`
signal, written only inside the submit handler after validation passes. `rxResource` (from
`@angular/core/rxjs-interop`) is keyed off `submittedRequest`; when it's `undefined` the resource
has no request and does not call the backend.

**Rationale**: `rxResource`'s `params`/`request` callback re-runs the `stream` factory whenever
the signals it reads change — if it read the raw form signals directly, every keystroke in the
date field would fire a new HTTP call. Separating "current form input" from "the request that was
actually submitted" is what makes FR-004 (no backend contact on invalid/unsubmitted input) and
FR-005 (one request per submit) hold structurally, not by convention.

**Alternatives considered**: Manual `HttpClient` call inside the submit handler, storing the
`Observable` result in a signal via `toSignal` — rejected: reinvents what `rxResource` already
gives for free (loading/error/value signals, automatic stale-response discarding), and CLAUDE.md
says to prefer `resource`/`httpResource` idioms.

## 3. Discarding stale responses (FR-010)

**Decision**: Rely on `rxResource`'s built-in request-identity behavior — it tracks the
request object identity for its current in-flight call and only commits a response to
`.value()`/`.error()` if that response belongs to the *latest* request signal value. No extra
request-token bookkeeping needed in component code.

**Rationale**: This is Angular's own resource-race-handling contract; re-implementing it (e.g.
a manual "requestId" counter compared on response) would duplicate framework behavior the spec's
"signals for state" direction is already steering toward using directly.

**Alternatives considered**: Manual incrementing request-id + comparison on resolve — rejected,
redundant with `rxResource` semantics and adds state to track for no behavioral gain.

## 4. Distinguishing error categories from one `error()` signal

**Decision**: A `computed()` derived from `rate.error()` inspects the thrown value: if it's an
`HttpErrorResponse` with `status === 400`, category is `invalid`; `status === 404` → `no-data`;
anything else (network failure, `status === 0`, non-2xx not otherwise mapped, or a timeout) →
`unreachable`. The message shown is the backend's `ProblemDetail.detail` when present (400/404
cases — the API already returns human-readable `detail` text per `contracts/openapi.yaml`), else
a static fallback string for the `unreachable` category (no `ProblemDetail` body exists for a
network-level failure).

**Rationale**: The spec (FR-008) requires distinguishing at least these three cases with
human-readable text, and the backend already encodes exactly this distinction via HTTP status +
`ProblemDetail.detail` — no client-side re-derivation of *why* it failed beyond reading the
status code is needed.

**Alternatives considered**: Generic single "Something went wrong" message for all failures —
rejected, fails FR-008 and SC-003's requirement for a *distinct* message per category.

## 5. Currency dropdown option list

**Decision**: A fixed, hand-maintained `const CURRENCY_CODES: readonly string[]` in a small
co-located `currencies.ts`, covering the currencies the backend's spread configuration already
names explicitly (`EUR`, `JPY`, `HKD`, `KRW`, `MYR`, `INR`, `MXN`, `RUB`, `CNY`, `ZAR`) plus `USD`
(the collection/normalization base currency) and a handful of other majors commonly paired
against them (`GBP`, `CHF`, `CAD`, `AUD`, `SGD`, `NZD`, `SEK`, `NOK`, `DKK`). The backend accepts
any 3-letter code and applies its configured `default-spread-percent` to codes outside the
explicit spread table, so this list is a UI convenience, not a backend allow-list — it doesn't
need to be exhaustive of every currency Fixer.io could ever return.

**Rationale**: Per the spec's resolved clarification, there is no "list available currencies"
backend endpoint to populate the dropdown from; a static list matching known real usage
(`application.yml`'s explicit spread table) is the smallest correct choice.

**Alternatives considered**: Free-text pattern-validated input — explicitly rejected by the
user's answer to the spec's clarification question in favor of dropdowns.

## 6. Date input validation ("not in the future")

**Decision**: Compare the entered date string (`yyyy-MM-dd` from a native `<input type="date">`)
against a `today` value passed in as a plain ISO date string, using string comparison (which is
correct for `yyyy-MM-dd`-formatted dates) rather than constructing `Date` objects.

**Rationale**: Native `<input type="date">` already constrains entry to valid calendar dates and
emits `yyyy-MM-dd`, so lexicographic string comparison against another `yyyy-MM-dd` string is
sufficient and avoids timezone-conversion pitfalls that `new Date(str)` parsing introduces (a
subtlety worth calling out explicitly since it's the kind of correct-looking bug that silently
misfires near midnight).

**Alternatives considered**: `Date` object comparison — rejected due to timezone-parsing
ambiguity for bare date strings across browsers.
