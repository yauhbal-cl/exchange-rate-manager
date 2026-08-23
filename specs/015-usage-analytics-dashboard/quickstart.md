# Quickstart & Validation: Usage Analytics Dashboard

**Feature**: `015-usage-analytics-dashboard` | Route: `/usage-analytics`

How to run this view and prove it satisfies the spec. Data shapes live in
[data-model.md](./data-model.md); the DOM/behavior contract (including every `data-testid`) lives in
[contracts/ui-contract.md](./contracts/ui-contract.md). No implementation code here.

---

## Prerequisites

- Node 22 LTS, npm (bundled), Java 21, Docker (for Postgres).
- Local API base URL is already `http://localhost:8080/api/v1`
  (`frontend/src/environments/environment.ts`); nothing to configure.
- No contract change in this feature → **do not** run `npm run generate:api`.

## Run

```bash
docker compose up -d                       # Postgres
cd backend && ./mvnw spring-boot:run       # API on :8080
cd frontend && npm start                   # SPA on :4200
```

Open <http://localhost:4200/usage-analytics> (or click "Usage Analytics" in the top nav — the route
address is unchanged, FR-020).

## Automated tests

```bash
cd frontend && npm test                    # Vitest: all specs
cd frontend && npm test -- usage           # just this feature's specs
```

Expected coverage (see [plan.md](./plan.md) → Project Structure for file names):

- `usage-metrics.spec.ts` — KPI totals over the full set, alphabetical tie-break, zero-count
  exclusion, `queryCount` DESC + code ASC ordering, 10-row cap, proportion percentages, footnote
  count, `lastQueriedAt` DESC ordering, null-timestamp exclusion, 8-entry cap, and the empty /
  single-currency / all-tied cases.
- `relative-time.spec.ts` — each unit-ladder threshold, the under-a-minute phrase, the future-instant
  (clock-skew) clamp, and the local absolute date-time string.
- `usage-analytics.spec.ts` — the four page states: loading, error (HTTP failure), error (10 s
  timeout, driven with fake timers and a never-emitting stream), and populated; plus that exactly one
  `getUsageAnalytics()` call is made, with no arguments.

## Seeding data conditions

Usage counters only move when a **rate query** happens. Each `GET /exchange` increments both
currencies' counters (`/exchange/refresh` deliberately does not — it is not a query).

```bash
BASE=http://localhost:8080/api/v1
curl -s -X POST "$BASE/exchange/refresh" > /dev/null      # ingest rates → populates known currencies

for i in $(seq 1 12); do curl -s "$BASE/exchange?from=USD&to=EUR" > /dev/null; done   # USD/EUR hottest
for i in $(seq 1 5);  do curl -s "$BASE/exchange?from=USD&to=GBP" > /dev/null; done
curl -s "$BASE/exchange?from=CHF&to=JPY" > /dev/null                                  # one-off pair

curl -s "$BASE/exchange/usage" | head -c 800               # the exact payload the page consumes
```

| Condition | How to produce it |
|---|---|
| **Populated** | the block above (≥ 3 queried currencies at different volumes, many never queried) |
| **More than 10 queried currencies** | loop `GET /exchange` over ≥ 11 distinct currency codes with differing repeat counts |
| **Single currency queried** | fresh DB → `POST /exchange/refresh`, then one `GET /exchange?from=USD&to=USD`-style single pair only |
| **Nothing queried yet** | `POST /exchange/refresh` on a fresh DB and issue **no** rate query — every entry comes back `queryCount: 0`, `lastQueriedAt: null` |
| **No usage records at all** | fresh DB with no ingestion → `currencies: []` |
| **Data unavailable** | stop the backend (or point `apiBaseUrl` at an unused port) and reload |
| **Retrieval hangs** | keep the backend down but have the port accept and never answer (e.g. `nc -l 8080`), then reload and wait |
| **Recent-activity spread** | query one pair now, then `UPDATE currency_usage SET last_queried_at = now() - interval '2 days' WHERE currency_code = 'GBP';` in psql to get mixed ages |
| **Clock skew** | `UPDATE currency_usage SET last_queried_at = now() + interval '5 minutes' WHERE currency_code = 'CHF';` |
| **Very large counts** | `UPDATE currency_usage SET query_count = 1234567 WHERE currency_code = 'USD';` |

`psql` access: `docker compose exec postgres psql -U exchange_user -d exchange_rate_db` (defaults
from `docker-compose.yml`; override if you set `POSTGRES_USER`/`POSTGRES_DB`).

---

## Manual validation scenarios

### US1 — Grasp overall query activity at a glance (P1)

