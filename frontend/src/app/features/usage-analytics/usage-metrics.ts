/**
 * View models and display caps for the usage analytics page (data-model.md §2).
 *
 * Dependency-free by design (research.md §9): every derivation added here takes a
 * `readonly CurrencyUsageEntry[]` from the single `GET /exchange/usage` response and
 * returns plain data, so each acceptance scenario is a one-line unit assertion.
 */

import type { CurrencyUsageEntry } from '../../api-client';
import { absoluteLocal, isParsableInstant, relativePhrase } from './relative-time';

/**
 * Backs the three KPI cards (data-model.md §2.1, FR-003 … FR-005a). Computed from the
 * complete, unlimited entry set — the display caps below never narrow its input (INV-2).
 */
export interface UsageSummary {
  totalQueries: number;
  queriedCurrencyCount: number;
  mostQueried: { currencyCode: string; queryCount: number } | null;
}

/**
 * One row of the breakdown panel (data-model.md §2.2, FR-006 … FR-008).
 * Invariant: `queryCount >= 1`; `proportionPercent` is relative to the highest count
 * among displayed rows, so the top row is always 100% (INV-3, FR-008).
 */
export interface RankedUsageRow {
  currencyCode: string;
  queryCount: number;
  proportionPercent: number;
}

/**
 * The breakdown panel as a whole (data-model.md §2.2). `queriedTotal` drives the
 * "top 10 of N" indication (FR-009); `neverQueriedCount` is counted across every entry,
 * not just displayed rows (FR-009a, INV-4).
 */
export interface BreakdownView {
  rows: RankedUsageRow[];
  displayedCount: number;
  queriedTotal: number;
  neverQueriedCount: number;
}

/**
 * One entry of the recent-activity panel (data-model.md §2.3, FR-010 … FR-012a).
 * `lastQueriedAt` is the verbatim non-null ISO-8601 instant for the `datetime`
 * attribute (FR-025); the phrasing fields are presentation only, fixed at load time.
 */
export interface RecentActivityEntry {
  currencyCode: string;
  lastQueriedAt: string;
  relativePhrase: string;
  absoluteLocal: string;
}

/** Display-only cap on breakdown rows (data-model.md §2.2, FR-009). */
export const BREAKDOWN_ROW_LIMIT = 10;

/** Display-only cap on recent-activity entries (data-model.md §2.3, FR-011). */
export const RECENT_ENTRY_LIMIT = 8;

/**
 * The single shared count formatter (data-model.md §4, research.md §5). Constructed once at
 * module level rather than per call so repeated row rendering reuses one instance. No options:
 * the viewer's default locale, integer path — thousands separators only.
 */
const COUNT_FORMATTER = new Intl.NumberFormat();

/**
 * Formats a query count for display (FR-019, data-model.md §4): locale thousands separators,
 * never rounded, abbreviated, or truncated. View models keep counts raw; formatting is applied
 * at render time only.
 */
export function formatCount(value: number): string {
  return COUNT_FORMATTER.format(value);
}

/**
 * The single FR-006 ordering, shared by every derivation that ranks currencies: `queryCount`
 * DESC, then `currencyCode` ASC as the tie-break (data-model.md §2.1, §2.2).
 *
 * Codepoint comparison, not `localeCompare`: currency codes are ASCII A–Z, and the ordering must
 * not shift with the viewer's locale (SC-006). Codes are unique across the response
 * (data-model.md §1), so the two-level comparison is already total.
 */
function compareByUsageRank(a: CurrencyUsageEntry, b: CurrencyUsageEntry): number {
  return b.queryCount - a.queryCount || (a.currencyCode < b.currencyCode ? -1 : 1);
}

/**
 * Derives the three KPI card values (data-model.md §2.1, FR-003 … FR-005a).
 *
 * `entries` is the complete, unlimited response set: the sum (FR-003) and the queried-currency
 * count (FR-004) span every currency known to the system, and `mostQueried` is resolved here —
 * before the §2.2 / §2.3 display caps, which never narrow this input (FR-005a, INV-2).
 *
 * `mostQueried` follows the FR-006 ordering restricted to `queryCount > 0`: highest `queryCount`,
 * ties broken by alphabetically first `currencyCode`, so it is stable across reloads (FR-005,
 * SC-006). It is `null` when no currency has ever been queried (US1 scenario 4).
 *
 * Pure: the input is treated as immutable and sorted on a copy (data-model.md §1, INV-6).
 */
export function computeUsageSummary(entries: readonly CurrencyUsageEntry[]): UsageSummary {
  let totalQueries = 0;
  const queried: CurrencyUsageEntry[] = [];

  for (const entry of entries) {
    totalQueries += entry.queryCount;
    if (entry.queryCount > 0) {
      queried.push(entry);
    }
  }

  const [top] = queried.sort(compareByUsageRank);

  return {
    totalQueries,
    queriedCurrencyCount: queried.length,
    mostQueried: top ? { currencyCode: top.currencyCode, queryCount: top.queryCount } : null,
  };
}

