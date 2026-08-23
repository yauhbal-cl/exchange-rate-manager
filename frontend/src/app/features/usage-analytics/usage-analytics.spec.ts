import { TestBed } from '@angular/core/testing';
import { HttpErrorResponse } from '@angular/common/http';
import { Subject, of, throwError } from 'rxjs';
import { UsageAnalytics } from './usage-analytics';
import {
  ExchangeRateUsageAnalyticsService,
  type CurrencyUsageEntry,
  type UsageAnalyticsResponse,
} from '../../api-client';

async function flush(fixture: {
  whenStable(): Promise<unknown>;
  detectChanges(): void;
}): Promise<void> {
  await fixture.whenStable();
  fixture.detectChanges();
}

function usageResponse(overrides: Partial<UsageAnalyticsResponse> = {}): UsageAnalyticsResponse {
  return {
    currencies: [
      { currencyCode: 'USD', queryCount: 12, lastQueriedAt: '2026-08-23T09:00:00Z' },
      { currencyCode: 'EUR', queryCount: 5, lastQueriedAt: '2026-08-22T09:00:00Z' },
      { currencyCode: 'GBP', queryCount: 0, lastQueriedAt: null },
    ],
    ...overrides,
  };
}

function loadingState(fixture: { nativeElement: HTMLElement }): Element | null {
  return fixture.nativeElement.querySelector('[data-testid="usage-loading"]');
}

function errorState(fixture: { nativeElement: HTMLElement }): Element | null {
  return fixture.nativeElement.querySelector('[data-testid="usage-error"]');
}

/**
 * 13 queried currencies (more than the FR-009 top-10 display cap) plus three never-queried ones,
 * deliberately unsorted so the KPI assertions can't pass by reading the first entry. Ranks 11-13
 * carry substantial counts, which is what makes the FR-005a "beyond the top 10" check bite: the
 * true total is well above the top-10 subtotal.
 */
const POPULATED_ENTRIES: readonly CurrencyUsageEntry[] = [
  { currencyCode: 'SEK', queryCount: 760, lastQueriedAt: '2026-08-19T08:00:00Z' },
  { currencyCode: 'PLN', queryCount: 375, lastQueriedAt: '2026-08-14T08:00:00Z' },
  { currencyCode: 'EUR', queryCount: 3120, lastQueriedAt: '2026-08-23T07:30:00Z' },
  { currencyCode: 'TRY', queryCount: 0, lastQueriedAt: null },
  { currencyCode: 'CAD', queryCount: 980, lastQueriedAt: '2026-08-21T08:00:00Z' },
  { currencyCode: 'USD', queryCount: 4210, lastQueriedAt: '2026-08-23T09:00:00Z' },
  { currencyCode: 'NOK', queryCount: 480, lastQueriedAt: '2026-08-15T08:00:00Z' },
  { currencyCode: 'CHF', queryCount: 1440, lastQueriedAt: '2026-08-22T08:00:00Z' },
  { currencyCode: 'ZAR', queryCount: 0, lastQueriedAt: null },
  { currencyCode: 'JPY', queryCount: 2050, lastQueriedAt: '2026-08-23T06:00:00Z' },
  { currencyCode: 'NZD', queryCount: 640, lastQueriedAt: '2026-08-18T08:00:00Z' },
  { currencyCode: 'MXN', queryCount: 590, lastQueriedAt: '2026-08-17T08:00:00Z' },
  { currencyCode: 'GBP', queryCount: 1875, lastQueriedAt: '2026-08-22T10:00:00Z' },
  { currencyCode: 'CNY', queryCount: 870, lastQueriedAt: '2026-08-20T08:00:00Z' },
  { currencyCode: 'AUD', queryCount: 1310, lastQueriedAt: '2026-08-21T12:00:00Z' },
  { currencyCode: 'BRL', queryCount: 0, lastQueriedAt: null },
];

