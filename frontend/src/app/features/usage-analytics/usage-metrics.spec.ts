import { BREAKDOWN_ROW_LIMIT, computeUsageSummary } from './usage-metrics';
import type { CurrencyUsageEntry } from '../../api-client';

function entry(currencyCode: string, queryCount: number): CurrencyUsageEntry {
  return {
    currencyCode,
    queryCount,
    lastQueriedAt: queryCount > 0 ? '2026-08-23T09:00:00Z' : null,
  };
}

describe('computeUsageSummary', () => {
  it('returns zero totals and a null mostQueried for an empty entry array (data-model.md §2.1, US1 scenario 4)', () => {
    expect(computeUsageSummary([])).toEqual({
      totalQueries: 0,
      queriedCurrencyCount: 0,
      mostQueried: null,
    });
  });

  it('sums queryCount over every entry, including entries the 10-row breakdown cap would drop (FR-003, FR-005a)', () => {
    // 12 queried currencies — more than BREAKDOWN_ROW_LIMIT, so the two lowest-ranked
    // entries fall outside the breakdown panel but must still be summed here (INV-2).
    const entries = [
      entry('AUD', 12),
      entry('BGN', 11),
      entry('CAD', 10),
      entry('CHF', 9),
      entry('CNY', 8),
      entry('CZK', 7),
      entry('DKK', 6),
      entry('EUR', 5),
      entry('GBP', 4),
      entry('HKD', 3),
      entry('HUF', 2),
      entry('IDR', 1),
    ];
    expect(entries.length).toBeGreaterThan(BREAKDOWN_ROW_LIMIT);

    const summary = computeUsageSummary(entries);
    expect(summary.totalQueries).toBe(78);
    expect(summary.queriedCurrencyCount).toBe(12);
  });

  it('counts only currencies queried at least once, excluding queryCount === 0 (FR-004, data-model.md §2.1)', () => {
    const entries = [entry('USD', 12), entry('EUR', 5), entry('GBP', 0), entry('JPY', 0)];
    const summary = computeUsageSummary(entries);
    expect(summary.queriedCurrencyCount).toBe(2);
    expect(summary.totalQueries).toBe(17);
  });

  it('selects the currency with the highest query count as mostQueried (FR-005, US1 scenario 2)', () => {
    const entries = [entry('EUR', 5), entry('USD', 12), entry('GBP', 9)];
    expect(computeUsageSummary(entries).mostQueried).toEqual({
      currencyCode: 'USD',
      queryCount: 12,
    });
  });

  it('breaks a tie for highest count on the alphabetically first currency code (FR-005, US1 scenario 3)', () => {
    const entries = [entry('USD', 12), entry('CHF', 12), entry('EUR', 12), entry('GBP', 3)];
    expect(computeUsageSummary(entries).mostQueried).toEqual({
      currencyCode: 'CHF',
      queryCount: 12,
    });
  });

  it('returns mostQueried === null when no currency has ever been queried (FR-005, US1 scenario 4)', () => {
    const entries = [entry('USD', 0), entry('EUR', 0), entry('GBP', 0)];
    expect(computeUsageSummary(entries)).toEqual({
      totalQueries: 0,
      queriedCurrencyCount: 0,
      mostQueried: null,
    });
  });

  it('does not mutate or reorder the input array (INV-6, data-model.md §1 "derivations copy before sorting")', () => {
    const entries = [entry('EUR', 5), entry('USD', 12), entry('GBP', 9)];
    const snapshot = entries.map((item) => ({ ...item }));

    computeUsageSummary(entries);

    expect(entries).toEqual(snapshot);
  });
});
