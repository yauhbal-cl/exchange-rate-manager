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
});