/**
 * The FR-006 ranking of POPULATED_ENTRIES, capped at the 10 rows the panel displays — spelled out
 * rather than re-derived, so a regression in the component's ordering can't be mirrored by the
 * expectation. The three queried currencies below the cut are listed separately.
 */
const EXPECTED_TOP_TEN_CODES: readonly string[] = [
  'USD', // 4210
  'EUR', // 3120
  'JPY', // 2050
  'GBP', // 1875
  'CHF', // 1440
  'AUD', // 1310
  'CAD', // 980
  'CNY', // 870
  'SEK', // 760
  'NZD', // 640
];

/** Queried, but ranked 11th-13th, so the FR-009 cap keeps them out of the rows. */
const EXPECTED_DROPPED_CODES: readonly string[] = ['MXN', 'NOK', 'PLN'];

/**
 * Seven queried currencies — below the FR-009 cap, so every queried entry becomes a row and the
 * "top N of M" note stays away — with two deliberate count ties (900 and 150) that pin the
 * `currencyCode` ASC tie-break, plus two never-queried entries. Unsorted on purpose.
 */
const UNCAPPED_ENTRIES: readonly CurrencyUsageEntry[] = [
  { currencyCode: 'USD', queryCount: 900, lastQueriedAt: '2026-08-23T09:00:00Z' },
  { currencyCode: 'GBP', queryCount: 150, lastQueriedAt: '2026-08-20T09:00:00Z' },
  { currencyCode: 'SEK', queryCount: 0, lastQueriedAt: null },
  { currencyCode: 'EUR', queryCount: 1500, lastQueriedAt: '2026-08-23T08:00:00Z' },
  { currencyCode: 'CAD', queryCount: 75, lastQueriedAt: '2026-08-18T09:00:00Z' },
  { currencyCode: 'CHF', queryCount: 900, lastQueriedAt: '2026-08-22T09:00:00Z' },
  { currencyCode: 'NOK', queryCount: 0, lastQueriedAt: null },
  { currencyCode: 'AUD', queryCount: 150, lastQueriedAt: '2026-08-21T09:00:00Z' },
  { currencyCode: 'JPY', queryCount: 420, lastQueriedAt: '2026-08-22T06:00:00Z' },
];

/** UNCAPPED_ENTRIES ranked by FR-006: count DESC, then code ASC on the 900 and 150 ties. */
const EXPECTED_UNCAPPED_ROWS: readonly { code: string; count: number }[] = [
  { code: 'EUR', count: 1500 },
  { code: 'CHF', count: 900 },
  { code: 'USD', count: 900 },
  { code: 'JPY', count: 420 },
  { code: 'AUD', count: 150 },
  { code: 'GBP', count: 150 },
  { code: 'CAD', count: 75 },
];

/**
 * POPULATED_ENTRIES ranked by recency (last-queried DESC), capped at the eight entries the recent-
 * activity panel displays — spelled out rather than re-derived, so a regression in the component's
 * ordering can't be mirrored by the expectation. Note this is a different order from
 * EXPECTED_TOP_TEN_CODES: the fixture's counts and timestamps deliberately disagree, so a panel
 * accidentally fed the count ranking fails here.
 */
const EXPECTED_RECENT_CODES: readonly string[] = [
  'USD', // 2026-08-23T09:00Z
  'EUR', // 2026-08-23T07:30Z
  'JPY', // 2026-08-23T06:00Z
  'GBP', // 2026-08-22T10:00Z
  'CHF', // 2026-08-22T08:00Z
  'AUD', // 2026-08-21T12:00Z
  'CAD', // 2026-08-21T08:00Z
  'CNY', // 2026-08-20T08:00Z
];

/** Queried, but older than the eight above, so the FR-011 cap keeps them out of the panel. */
const EXPECTED_STALE_CODES: readonly string[] = ['SEK', 'NZD', 'MXN', 'NOK', 'PLN'];

