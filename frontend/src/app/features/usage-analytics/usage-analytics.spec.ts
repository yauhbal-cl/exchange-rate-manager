import { HttpErrorResponse } from '@angular/common/http';
import { TestBed } from '@angular/core/testing';
import { Subject, of, throwError } from 'rxjs';
import type { CurrencyUsageEntry, UsageAnalyticsResponse } from '../../api-client';
import { ExchangeRateUsageAnalyticsService } from '../../api-client';
import { UsageAnalytics } from './usage-analytics';

const NOW_MS = Date.now();
const daysAgo = (days: number) => new Date(NOW_MS - days * 24 * 60 * 60 * 1_000).toISOString();

const ENTRIES: CurrencyUsageEntry[] = [
  {
    currencyCode: 'USD',
    queryCount: 120,
    lastQueriedAt: daysAgo(1),
    queryTimestamps: [daysAgo(80), daysAgo(20), daysAgo(5), daysAgo(1)],
  },
  {
    currencyCode: 'EUR',
    queryCount: 45,
    lastQueriedAt: daysAgo(10),
    queryTimestamps: [daysAgo(60), daysAgo(10)],
  },
  { currencyCode: 'GBP', queryCount: 0, lastQueriedAt: null, queryTimestamps: [] },
];

function response(currencies = ENTRIES): UsageAnalyticsResponse {
  return { currencies };
}

async function render(getUsageAnalytics: ReturnType<typeof vi.fn>) {
  const fixture = TestBed.createComponent(UsageAnalytics);
  fixture.detectChanges();
  await fixture.whenStable();
  fixture.detectChanges();
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

  it('loads the complete default-window payload with one request', async () => {
    getUsageAnalytics.mockReturnValue(of(response()));
    const fixture = await render(getUsageAnalytics);
    const select = fixture.nativeElement.querySelector(
      '[data-testid="window-select"]',
    ) as HTMLSelectElement;

    expect(getUsageAnalytics).toHaveBeenCalledOnce();
    expect(getUsageAnalytics).toHaveBeenCalledWith();
    expect(select.value).toBe('90');
  });

  it('renders one table with the requested columns and one row per currency', async () => {
    getUsageAnalytics.mockReturnValue(of(response()));
    const fixture = await render(getUsageAnalytics);
    const page = fixture.nativeElement as HTMLElement;

    expect(page.querySelectorAll('table')).toHaveLength(1);
    expect(Array.from(page.querySelectorAll('th')).map((cell) => cell.textContent?.trim())).toEqual(
      ['Currency', 'Total queries', 'In window', 'Last queried', 'Activity', 'Details'],
    );
    expect(page.querySelectorAll('[data-testid="usage-row"]')).toHaveLength(3);
    expect(page.querySelector('[data-code="GBP"]')?.textContent).toContain('Never');
  });

  it('renders a twelve-point activity chart per currency and exposes it as accessible text', async () => {
    getUsageAnalytics.mockReturnValue(of(response()));
    const fixture = await render(getUsageAnalytics);
    const usd = fixture.nativeElement.querySelector('[data-code="USD"]') as HTMLElement;

    expect(usd.querySelectorAll('[data-testid="activity-point"]')).toHaveLength(12);
    expect(usd.querySelector('[data-testid="activity-chart"]')?.getAttribute('aria-label')).toBe(
      '4 queries for USD over the last 90 days',
    );
  });

  it('opens and closes a detailed activity chart from a row action', async () => {
    getUsageAnalytics.mockReturnValue(of(response()));
    const fixture = await render(getUsageAnalytics);
    const usd = fixture.nativeElement.querySelector('[data-code="USD"]') as HTMLElement;

    (usd.querySelector('[data-testid="activity-details-button"]') as HTMLButtonElement).click();
    fixture.detectChanges();

    const dialog = fixture.nativeElement.querySelector(
      '[data-testid="activity-details-dialog"]',
    ) as HTMLElement;
    expect(dialog).not.toBeNull();
    expect(dialog.textContent).toContain('USD activity');
    expect(dialog.querySelector('[data-testid="detailed-activity-chart"]')).not.toBeNull();
    expect(dialog.querySelectorAll('[data-testid="detailed-activity-point"]')).toHaveLength(12);
    expect(
      dialog.querySelector('[data-testid="detailed-chart-y-axis-label"]')?.textContent?.trim(),
    ).toBe('Queries');
    const statistics = dialog.querySelector('[data-testid="activity-details-statistics"]');
    expect(statistics?.textContent).toContain('Peak query day');
    expect(statistics?.textContent).toContain('1 query');
    expect(statistics?.textContent).toContain('Average queries per day');
    expect(statistics?.textContent).toContain(
      new Intl.NumberFormat(undefined, { maximumFractionDigits: 2 }).format(4 / 90),
    );

    (dialog.querySelector('[data-testid="activity-details-close"]') as HTMLButtonElement).click();
    fixture.detectChanges();
    expect(
      fixture.nativeElement.querySelector('[data-testid="activity-details-dialog"]'),
    ).toBeNull();
  });

  it('shows total queries and the most popular currency for the selected window', async () => {
    getUsageAnalytics.mockReturnValue(of(response()));
    const fixture = await render(getUsageAnalytics);
    const page = fixture.nativeElement as HTMLElement;

    expect(page.querySelector('[data-testid="window-total-queries"]')?.textContent).toContain('6');
    expect(page.querySelector('[data-testid="window-most-popular"]')?.textContent).toContain('USD');
    expect(page.querySelector('[data-testid="window-most-popular"]')?.textContent).toContain(
      '4 queries',
    );
  });

  it('changes the displayed count and buckets without making another API request', async () => {
    getUsageAnalytics.mockReturnValue(of(response()));
    const fixture = await render(getUsageAnalytics);
    const select = fixture.nativeElement.querySelector(
      '[data-testid="window-select"]',
    ) as HTMLSelectElement;

    select.value = '7';
    select.dispatchEvent(new Event('change'));
    fixture.detectChanges();

    const usd = fixture.nativeElement.querySelector('[data-code="USD"]') as HTMLElement;
    expect(usd.textContent).toContain('2');
    expect(usd.querySelector('[data-testid="activity-chart"]')?.getAttribute('aria-label')).toBe(
      '2 queries for USD over the last 7 days',
    );
    expect(
      fixture.nativeElement.querySelector('[data-testid="window-total-queries"]')?.textContent,
    ).toContain('2');
    expect(
      fixture.nativeElement.querySelector('[data-testid="window-most-popular"]')?.textContent,
    ).toContain('USD');
    expect(getUsageAnalytics).toHaveBeenCalledOnce();
  });

  it('shows the loading state while the request is pending', () => {
    getUsageAnalytics.mockReturnValue(new Subject<UsageAnalyticsResponse>());
    const fixture = TestBed.createComponent(UsageAnalytics);
    fixture.detectChanges();

    expect(fixture.nativeElement.querySelector('[data-testid="usage-loading"]')).not.toBeNull();
    expect(fixture.nativeElement.querySelector('table')).toBeNull();
  });

  it('shows the error state when the request fails', async () => {
    getUsageAnalytics.mockReturnValue(throwError(() => new HttpErrorResponse({ status: 503 })));
    const fixture = await render(getUsageAnalytics);

    expect(fixture.nativeElement.querySelector('[data-testid="usage-error"]')).not.toBeNull();
    expect(fixture.nativeElement.querySelector('table')).toBeNull();
  });
});
