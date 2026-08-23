import {
  BREAKDOWN_ROW_LIMIT,
  RECENT_ENTRY_LIMIT,
  buildBreakdownView,
  buildRecentActivity,
  computeUsageSummary,
} from './usage-metrics';
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

describe('buildBreakdownView', () => {
  it('returns no rows and zero counts for an empty entry array (data-model.md §2.2, FR-013)', () => {
    expect(buildBreakdownView([])).toEqual({
      rows: [],
      displayedCount: 0,
      queriedTotal: 0,
      neverQueriedCount: 0,
    });
  });

  it('excludes queryCount === 0 entries from rows entirely (FR-006, US2 scenario 4)', () => {
    const entries = [entry('USD', 12), entry('GBP', 0), entry('EUR', 5), entry('JPY', 0)];

    const view = buildBreakdownView(entries);

    expect(view.rows.map((row) => row.currencyCode)).toEqual(['USD', 'EUR']);
  });

  it('renders no rows at all when no currency has ever been queried, while the footnote still counts them (FR-006, FR-009a, US2 scenario 6)', () => {
    const entries = [entry('USD', 0), entry('EUR', 0), entry('GBP', 0)];

    expect(buildBreakdownView(entries)).toEqual({
      rows: [],
      displayedCount: 0,
      queriedTotal: 0,
      neverQueriedCount: 3,
    });
  });

  it('orders rows by queryCount descending (FR-006, US2 scenario 1)', () => {
    const entries = [entry('EUR', 5), entry('USD', 12), entry('CHF', 1), entry('GBP', 9)];

    const view = buildBreakdownView(entries);

    expect(view.rows.map((row) => row.currencyCode)).toEqual(['USD', 'GBP', 'EUR', 'CHF']);
  });

  it('breaks queryCount ties on alphabetically ascending currencyCode (FR-006, data-model.md §2.2)', () => {
    const entries = [entry('USD', 7), entry('CHF', 7), entry('EUR', 7), entry('AUD', 3)];

    const view = buildBreakdownView(entries);

    expect(view.rows.map((row) => row.currencyCode)).toEqual(['CHF', 'EUR', 'USD', 'AUD']);
  });

  it('caps rows at BREAKDOWN_ROW_LIMIT, keeping the highest-ranked entries (FR-009, US2 scenario 3)', () => {
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

    const view = buildBreakdownView(entries);

    expect(view.rows).toHaveLength(BREAKDOWN_ROW_LIMIT);
    expect(view.rows.map((row) => row.currencyCode)).toEqual([
      'AUD',
      'BGN',
      'CAD',
      'CHF',
      'CNY',
      'CZK',
      'DKK',
      'EUR',
      'GBP',
      'HKD',
    ]);
  });

  it('gives the top row a proportionPercent of 100 and scales the rest against it (FR-008, US2 scenario 2)', () => {
    const entries = [entry('EUR', 25), entry('USD', 100), entry('GBP', 50)];

    const view = buildBreakdownView(entries);

    expect(view.rows).toEqual([
      { currencyCode: 'USD', queryCount: 100, proportionPercent: 100 },
      { currencyCode: 'GBP', queryCount: 50, proportionPercent: 50 },
      { currencyCode: 'EUR', queryCount: 25, proportionPercent: 25 },
    ]);
  });

  it('measures proportionPercent against the highest displayed count after the cap is applied (FR-008, FR-009)', () => {
    // 11 queried currencies: 'KRW' (count 1) ranks 11th and is dropped by the cap, so it can
    // neither appear as a row nor shift the proportions of the rows that survive.
    const entries = [
      entry('AUD', 100),
      entry('BGN', 90),
      entry('CAD', 80),
      entry('CHF', 70),
      entry('CNY', 60),
      entry('CZK', 50),
      entry('DKK', 40),
      entry('EUR', 30),
      entry('GBP', 20),
      entry('HKD', 10),
      entry('KRW', 1),
    ];

    const view = buildBreakdownView(entries);

    expect(view.rows[0]).toEqual({ currencyCode: 'AUD', queryCount: 100, proportionPercent: 100 });
    expect(view.rows.at(-1)).toEqual({
      currencyCode: 'HKD',
      queryCount: 10,
      proportionPercent: 10,
    });
  });

  it('gives every row a proportionPercent of 100 when all displayed counts are tied (FR-008, spec edge case "all counts equal")', () => {
    const entries = [entry('USD', 4), entry('EUR', 4), entry('GBP', 4)];

    const view = buildBreakdownView(entries);

    expect(view.rows.map((row) => row.proportionPercent)).toEqual([100, 100, 100]);
  });

  it('gives a lone row a proportionPercent of 100 (FR-008, data-model.md §2.2)', () => {
    const view = buildBreakdownView([entry('USD', 3), entry('EUR', 0)]);

    expect(view.rows).toEqual([{ currencyCode: 'USD', queryCount: 3, proportionPercent: 100 }]);
  });

  it('rounds proportionPercent to 2 decimal places (FR-008, data-model.md §2.2)', () => {
    const entries = [entry('USD', 7), entry('EUR', 3), entry('GBP', 1)];

    const view = buildBreakdownView(entries);

    expect(view.rows.map((row) => row.proportionPercent)).toEqual([100, 42.86, 14.29]);
  });

  it('reports queriedTotal over all queried currencies while displayedCount tracks the capped rows (FR-009, US2 scenario 3)', () => {
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
      entry('ILS', 0),
    ];

    const view = buildBreakdownView(entries);

    expect(view.queriedTotal).toBe(12);
    expect(view.displayedCount).toBe(BREAKDOWN_ROW_LIMIT);
    expect(view.displayedCount).toBe(view.rows.length);
    expect(view.queriedTotal).toBeGreaterThan(view.displayedCount);
  });

  it('sets displayedCount equal to queriedTotal when every queried currency fits under the cap (FR-009)', () => {
    const entries = [entry('USD', 12), entry('EUR', 5), entry('GBP', 0)];

    const view = buildBreakdownView(entries);

    expect(view.displayedCount).toBe(2);
    expect(view.queriedTotal).toBe(2);
  });

  it('counts neverQueriedCount across all entries, including those the cap drops (FR-009a, US2 scenario 4)', () => {
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
      entry('IDR', 0),
      entry('ILS', 0),
      entry('INR', 0),
    ];

    const view = buildBreakdownView(entries);

    expect(view.rows).toHaveLength(BREAKDOWN_ROW_LIMIT);
    expect(view.neverQueriedCount).toBe(3);
  });

  it('reports neverQueriedCount === 0 when every known currency has been queried at least once (FR-009a, US2 scenario 5)', () => {
    const entries = [entry('USD', 12), entry('EUR', 5), entry('GBP', 1)];

    expect(buildBreakdownView(entries).neverQueriedCount).toBe(0);
  });

  it('gives every row a queryCount of at least 1 (INV-3, FR-009)', () => {
    const entries = [
      entry('USD', 12),
      entry('EUR', 0),
      entry('GBP', 1),
      entry('JPY', 0),
      entry('CHF', 4),
    ];

    const view = buildBreakdownView(entries);

    expect(view.rows.length).toBeGreaterThan(0);
    for (const row of view.rows) {
      expect(row.queryCount).toBeGreaterThanOrEqual(1);
    }
  });

  it('keeps neverQueriedCount + queriedTotal equal to the entry count, matching the KPI queriedCurrencyCount (INV-4, data-model.md §2.2)', () => {
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
      entry('IDR', 0),
      entry('ILS', 0),
    ];

    const view = buildBreakdownView(entries);

    expect(view.neverQueriedCount + view.queriedTotal).toBe(entries.length);
    expect(view.queriedTotal).toBe(computeUsageSummary(entries).queriedCurrencyCount);
  });
});

