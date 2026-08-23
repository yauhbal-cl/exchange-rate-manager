import { TestBed } from '@angular/core/testing';
import { of } from 'rxjs';
import { HistoricalRates } from './historical-rates';
import { ExchangeRateAnalyticsService, type ExchangeRateTrendResponse } from '../../api-client';
import { resolveRange, todayIso } from './period-presets';

function selectCurrency(
  fixture: { nativeElement: HTMLElement; detectChanges(): void },
  name: string,
  code: string,
): void {
  const input: HTMLInputElement = fixture.nativeElement.querySelector(`input[name="${name}"]`)!;
  input.dispatchEvent(new Event('focus'));
  fixture.detectChanges();
  input.value = code;
  input.dispatchEvent(new Event('input'));
  fixture.detectChanges();
  const option: HTMLElement = fixture.nativeElement.querySelector(`[data-code="${code}"]`)!;
  option.dispatchEvent(new Event('mousedown'));
  fixture.detectChanges();
}

async function flush(fixture: {
  whenStable(): Promise<unknown>;
  detectChanges(): void;
}): Promise<void> {
  await fixture.whenStable();
  fixture.detectChanges();
}

function trendResponse(
  overrides: Partial<ExchangeRateTrendResponse> = {},
): ExchangeRateTrendResponse {
  return {
    fromCurrency: 'USD',
    toCurrency: 'EUR',
    points: [
      { rateDate: '2026-07-24', rate: '0.9000000000' },
      { rateDate: '2026-08-23', rate: '0.9500000000' },
    ],
    ...overrides,
  };
}

describe('HistoricalRates', () => {
  let getExchangeRateTrend: ReturnType<typeof vi.fn>;

  beforeEach(() => {
    getExchangeRateTrend = vi.fn();
    TestBed.configureTestingModule({
      providers: [{ provide: ExchangeRateAnalyticsService, useValue: { getExchangeRateTrend } }],
    });
  });

  it('renders the summary metrics row before the chart for the default USD/EUR pair and 1M preset (FR-010)', async () => {
    getExchangeRateTrend.mockReturnValue(of(trendResponse()));

    const fixture = TestBed.createComponent(HistoricalRates);
    fixture.detectChanges();
    await flush(fixture);

    const range = resolveRange({ kind: 'preset', id: '1M' }, todayIso());
    expect(getExchangeRateTrend).toHaveBeenCalledTimes(1);
    expect(getExchangeRateTrend).toHaveBeenCalledWith(
      'USD',
      'EUR',
      range.startDate,
      range.endDate,
    );

    const html: string = fixture.nativeElement.innerHTML;
    const metricsIndex = html.indexOf('Latest rate');
    const chartIndex = html.indexOf('app-rate-trend-chart');
    expect(metricsIndex).toBeGreaterThan(-1);
    expect(chartIndex).toBeGreaterThan(-1);
    expect(metricsIndex).toBeLessThan(chartIndex);
    expect(fixture.nativeElement.textContent).toContain('0.9500000000');
  });

  it('fires exactly one new request and updates metrics/chart together when the pair changes (Acceptance Scenario 3, SC-001)', async () => {
    getExchangeRateTrend
      .mockReturnValueOnce(of(trendResponse()))
      .mockReturnValueOnce(
        of(
          trendResponse({
            fromCurrency: 'GBP',
            points: [{ rateDate: '2026-08-23', rate: '1.2000000000' }],
          }),
        ),
      );

    const fixture = TestBed.createComponent(HistoricalRates);
    fixture.detectChanges();
    await flush(fixture);

    selectCurrency(fixture, 'base-currency', 'GBP');
    await flush(fixture);

    expect(getExchangeRateTrend).toHaveBeenCalledTimes(2);
    expect(getExchangeRateTrend).toHaveBeenLastCalledWith(
      'GBP',
      'EUR',
      expect.any(String),
      expect.any(String),
    );
    expect(fixture.nativeElement.textContent).toContain('1.2000000000');
  });

  it('shows the explicit no-data state for both metrics and chart when points is empty (FR-015, Acceptance Scenario 2)', async () => {
    getExchangeRateTrend.mockReturnValue(of(trendResponse({ points: [] })));

    const fixture = TestBed.createComponent(HistoricalRates);
    fixture.detectChanges();
    await flush(fixture);

    expect(fixture.nativeElement.querySelector('[data-testid="metrics-no-data"]')).not.toBeNull();
    expect(fixture.nativeElement.querySelector('[data-testid="chart-no-data"]')).not.toBeNull();
  });

  it('shows a validation message and fires no request when base and quote currencies are identical (FR-002)', async () => {
    getExchangeRateTrend.mockReturnValue(of(trendResponse()));

    const fixture = TestBed.createComponent(HistoricalRates);
    fixture.detectChanges();
    await flush(fixture);

    getExchangeRateTrend.mockClear();
    selectCurrency(fixture, 'quote-currency', 'USD');
    await flush(fixture);

    expect(getExchangeRateTrend).not.toHaveBeenCalled();
    expect(fixture.nativeElement.textContent).toContain(
      'Base and quote currency must be different.',
    );
  });
});