/**
 * The FR-011 edge case the two panels must disagree on: SEK has been queried 640 times but carries
 * no recorded last-queried time, so it is a real breakdown row yet has no place in a list ordered
 * by recency. NOK is the ordinary never-queried case, absent from both.
 */
const NULL_TIMESTAMP_ENTRIES: readonly CurrencyUsageEntry[] = [
  { currencyCode: 'USD', queryCount: 4210, lastQueriedAt: '2026-08-23T09:00:00Z' },
  { currencyCode: 'SEK', queryCount: 640, lastQueriedAt: null },
  { currencyCode: 'NOK', queryCount: 0, lastQueriedAt: null },
  { currencyCode: 'EUR', queryCount: 3120, lastQueriedAt: '2026-08-23T07:30:00Z' },
];

/** Every known currency present but never looked up (US1 scenario 4, data-model.md §5). */
const NOTHING_QUERIED_ENTRIES: readonly CurrencyUsageEntry[] = ['USD', 'EUR', 'GBP', 'JPY'].map(
  (currencyCode) => ({ currencyCode, queryCount: 0, lastQueriedAt: null }),
);

function sumCounts(entries: readonly CurrencyUsageEntry[]): number {
  return entries.reduce((total, entry) => total + entry.queryCount, 0);
}

/**
 * The viewer's default locale, exactly as the component formats counts (FR-019) — asserting
 * through the same formatter keeps these specs locale-independent instead of hardcoding "4,210".
 */
function displayCount(value: number): string {
  return new Intl.NumberFormat().format(value);
}

function normalize(value: string | null | undefined): string {
  return (value ?? '').replace(/\s+/g, ' ').trim();
}

function kpiCard(fixture: { nativeElement: HTMLElement }, testId: string): HTMLElement {
  const card = fixture.nativeElement.querySelector<HTMLElement>(`[data-testid="${testId}"]`);
  expect(card, `expected a KPI card with data-testid="${testId}"`).not.toBeNull();
  return card as HTMLElement;
}

/** The headline number/word of a KPI card, without its label or hint copy. */
function kpiValue(fixture: { nativeElement: HTMLElement }, testId: string): string {
  return normalize(kpiCard(fixture, testId).querySelector('.kpi-value')?.textContent);
}

/** The breakdown rows in DOM order — the order the operator reads them in (FR-006). */
function breakdownRows(fixture: { nativeElement: HTMLElement }): HTMLElement[] {
  return Array.from(
    fixture.nativeElement.querySelectorAll<HTMLElement>('[data-testid="breakdown-row"]'),
  );
}

function rowCodes(fixture: { nativeElement: HTMLElement }): string[] {
  return breakdownRows(fixture).map((row) => row.getAttribute('data-code') ?? '');
}

function neverQueriedFootnote(fixture: { nativeElement: HTMLElement }): string {
  const footnote = fixture.nativeElement.querySelector('[data-testid="never-queried-footnote"]');
  expect(footnote, 'expected the never-queried footnote to be rendered').not.toBeNull();
  return normalize(footnote?.textContent);
}

/** The recent-activity entries in DOM order — newest first according to FR-010. */
function recentEntries(fixture: { nativeElement: HTMLElement }): HTMLElement[] {
  return Array.from(
    fixture.nativeElement.querySelectorAll<HTMLElement>('[data-testid="recent-entry"]'),
  );
}

function recentCodes(fixture: { nativeElement: HTMLElement }): string[] {
  return recentEntries(fixture).map((entry) => entry.getAttribute('data-code') ?? '');
}

async function renderWith(
  getUsageAnalytics: ReturnType<typeof vi.fn>,
  entries: readonly CurrencyUsageEntry[],
) {
  getUsageAnalytics.mockReturnValue(of(usageResponse({ currencies: [...entries] })));

  const fixture = TestBed.createComponent(UsageAnalytics);
  fixture.detectChanges();
  await flush(fixture);

  return fixture;
}