describe('buildRecentActivity', () => {
  // Fixed load-time `now`, passed explicitly: the phrases are pure functions of
  // `(instant, now)` and never advance, so no fake timers are needed (data-model.md §3).
  const NOW = new Date('2026-08-23T12:00:00Z');

  function queried(
    currencyCode: string,
    lastQueriedAt: string | null,
    queryCount = 1,
  ): CurrencyUsageEntry {
    return { currencyCode, queryCount, lastQueriedAt };
  }

  /** The FR-012a formatting contract, expressed locale-independently (data-model.md §3). */
  function expectedAbsoluteLocal(instant: string): string {
    return new Intl.DateTimeFormat(undefined, { dateStyle: 'medium', timeStyle: 'short' }).format(
      new Date(instant),
    );
  }

  it('returns no entries for an empty entry array (data-model.md §2.3, FR-013, US3 scenario 4)', () => {
    expect(buildRecentActivity([], NOW)).toEqual([]);
  });

  it('returns no entries when no currency has ever been queried (FR-011, US3 scenario 4)', () => {
    const entries = [queried('USD', null, 0), queried('EUR', null, 0), queried('GBP', null, 0)];

    expect(buildRecentActivity(entries, NOW)).toEqual([]);
  });

  it('excludes entries with lastQueriedAt === null (FR-011, US3 scenario 2)', () => {
    const entries = [
      queried('USD', '2026-08-23T11:55:00Z'),
      queried('EUR', null, 0),
      queried('GBP', '2026-08-23T10:00:00Z'),
      queried('JPY', null, 0),
    ];

    expect(buildRecentActivity(entries, NOW).map((item) => item.currencyCode)).toEqual([
      'USD',
      'GBP',
    ]);
  });

  it('excludes a currency with queryCount > 0 but a null lastQueriedAt, which still appears in the breakdown (data-model.md §2.3, spec edge case "query count but no recorded last-queried time")', () => {
    const entries = [
      queried('USD', '2026-08-23T11:55:00Z', 12),
      queried('CHF', null, 7),
      queried('GBP', '2026-08-23T10:00:00Z', 3),
    ];

    expect(buildRecentActivity(entries, NOW).map((item) => item.currencyCode)).toEqual([
      'USD',
      'GBP',
    ]);
    expect(buildBreakdownView(entries).rows.map((row) => row.currencyCode)).toContain('CHF');
  });

  it('orders entries by lastQueriedAt descending, most recent first (FR-010, US3 scenario 1)', () => {
    const entries = [
      queried('GBP', '2026-08-21T12:00:00Z'),
      queried('USD', '2026-08-23T11:58:00Z'),
      queried('CHF', '2026-07-30T09:15:00Z'),
      queried('EUR', '2026-08-23T08:00:00Z'),
    ];

    expect(buildRecentActivity(entries, NOW).map((item) => item.currencyCode)).toEqual([
      'USD',
      'EUR',
      'GBP',
      'CHF',
    ]);
  });

  it('orders by the represented instant, not by the raw string, across differing UTC offsets (data-model.md §2.3)', () => {
    // 09:30+02:00 === 07:30Z, so EUR is the *older* instant despite the larger literal.
    const entries = [
      queried('EUR', '2026-08-23T09:30:00+02:00'),
      queried('USD', '2026-08-23T08:00:00Z'),
    ];

    expect(buildRecentActivity(entries, NOW).map((item) => item.currencyCode)).toEqual([
      'USD',
      'EUR',
    ]);
  });

  it('breaks identical instants on alphabetically ascending currencyCode (SC-006, data-model.md §2.3)', () => {
    const entries = [
      queried('USD', '2026-08-23T11:00:00Z'),
      queried('CHF', '2026-08-23T11:00:00Z'),
      queried('EUR', '2026-08-23T11:00:00Z'),
      queried('AUD', '2026-08-22T11:00:00Z'),
    ];

    expect(buildRecentActivity(entries, NOW).map((item) => item.currencyCode)).toEqual([
      'CHF',
      'EUR',
      'USD',
      'AUD',
    ]);
  });

  it('caps entries at RECENT_ENTRY_LIMIT, keeping the most recent (FR-011, US3 scenario 3)', () => {
    const entries = [
      queried('AUD', '2026-08-23T11:00:00Z'),
      queried('BGN', '2026-08-23T10:00:00Z'),
      queried('CAD', '2026-08-23T09:00:00Z'),
      queried('CHF', '2026-08-23T08:00:00Z'),
      queried('CNY', '2026-08-23T07:00:00Z'),
      queried('CZK', '2026-08-23T06:00:00Z'),
      queried('DKK', '2026-08-23T05:00:00Z'),
      queried('EUR', '2026-08-23T04:00:00Z'),
      queried('GBP', '2026-08-23T03:00:00Z'),
      queried('HKD', '2026-08-23T02:00:00Z'),
    ];
    expect(entries.length).toBeGreaterThan(RECENT_ENTRY_LIMIT);

    const recent = buildRecentActivity(entries, NOW);

    expect(recent).toHaveLength(RECENT_ENTRY_LIMIT);
    expect(recent.map((item) => item.currencyCode)).toEqual([
      'AUD',
      'BGN',
      'CAD',
      'CHF',
      'CNY',
      'CZK',
      'DKK',
      'EUR',
    ]);
  });

  it('keeps lastQueriedAt verbatim from the API for the machine-readable datetime attribute (FR-025)', () => {
    const entries = [
      queried('USD', '2026-08-23T11:58:00Z'),
      queried('EUR', '2026-08-23T09:30:00+02:00'),
    ];

    expect(buildRecentActivity(entries, NOW).map((item) => item.lastQueriedAt)).toEqual([
      '2026-08-23T11:58:00Z',
      '2026-08-23T09:30:00+02:00',
    ]);
  });

  it('attaches a non-empty relativePhrase to every entry (FR-012, data-model.md §3)', () => {
    const entries = [
      queried('USD', '2026-08-23T11:58:00Z'),
      queried('EUR', '2026-08-21T12:00:00Z'),
      queried('GBP', '2025-08-23T12:00:00Z'),
    ];

    const recent = buildRecentActivity(entries, NOW);

    expect(recent).toHaveLength(3);
    for (const item of recent) {
      // Exact wording is relative-time.spec.ts's contract, not this one.
      expect(typeof item.relativePhrase).toBe('string');
      expect(item.relativePhrase.length).toBeGreaterThan(0);
    }
  });

  it('attaches the local absolute date-time of the same instant as absoluteLocal (FR-012a, data-model.md §3)', () => {
    const entries = [
      queried('USD', '2026-08-23T11:58:00Z'),
      queried('EUR', '2026-08-21T12:00:00Z'),
    ];

    const recent = buildRecentActivity(entries, NOW);

    expect(recent).toHaveLength(2);
    for (const item of recent) {
      expect(item.absoluteLocal).toBe(expectedAbsoluteLocal(item.lastQueriedAt));
      expect(item.absoluteLocal.length).toBeGreaterThan(0);
    }
  });

  it('does not mutate or reorder the input array (INV-6, data-model.md §1 "derivations copy before sorting")', () => {
    const entries = [
      queried('GBP', '2026-08-21T12:00:00Z'),
      queried('USD', '2026-08-23T11:58:00Z'),
      queried('CHF', null, 0),
      queried('EUR', '2026-08-23T08:00:00Z'),
    ];
    const snapshot = entries.map((item) => ({ ...item }));

    buildRecentActivity(entries, NOW);

    expect(entries).toEqual(snapshot);
  });
});