/**
 * Derives the breakdown panel: the ranked, capped rows plus the counts its footnote needs
 * (data-model.md §2.2, FR-006 … FR-009a).
 *
 * Order of operations is exactly the one §2.2 prescribes:
 * 1. **Filter** — entries with `queryCount === 0` are excluded from the rows entirely, so every
 *    row has `queryCount >= 1` and no bar can be zero-length (FR-006, INV-3, US2 scenario 4).
 * 2. **Order** — `queryCount` DESC, `currencyCode` ASC on ties, via the shared FR-006 comparator,
 *    so the ranking matches `mostQueried` and is stable across reloads (FR-006, SC-006).
 * 3. **Cap** — the first `BREAKDOWN_ROW_LIMIT` rows (FR-009). Display-only: the dropped entries
 *    still count towards `queriedTotal`, which drives the "top 10 of N" indication.
 *
 * `proportionPercent` is measured against the highest count **among displayed rows**, resolved
 * after the cap, so the top row is always exactly 100 and a dropped entry can never shift the
 * surviving bars (FR-008). Rounded to 2 dp; all-tied counts give every bar 100.
 *
 * `neverQueriedCount` is counted across every entry, not just the displayed rows, so the footnote
 * stays correct when the cap drops entries and when no currency has been queried at all
 * (FR-009a, INV-4). `neverQueriedCount + queriedTotal` therefore always equals `entries.length`.
 *
 * Pure: the input is treated as immutable and sorted on a copy (data-model.md §1, INV-6).
 */
export function buildBreakdownView(entries: readonly CurrencyUsageEntry[]): BreakdownView {
  const queried = entries.filter((entry) => entry.queryCount > 0).sort(compareByUsageRank);
  const displayed = queried.slice(0, BREAKDOWN_ROW_LIMIT);
  const highestDisplayedCount = displayed[0]?.queryCount ?? 0;

  return {
    rows: displayed.map((entry) => ({
      currencyCode: entry.currencyCode,
      queryCount: entry.queryCount,
      proportionPercent: Math.round((entry.queryCount / highestDisplayedCount) * 10000) / 100,
    })),
    displayedCount: displayed.length,
    queriedTotal: queried.length,
    neverQueriedCount: entries.length - queried.length,
  };
}

/** A source entry already known to carry a non-null, parsable `lastQueriedAt` (data-model.md §2.3). */
type TimestampedEntry = CurrencyUsageEntry & { lastQueriedAt: string };

/**
 * The §2.3 ordering: `lastQueriedAt` DESC, then `currencyCode` ASC for identical instants.
 *
 * Compares the represented **instant** (`Date.parse`), not the raw string — `09:30+02:00` and
 * `07:30Z` are the same moment, and a lexical comparison would rank equivalent instants written
 * with different UTC offsets wrongly. Codepoint tie-break, matching {@link compareByUsageRank}:
 * codes are ASCII and unique, so the ordering is total and locale-independent (SC-006).
 */
function compareByRecency(a: TimestampedEntry, b: TimestampedEntry): number {
  return (
    Date.parse(b.lastQueriedAt) - Date.parse(a.lastQueriedAt) ||
    (a.currencyCode < b.currencyCode ? -1 : 1)
  );
}

/**
 * Derives the recent-activity panel (data-model.md §2.3, FR-010 … FR-012a, FR-025).
 *
 * Order of operations is exactly the one §2.3 prescribes:
 * 1. **Filter** — entries with a `null` `lastQueriedAt` are excluded (FR-011), as are instants the
 *    time layer cannot parse ({@link isParsableInstant}): an unphraseable timestamp is treated as
 *    absent rather than rendered broken. This is the spec's "query count but no recorded
 *    last-queried time" edge case — such a currency still appears in the breakdown panel (§2.2).
 * 2. **Order** — most recent first, ties broken on alphabetically ascending `currencyCode`, so the
 *    panel is stable across reloads (SC-006).
 * 3. **Cap** — the first `RECENT_ENTRY_LIMIT` entries (FR-011). Display-only.
 *
 * `now` is the load-time instant captured once by the caller and threaded through, so every phrase
 * is fixed at load and never ticks forward (data-model.md §3, SC-006). `relativePhrase` (FR-012)
 * and `absoluteLocal` (FR-012a) are presentation only; `lastQueriedAt` is carried through
 * **verbatim** from the API for the machine-readable `datetime` attribute (FR-025).
 *
 * Pure: the input is treated as immutable and sorted on a copy (data-model.md §1, INV-6) — the
 * `filter` above already produces the copy that `sort` then reorders.
 */
export function buildRecentActivity(
  entries: readonly CurrencyUsageEntry[],
  now: Date,
): RecentActivityEntry[] {
  return entries
    .filter(
      (entry): entry is TimestampedEntry =>
        entry.lastQueriedAt !== null && isParsableInstant(entry.lastQueriedAt),
    )
    .sort(compareByRecency)
    .slice(0, RECENT_ENTRY_LIMIT)
    .map((entry) => ({
      currencyCode: entry.currencyCode,
      lastQueriedAt: entry.lastQueriedAt,
      relativePhrase: relativePhrase(entry.lastQueriedAt, now),
      absoluteLocal: absoluteLocal(entry.lastQueriedAt),
    }));
}