1. With **Populated** data, open the page. Confirm the `<h1>` title and the one-line subtitle
   describing the page as an overview of query activity sit above everything else (FR-001).
2. Confirm exactly three bordered KPI cards in one row directly below (FR-002).
3. Cross-check each against `curl "$BASE/exchange/usage"`: card 1 = the sum of **every**
   `queryCount` in the payload (not just the top 10), card 2 = the number of entries with
   `queryCount > 0`, card 3 = the highest-count currency with its count (FR-003 … FR-005a).
4. Reload several times: the "most queried" currency and all three numbers are identical each time
   (SC-006). With two currencies tied at the top, the alphabetically first code wins (FR-005).
5. With **Nothing queried yet**: cards read `0`, `0`, and an explicit empty indication — never blank
   and never a currency with count `0` (FR-013).

### US2 — Compare query volume across currencies (P1)

1. Confirm the left panel is headed "Activity breakdown" and its rows descend by count; ties are
   ordered alphabetically (FR-006).
2. Each row shows the currency code as text, a bar, and the count as text (FR-007, FR-022).
3. The top row's bar is full width; a currency with roughly half the top count has a bar roughly
   half as wide (FR-008).
4. No currency with `queryCount: 0` appears as a row; the footnote below the rows reports how many
   known currencies have never been queried, and matches the count of `queryCount: 0` entries in the
   payload (FR-006, FR-009a).
5. With **More than 10 queried currencies**: exactly 10 rows, plus a visible indication that this is
   the top 10 of the larger total (FR-009).
6. With **Nothing queried yet** / **No usage records at all**: the panel shows its empty state with
   no rows and no zero-length bars, and the footnote still reports the never-queried figure (or
   states zero when there is nothing to report) (FR-013, US2 scenario 6).

### US3 — Check the latest query activity (P2)

1. Confirm the right panel is headed "Recent activity" and entries run newest first (FR-010).
2. Each entry shows a currency code and an elapsed-time phrase; a just-queried currency reads as the
   under-a-minute phrase, a 2-day-old one reads "2 days ago" (FR-012).
3. Hover an entry: the tooltip gives the absolute date **and** time-of-day in your local timezone
   (FR-012a). Inspect the element: a `<time datetime="…">` carries the raw ISO instant (FR-025).
4. Never-queried currencies are absent; a currency with a count but no timestamp appears in the
   breakdown panel only (FR-011, edge case).
5. With **Clock skew** data, the future-stamped currency shows the just-now phrase — never a
   negative or future-tense phrase.
6. Leave the page open a few minutes: phrases do **not** tick forward; reloading recomputes them
   (edge case, no auto-refresh).
7. With no queried currency at all: an explicit empty-state message, not an empty list (FR-013).

### US4 — Read the page comfortably on any screen (P3)

1. At 1440 px: KPI cards in one row above a two-column section, breakdown column visibly wider than
   recent activity, visible borders around each card/panel, generous spacing, content centered
   within the same max width as the other pages (FR-016, FR-018).
2. Sweep the viewport from 2560 px down to 320 px: panels stack in reading order (KPI → breakdown →
   recent activity), no horizontal scrollbar, nothing clipped or overlapping at any width (FR-017,
   SC-005).
3. With **Very large counts**: the number stays fully inside its card, with locale thousands
   separators and no truncation or abbreviation (FR-019).

### Loading, error, and timeout states

1. Throttle the network (DevTools → Slow 3G) and reload: an explicit loading indication appears —
   no flash of zeros or empty states (FR-015).
2. **Data unavailable**: one clear error message replaces all three data sections; no zeros, no
   partial data (FR-014).
3. **Retrieval hangs**: within ~10 s the loading indication is replaced by that same error state —
   never a permanent spinner (FR-015a, SC-010).
4. With the backend healthy on localhost, the complete content appears within 2 s (SC-009).

### Accessibility & counter-safety

1. Screen reader (VoiceOver/NVDA): navigate by heading — the page `<h1>` then "Summary",
   "Activity breakdown", "Recent activity" `<h2>`s (FR-024). Each breakdown row is announced once as
   code + count, each recent entry once as code + time; no bar is announced (FR-022, FR-023, SC-008).
2. Keyboard only: `Tab` through the page — nothing on it takes focus, nothing traps focus, and no
   value requires a pointer to reveal (FR-026).
3. Counter-safety: note `curl "$BASE/exchange/usage"` output, reload the page 10 times, re-`curl` —
   every `queryCount` and `lastQueriedAt` is unchanged (FR-021, SC-007).
4. Network panel: exactly **one** `GET /exchange/usage` per page load, with no `limit` and no
   `recentDays` query parameter (FR-005a, ui-contract §Backend calls).