describe('usage derivation determinism (SC-006, INV-6)', () => {
  it('produces identical complete views repeatedly without mutating the resource input', () => {
    const now = new Date('2026-08-23T12:00:00Z');
    const entries: CurrencyUsageEntry[] = [
      { currencyCode: 'USD', queryCount: 12, lastQueriedAt: '2026-08-23T11:00:00Z' },
      { currencyCode: 'AUD', queryCount: 3, lastQueriedAt: null },
      { currencyCode: 'EUR', queryCount: 12, lastQueriedAt: '2026-08-23T11:00:00Z' },
      { currencyCode: 'CHF', queryCount: 12, lastQueriedAt: '2026-08-23T11:00:00Z' },
      { currencyCode: 'GBP', queryCount: 0, lastQueriedAt: null },
    ];
    const inputSnapshot = entries.map((item) => ({ ...item }));
    const derive = () => ({
      summary: computeUsageSummary(entries),
      breakdown: buildBreakdownView(entries),
      recentActivity: buildRecentActivity(entries, now),
    });

    const first = derive();
    const second = derive();

    expect(second).toEqual(first);
    expect(second.summary.mostQueried).toEqual({ currencyCode: 'CHF', queryCount: 12 });
    expect(second.breakdown.rows.map((row) => row.currencyCode)).toEqual([
      'CHF',
      'EUR',
      'USD',
      'AUD',
    ]);
    expect(second.recentActivity.map((item) => item.currencyCode)).toEqual(['CHF', 'EUR', 'USD']);
    expect(second.recentActivity.map((item) => item.relativePhrase)).toEqual(
      first.recentActivity.map((item) => item.relativePhrase),
    );
    expect(entries).toEqual(inputSnapshot);
  });
});