describe('UsageAnalytics', () => {
  let getUsageAnalytics: ReturnType<typeof vi.fn>;

  beforeEach(() => {
    getUsageAnalytics = vi.fn();
    TestBed.configureTestingModule({
      providers: [{ provide: ExchangeRateUsageAnalyticsService, useValue: { getUsageAnalytics } }],
    });
  });

  it('issues exactly one usage request, with no arguments, when the page loads (FR-005a)', async () => {
    getUsageAnalytics.mockReturnValue(of(usageResponse()));

    const fixture = TestBed.createComponent(UsageAnalytics);
    fixture.detectChanges();
    await flush(fixture);

    expect(getUsageAnalytics).toHaveBeenCalledTimes(1);
    expect(getUsageAnalytics).toHaveBeenCalledWith();
    expect(getUsageAnalytics.mock.calls[0]).toHaveLength(0);
  });

  it('shows only the loading state while the usage request is in flight (FR-015)', () => {
    const usage$ = new Subject<UsageAnalyticsResponse>();
    getUsageAnalytics.mockReturnValue(usage$);

    const fixture = TestBed.createComponent(UsageAnalytics);
    fixture.detectChanges();

    expect(loadingState(fixture)).not.toBeNull();
    expect(errorState(fixture)).toBeNull();
  });

  it('replaces the loading state with the single error message when the request fails (FR-014)', async () => {
    getUsageAnalytics.mockReturnValue(throwError(() => new HttpErrorResponse({ status: 503 })));

    const fixture = TestBed.createComponent(UsageAnalytics);
    fixture.detectChanges();
    await flush(fixture);

    expect(errorState(fixture)).not.toBeNull();
    expect(loadingState(fixture)).toBeNull();
  });

  it('stops waiting and shows the error state once the request exceeds 10 seconds (FR-015a)', async () => {
    vi.useFakeTimers();
    try {
      getUsageAnalytics.mockReturnValue(new Subject<UsageAnalyticsResponse>());

      const fixture = TestBed.createComponent(UsageAnalytics);
      fixture.detectChanges();
      expect(loadingState(fixture)).not.toBeNull();

      await vi.advanceTimersByTimeAsync(10_001);
      fixture.detectChanges();

      expect(errorState(fixture)).not.toBeNull();
      expect(loadingState(fixture)).toBeNull();
    } finally {
      vi.useRealTimers();
    }
  });

  it('renders the three KPI values from the whole payload, not just the top 10 (FR-005a, SC-003)', async () => {
    const fixture = await renderWith(getUsageAnalytics, POPULATED_ENTRIES);

    const queried = POPULATED_ENTRIES.filter((entry) => entry.queryCount > 0);
    const byCountDesc = [...queried].sort((a, b) => b.queryCount - a.queryCount);
    const expectedTotal = sumCounts(POPULATED_ENTRIES);
    const topTenSubtotal = sumCounts(byCountDesc.slice(0, 10));
    const [expectedTop] = byCountDesc;

    // Guards on the fixture itself: without these the assertions below could pass on a payload
    // that never exercised the "beyond the top 10" case.
    expect(queried.length).toBeGreaterThan(10);
    expect(expectedTotal).toBeGreaterThan(topTenSubtotal);

    expect(fixture.nativeElement.querySelector('[data-testid="kpi-row"]')).not.toBeNull();

    // FR-003: the sum spans every entry — the 11th-and-below currencies are included.
    expect(kpiValue(fixture, 'kpi-total-queries')).toBe(displayCount(expectedTotal));
    expect(kpiValue(fixture, 'kpi-total-queries')).not.toBe(displayCount(topTenSubtotal));

    // FR-004: zero-count currencies are excluded, everything queried is counted.
    expect(kpiValue(fixture, 'kpi-queried-currencies')).toBe(displayCount(queried.length));

    // FR-005: the top currency plus its count, both read off the full set.
    expect(kpiValue(fixture, 'kpi-most-queried')).toBe(expectedTop.currencyCode);
    expect(normalize(kpiCard(fixture, 'kpi-most-queried').textContent)).toContain(
      `Query count: ${displayCount(expectedTop.queryCount)}`,
    );
  });

  it('shows zeros and an explicit most-queried indication when nothing has been queried (FR-013, SC-003)', async () => {
    const fixture = await renderWith(getUsageAnalytics, NOTHING_QUERIED_ENTRIES);

    expect(fixture.nativeElement.querySelector('[data-testid="kpi-row"]')).not.toBeNull();

    expect(kpiValue(fixture, 'kpi-total-queries')).toBe(displayCount(0));
    expect(kpiValue(fixture, 'kpi-queried-currencies')).toBe(displayCount(0));

    // Neither blank nor a zero dressed up as a winning currency.
    const mostQueried = kpiValue(fixture, 'kpi-most-queried');
    expect(mostQueried).toBe('No currency queried yet');
    expect(normalize(kpiCard(fixture, 'kpi-most-queried').textContent)).not.toMatch(/\d/);
  });

  it('exposes the dashboard as a non-interactive heading and text hierarchy (FR-022–FR-024, FR-026, SC-008)', async () => {
    const fixture = await renderWith(getUsageAnalytics, UNCAPPED_ENTRIES);
    const page = fixture.nativeElement as HTMLElement;

    // Heading navigation follows the same order and levels as the visual layout.
    expect(
      Array.from(page.querySelectorAll<HTMLHeadingElement>('h1, h2')).map((heading) => ({
        level: heading.tagName.toLowerCase(),
        text: normalize(heading.textContent),
      })),
    ).toEqual([
      { level: 'h1', text: 'Usage analytics' },
      { level: 'h2', text: 'Summary' },
      { level: 'h2', text: 'Activity breakdown' },
      { level: 'h2', text: 'Recent activity' },
    ]);

    breakdownRows(fixture).forEach((row, index) => {
      const expected = EXPECTED_UNCAPPED_ROWS[index];
      const bar = row.querySelector<HTMLElement>('[data-testid="breakdown-bar"]');

      // Code and count each have one text carrier; the proportional graphic contributes no
      // duplicate name, value, tooltip or range semantics to the accessibility tree.
      expect(row.querySelectorAll('.row-code')).toHaveLength(1);
      expect(row.querySelectorAll('.row-count')).toHaveLength(1);
      expect(normalize(row.textContent)).toBe(`${expected.code}${displayCount(expected.count)}`);
      expect(bar?.getAttribute('aria-hidden')).toBe('true');
      for (const attribute of ['role', 'aria-label', 'aria-valuenow', 'title']) {
        expect(bar?.hasAttribute(attribute), `bar must not expose ${attribute}`).toBe(false);
      }
    });

    recentEntries(fixture).forEach((entry) => {
      const code = normalize(entry.querySelector('.entry-code')?.textContent);
      const time = normalize(entry.querySelector('.entry-time')?.textContent);

      expect(entry.querySelectorAll('.entry-code')).toHaveLength(1);
      expect(entry.querySelectorAll('time.entry-time')).toHaveLength(1);
      expect(code).not.toBe('');
      expect(time).not.toBe('');
      expect(normalize(entry.textContent)).toBe(`${code}${time}`);
    });

    // With no controls, links, editable content or non-negative tabindex, Tab cannot enter or be
    // trapped by the page and every value remains available as ordinary text.
    expect(
      page.querySelectorAll(
        'a[href], button, input, select, textarea, [contenteditable="true"], [tabindex]:not([tabindex="-1"])',
      ),
    ).toHaveLength(0);
  });

  it('renders one breakdown row per queried currency, ranked by count then code (FR-006, US2 scenario 3)', async () => {
    const fixture = await renderWith(getUsageAnalytics, UNCAPPED_ENTRIES);

    const rows = breakdownRows(fixture);

    // Below the display cap, so every queried currency is a row — and only those.
    expect(rows).toHaveLength(EXPECTED_UNCAPPED_ROWS.length);
    expect(rowCodes(fixture)).toEqual(EXPECTED_UNCAPPED_ROWS.map((row) => row.code));

    // Each row carries its own code and count, in the same position as the ranking above.
    rows.forEach((row, index) => {
      const expected = EXPECTED_UNCAPPED_ROWS[index];
      expect(normalize(row.querySelector('.row-code')?.textContent)).toBe(expected.code);
      expect(normalize(row.querySelector('.row-count')?.textContent)).toBe(
        displayCount(expected.count),
      );
    });

    // FR-009: nothing was hidden, so there is no "top N of M" line to explain away.
    expect(fixture.nativeElement.querySelector('.breakdown-note')).toBeNull();
  });

  it('gives never-queried currencies no row and no zero-length bar (FR-006, US2 scenario 4)', async () => {
    const fixture = await renderWith(getUsageAnalytics, UNCAPPED_ENTRIES);

    const neverQueried = UNCAPPED_ENTRIES.filter((entry) => entry.queryCount === 0);
    expect(neverQueried.length).toBeGreaterThan(0); // guard: the fixture must exercise the case

    const codes = rowCodes(fixture);
    for (const entry of neverQueried) {
      expect(codes).not.toContain(entry.currencyCode);
    }

    // Not merely absent from the codes: no row anywhere shows a zero count, and every rendered
    // bar belongs to a row that has one (INV-3).
    const counts = breakdownRows(fixture).map((row) =>
      normalize(row.querySelector('.row-count')?.textContent),
    );
    expect(counts).not.toContain(displayCount(0));
    expect(fixture.nativeElement.querySelectorAll('[data-testid="breakdown-bar"]')).toHaveLength(
      codes.length,
    );
  });

  it('states the never-queried figure for the whole payload, explicitly zero when there is none (FR-009a, US2 scenarios 4-5)', async () => {
    const expectedNeverQueried = POPULATED_ENTRIES.filter((entry) => entry.queryCount === 0).length;
    expect(expectedNeverQueried).toBeGreaterThan(0); // guard: the fixture must exercise the case

    const fixture = await renderWith(getUsageAnalytics, POPULATED_ENTRIES);

    // INV-4: counted across every entry, not just the ten displayed rows.
    const footnote = neverQueriedFootnote(fixture);
    expect(footnote).toContain(displayCount(expectedNeverQueried));
    expect(footnote).toMatch(/never been queried/i);

    // Scenario 5: with every known currency queried the figure is still stated, as an explicit
    // zero rather than a blank or a dropped sentence.
    const allQueried = POPULATED_ENTRIES.filter((entry) => entry.queryCount > 0);
    const queriedFixture = await renderWith(getUsageAnalytics, allQueried);

    const zeroFootnote = neverQueriedFootnote(queriedFixture);
    expect(zeroFootnote).toContain(displayCount(0));
    expect(zeroFootnote).toMatch(/never been queried/i);
  });

  it('caps the rows at ten and says it is showing the top 10 of 13 (FR-009, US2 scenario 3)', async () => {
    const fixture = await renderWith(getUsageAnalytics, POPULATED_ENTRIES);

    const queriedTotal = POPULATED_ENTRIES.filter((entry) => entry.queryCount > 0).length;
    expect(queriedTotal).toBe(EXPECTED_TOP_TEN_CODES.length + EXPECTED_DROPPED_CODES.length);

    // Only the highest-ranked ten, in rank order.
    expect(breakdownRows(fixture)).toHaveLength(EXPECTED_TOP_TEN_CODES.length);
    expect(rowCodes(fixture)).toEqual([...EXPECTED_TOP_TEN_CODES]);
    for (const dropped of EXPECTED_DROPPED_CODES) {
      expect(rowCodes(fixture)).not.toContain(dropped);
    }

    // The panel names both figures, so the operator knows the list is a subset and how big the
    // whole set is — not just that something was hidden.
    const note = normalize(fixture.nativeElement.querySelector('.breakdown-note')?.textContent);
    expect(note).toContain(displayCount(EXPECTED_TOP_TEN_CODES.length));
    expect(note).toContain(displayCount(queriedTotal));
  });

  it('shows the breakdown empty state with the footnote intact when nothing has been queried (FR-013, US2 scenario 6)', async () => {
    const fixture = await renderWith(getUsageAnalytics, NOTHING_QUERIED_ENTRIES);

    expect(fixture.nativeElement.querySelector('[data-testid="breakdown-empty"]')).not.toBeNull();

    // No rows, and therefore no zero-length bars, rather than an empty list.
    expect(breakdownRows(fixture)).toHaveLength(0);
    expect(fixture.nativeElement.querySelectorAll('[data-testid="breakdown-bar"]')).toHaveLength(0);
    expect(fixture.nativeElement.querySelector('.breakdown-note')).toBeNull();

    // The footnote survives the empty state and still reports the real figure.
    const footnote = neverQueriedFootnote(fixture);
    expect(footnote).toContain(displayCount(NOTHING_QUERIED_ENTRIES.length));
    expect(footnote).toMatch(/never been queried/i);
  });

  it('renders only the eight newest recent entries in descending timestamp order (FR-010, FR-011)', async () => {
    const fixture = await renderWith(getUsageAnalytics, POPULATED_ENTRIES);

    expect(recentEntries(fixture)).toHaveLength(EXPECTED_RECENT_CODES.length);
    expect(recentCodes(fixture)).toEqual([...EXPECTED_RECENT_CODES]);
    for (const staleCode of EXPECTED_STALE_CODES) {
      expect(recentCodes(fixture)).not.toContain(staleCode);
    }
  });

  it('keeps each recent instant verbatim and exposes its absolute local date-time (FR-012a, FR-025)', async () => {
    const fixture = await renderWith(getUsageAnalytics, POPULATED_ENTRIES);
    const sourceByCode = new Map(
      POPULATED_ENTRIES.map((entry) => [entry.currencyCode, entry] as const),
    );
    const absoluteFormat = new Intl.DateTimeFormat(undefined, {
      dateStyle: 'medium',
      timeStyle: 'short',
    });

    recentEntries(fixture).forEach((entry) => {
      const code = entry.getAttribute('data-code') ?? '';
      const source = sourceByCode.get(code);
      const time = entry.querySelector('time');

      expect(source?.lastQueriedAt).not.toBeNull();
      expect(time, `expected ${code} to render a time element`).not.toBeNull();
      expect(time?.getAttribute('datetime')).toBe(source?.lastQueriedAt);
      expect(time?.getAttribute('title')).toBe(
        absoluteFormat.format(new Date(source?.lastQueriedAt as string)),
      );
      expect(normalize(time?.textContent)).not.toBe('');
    });
  });

  it('excludes null timestamps even when queried while retaining the breakdown row (FR-011)', async () => {
    const fixture = await renderWith(getUsageAnalytics, NULL_TIMESTAMP_ENTRIES);

    expect(recentCodes(fixture)).toEqual(['USD', 'EUR']);
    expect(recentCodes(fixture)).not.toContain('SEK');
    expect(recentCodes(fixture)).not.toContain('NOK');
    expect(rowCodes(fixture)).toContain('SEK');
  });

  it('shows an explicit recent-activity message instead of an empty list (FR-013)', async () => {
    const fixture = await renderWith(getUsageAnalytics, NOTHING_QUERIED_ENTRIES);
    const empty = fixture.nativeElement.querySelector('[data-testid="recent-empty"]');

    expect(recentEntries(fixture)).toHaveLength(0);
    expect(empty).not.toBeNull();
    expect(normalize(empty?.textContent)).toMatch(/no currency has been queried yet/i);
  });
});
