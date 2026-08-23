import { TestBed } from '@angular/core/testing';
import { HttpErrorResponse } from '@angular/common/http';
import { Subject, of, throwError } from 'rxjs';
import { UsageAnalytics } from './usage-analytics';
import { ExchangeRateUsageAnalyticsService, type UsageAnalyticsResponse } from '../../api-client';

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
});
