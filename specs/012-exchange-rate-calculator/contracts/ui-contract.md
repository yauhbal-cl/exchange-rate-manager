# UI Contract: Exchange Rate Calculator View

This feature adds no new backend endpoint and changes no schema in `contracts/openapi.yaml`. The
contract below is the *view's* observable behavior — its inputs, outputs, and the exact backend
call it's allowed to make — so implementation and tests have a shared reference.

## Backend call (unchanged, existing contract)

- **Endpoint**: `GET /exchange` (`contracts/openapi.yaml` lines 57–104), consumed only via the
  generated `ExchangeRateLookupService.getExchangeRate(from: string, to: string, date?: string)`.
- No other generated service/method may be called from this view.
- `date` is omitted from the call entirely (not sent as `''`) when the user left it blank.

## Component public surface

`RateLookup` (`frontend/src/app/features/rate-lookup/rate-lookup.ts`) — standalone component,
already routed at path `rate-lookup` in `app.routes.ts`. No inputs/outputs (it's a routed leaf
view, not reused elsewhere), so "public surface" here means the DOM contract a test/E2E script
can rely on:

| Element | Selector contract | Behavior |
|---|---|---|
| Source currency select | `select[name="from"]` (or equivalent test id) | options from `CURRENCY_CODES`; empty/placeholder option first |
| Target currency select | `select[name="to"]` | same option list |
| Date input | `input[type="date"][name="date"]` | optional; browser-native date picker; `max` attribute bound to today so future dates cannot be picked via the UI control itself |
| Submit control | `button[type="submit"]` | disabled when `validationError() !== null` or `rate.isLoading()` |
| Validation message | rendered only when `validationError() !== null`, before any submit occurs | text matches the failing rule (empty selection / identical currencies / future date) |
| Loading indicator | rendered only when `rate.isLoading()` | replaces (not overlays ambiguously with) the previous result/error |
| Result block | rendered when `rate.value()` is set and `rate.error()` is not | shows `fromCurrency`, `toCurrency`, `rate` (verbatim string), `rateDate`, both usage counts |
| Error block | rendered when `rate.error()` is set | shows the categorized message (`invalid` / `no-data` / `unreachable`); form remains editable and submit remains available (not disabled by the error itself) |

## Behavioral contract (traces to spec FRs)

1. Selecting identical currencies and attempting submit → validation message shown, `from`/`to`
   never sent to `ExchangeRateLookupService` (FR-002, FR-004).
2. Leaving either currency unselected and attempting submit → validation message shown, no call
   (FR-002, FR-004).
3. Entering a date after today and attempting submit → validation message shown, no call
   (FR-003, FR-004).
4. Valid submit → exactly one call to `getExchangeRate`; submit control disabled until that call
   settles (FR-005).
5. Blank date on valid submit → call made with `date` omitted (FR-007).
6. Backend `400` → error block shows `invalid` category with the `ProblemDetail.detail` text
   (FR-008).
7. Backend `404` → error block shows `no-data` category with the `ProblemDetail.detail` text
   (FR-008).
8. Network failure / unreachable backend → error block shows `unreachable` category with a fixed
   fallback message (FR-008).
9. Re-submitting after an error, with the same or edited inputs, produces a new call and clears
   the previous error once the new result/error resolves (FR-009).
10. Changing an input after a slow request is in flight, then submitting again before the first
    resolves → only the response matching the latest submitted request is ever shown (FR-010).
