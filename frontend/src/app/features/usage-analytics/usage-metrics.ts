/**
 * View models and display caps for the usage analytics page (data-model.md §2).
 *
 * Dependency-free by design (research.md §9): every derivation added here takes a
 * `readonly CurrencyUsageEntry[]` from the single `GET /exchange/usage` response and
 * returns plain data, so each acceptance scenario is a one-line unit assertion.
 */

import type { CurrencyUsageEntry } from '../../api-client';

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

  // Codepoint comparison, not `localeCompare`: currency codes are ASCII A–Z, and the ordering
  // must not shift with the viewer's locale (SC-006).
  const [top] = queried.sort(
    (a, b) => b.queryCount - a.queryCount || (a.currencyCode < b.currencyCode ? -1 : 1),
  );

  return {
    totalQueries,
    queriedCurrencyCount: queried.length,
    mostQueried: top ? { currencyCode: top.currencyCode, queryCount: top.queryCount } : null,
  };
}
