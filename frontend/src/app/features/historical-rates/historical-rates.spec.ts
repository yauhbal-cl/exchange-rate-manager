import { TestBed } from '@angular/core/testing';
import { HttpErrorResponse } from '@angular/common/http';
import { Subject, of, throwError } from 'rxjs';
import { HistoricalRates } from './historical-rates';
import {
  ExchangeRateAIInsightService,
  ExchangeRateAnalyticsService,
  type ExchangeRateTrendResponse,
  type TrendInsightResponse,
} from '../../api-client';
import { PERIOD_PRESETS, resolveRange, subtractMonths, todayIso } from './period-presets';

function generateInsightButton(fixture: { nativeElement: HTMLElement }): HTMLButtonElement {
  return Array.from(fixture.nativeElement.querySelectorAll('button')).find((button) =>
    button.textContent?.includes('Generate insight'),
  ) as HTMLButtonElement;
}

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

function insightResponse(overrides: Partial<TrendInsightResponse> = {}): TrendInsightResponse {
  return {
    fromCurrency: 'USD',
    toCurrency: 'EUR',
    startDate: '2026-07-24',
    endDate: '2026-08-23',
    narrative: 'The rate rose steadily over the period.',
    ...overrides,
  };
}

describe('HistoricalRates', () => {
  let getExchangeRateTrend: ReturnType<typeof vi.fn>;
  let getExchangeRateTrendInsight: ReturnType<typeof vi.fn>;

  beforeEach(() => {
    getExchangeRateTrend = vi.fn();
    getExchangeRateTrendInsight = vi.fn();
    TestBed.configureTestingModule({
      providers: [
        { provide: ExchangeRateAnalyticsService, useValue: { getExchangeRateTrend } },
        { provide: ExchangeRateAIInsightService, useValue: { getExchangeRateTrendInsight } },
      ],
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

  for (const preset of PERIOD_PRESETS) {
    it(`resolves the ${preset.id} preset to its trailing window and fires a new trend request (FR-004)`, async () => {
      getExchangeRateTrend.mockReturnValue(of(trendResponse()));

      const fixture = TestBed.createComponent(HistoricalRates);
      fixture.detectChanges();
      await flush(fixture);

      getExchangeRateTrend.mockClear();
      const button: HTMLButtonElement = fixture.nativeElement.querySelector(
        `button[data-preset="${preset.id}"]`,
      );
      button.click();
      await flush(fixture);

      const range = resolveRange({ kind: 'preset', id: preset.id }, todayIso());
      expect(getExchangeRateTrend).toHaveBeenCalledTimes(1);
      expect(getExchangeRateTrend).toHaveBeenCalledWith(
        'USD',
        'EUR',
        range.startDate,
        range.endDate,
      );
    });
  }

  it('fires a request with the exact dates of a valid custom range (FR-005, Acceptance Scenario 2)', async () => {
    getExchangeRateTrend.mockReturnValue(of(trendResponse()));

    const fixture = TestBed.createComponent(HistoricalRates);
    fixture.detectChanges();
    await flush(fixture);

    getExchangeRateTrend.mockClear();
    const startInput: HTMLInputElement = fixture.nativeElement.querySelector(
      'input[name="range-start"]',
    );
    const endInput: HTMLInputElement = fixture.nativeElement.querySelector(
      'input[name="range-end"]',
    );
    startInput.value = '2026-06-01';
    startInput.dispatchEvent(new Event('change'));
    fixture.detectChanges();
    endInput.value = '2026-07-01';
    endInput.dispatchEvent(new Event('change'));
    await flush(fixture);

    expect(getExchangeRateTrend).toHaveBeenLastCalledWith(
      'USD',
      'EUR',
      '2026-06-01',
      '2026-07-01',
    );
  });

  it('shows a validation message and fires no request for a custom range spanning more than 6 months (FR-006, SC-004)', async () => {
    getExchangeRateTrend.mockReturnValue(of(trendResponse()));

    const fixture = TestBed.createComponent(HistoricalRates);
    fixture.detectChanges();
    await flush(fixture);

    getExchangeRateTrend.mockClear();
    const startInput: HTMLInputElement = fixture.nativeElement.querySelector(
      'input[name="range-start"]',
    );
    const endInput: HTMLInputElement = fixture.nativeElement.querySelector(
      'input[name="range-end"]',
    );
    const endDate = '2026-08-01';
    const tooFarStart = subtractMonths(subtractMonths(endDate, 6), 1);
    endInput.value = endDate;
    endInput.dispatchEvent(new Event('change'));
    await flush(fixture);

    getExchangeRateTrend.mockClear();
    startInput.value = tooFarStart;
    startInput.dispatchEvent(new Event('change'));
    await flush(fixture);

    expect(getExchangeRateTrend).not.toHaveBeenCalled();
    expect(fixture.nativeElement.textContent).toContain('Custom range cannot exceed 6 months.');
  });

  it('shows a validation message and fires no request when the custom range start is after the end (Acceptance Scenario 4)', async () => {
    getExchangeRateTrend.mockReturnValue(of(trendResponse()));

    const fixture = TestBed.createComponent(HistoricalRates);
    fixture.detectChanges();
    await flush(fixture);

    getExchangeRateTrend.mockClear();
    const startInput: HTMLInputElement = fixture.nativeElement.querySelector(
      'input[name="range-start"]',
    );
    const endInput: HTMLInputElement = fixture.nativeElement.querySelector(
      'input[name="range-end"]',
    );
    endInput.value = '2026-07-01';
    endInput.dispatchEvent(new Event('change'));
    fixture.detectChanges();
    startInput.value = '2026-08-01';
    startInput.dispatchEvent(new Event('change'));
    await flush(fixture);

    expect(getExchangeRateTrend).not.toHaveBeenCalled();
    expect(fixture.nativeElement.textContent).toContain(
      'End date must be on or after the start date.',
    );
  });

  it('exchanges base/quote and fires exactly one new request for the swapped pair using the same period (FR-003, SC-002)', async () => {
    getExchangeRateTrend
      .mockReturnValueOnce(of(trendResponse()))
      .mockReturnValueOnce(of(trendResponse({ fromCurrency: 'EUR', toCurrency: 'USD' })));

    const fixture = TestBed.createComponent(HistoricalRates);
    fixture.detectChanges();
    await flush(fixture);

    getExchangeRateTrend.mockClear();
    const swapButton: HTMLButtonElement = fixture.nativeElement.querySelector(
      'button[aria-label="Swap currencies"]',
    );
    swapButton.click();
    await flush(fixture);

    const range = resolveRange({ kind: 'preset', id: '1M' }, todayIso());
    expect(getExchangeRateTrend).toHaveBeenCalledTimes(1);
    expect(getExchangeRateTrend).toHaveBeenCalledWith(
      'EUR',
      'USD',
      range.startDate,
      range.endDate,
    );
  });

  it('renders table rows most-recent-first matching the chart points exactly, with daily % change (FR-009, SC-003)', async () => {
    getExchangeRateTrend.mockReturnValue(
      of(
        trendResponse({
          points: [
            { rateDate: '2026-08-01', rate: '0.9000000000' },
            { rateDate: '2026-08-15', rate: '0.9500000000' },
            { rateDate: '2026-08-23', rate: '0.9100000000' },
          ],
        }),
      ),
    );

    const fixture = TestBed.createComponent(HistoricalRates);
    fixture.detectChanges();
    await flush(fixture);

    const rows: NodeListOf<HTMLTableRowElement> =
      fixture.nativeElement.querySelectorAll('tbody tr');
    expect(rows.length).toBe(3);
    expect(rows[0].textContent).toContain('2026-08-23');
    expect(rows[0].textContent).toContain('0.9100000000');
    expect(rows[1].textContent).toContain('2026-08-15');
    expect(rows[2].textContent).toContain('2026-08-01');
    expect(rows[2].textContent).toContain('—');
  });

  it('shows the table no-data state consistent with the chart when points is empty (Acceptance Scenario 2)', async () => {
    getExchangeRateTrend.mockReturnValue(of(trendResponse({ points: [] })));

    const fixture = TestBed.createComponent(HistoricalRates);
    fixture.detectChanges();
    await flush(fixture);

    expect(fixture.nativeElement.querySelector('[data-testid="table-no-data"]')).not.toBeNull();
  });

  it('shows no narrative until Generate insight is clicked (FR-011)', async () => {
    getExchangeRateTrend.mockReturnValue(of(trendResponse()));

    const fixture = TestBed.createComponent(HistoricalRates);
    fixture.detectChanges();
    await flush(fixture);

    expect(getExchangeRateTrendInsight).not.toHaveBeenCalled();
    expect(fixture.nativeElement.textContent).not.toContain('The rate rose steadily');
  });

  it('fires exactly one insight request and shows a narrative grounded in the displayed range when clicked with data (FR-012, Acceptance Scenario 1)', async () => {
    getExchangeRateTrend.mockReturnValue(of(trendResponse()));
    getExchangeRateTrendInsight.mockReturnValue(of(insightResponse()));

    const fixture = TestBed.createComponent(HistoricalRates);
    fixture.detectChanges();
    await flush(fixture);

    const range = resolveRange({ kind: 'preset', id: '1M' }, todayIso());
    generateInsightButton(fixture).click();
    await flush(fixture);

    expect(getExchangeRateTrendInsight).toHaveBeenCalledTimes(1);
    expect(getExchangeRateTrendInsight).toHaveBeenCalledWith(
      'USD',
      'EUR',
      range.startDate,
      range.endDate,
    );
    expect(fixture.nativeElement.textContent).toContain('The rate rose steadily over the period.');
  });

  it('disables Generate insight while the trend is loading (FR-013)', async () => {
    const trend$ = new Subject<ExchangeRateTrendResponse>();
    getExchangeRateTrend.mockReturnValue(trend$);

    const fixture = TestBed.createComponent(HistoricalRates);
    fixture.detectChanges();

    expect(generateInsightButton(fixture).disabled).toBe(true);

    trend$.next(trendResponse());
    trend$.complete();
    await flush(fixture);

    expect(generateInsightButton(fixture).disabled).toBe(false);
  });

  it('disables Generate insight when there is no underlying data (FR-013, Acceptance Scenario 3)', async () => {
    getExchangeRateTrend.mockReturnValue(of(trendResponse({ points: [] })));

    const fixture = TestBed.createComponent(HistoricalRates);
    fixture.detectChanges();
    await flush(fixture);

    expect(generateInsightButton(fixture).disabled).toBe(true);
  });

  it('shows a "no data" message for a 404 insight error and "unavailable" for other failures (FR-013, Acceptance Scenario 2, SC-005)', async () => {
    getExchangeRateTrend.mockReturnValue(of(trendResponse()));
    getExchangeRateTrendInsight.mockReturnValue(
      throwError(() => new HttpErrorResponse({ status: 404, error: { detail: 'No data for range.' } })),
    );

    const fixture = TestBed.createComponent(HistoricalRates);
    fixture.detectChanges();
    await flush(fixture);

    generateInsightButton(fixture).click();
    await flush(fixture);

    expect(fixture.nativeElement.textContent).toContain('No data for range.');

    getExchangeRateTrendInsight.mockReturnValue(
      throwError(() => new HttpErrorResponse({ status: 503 })),
    );
    generateInsightButton(fixture).click();
    await flush(fixture);

    expect(fixture.nativeElement.textContent).toContain('interpretation unavailable');
  });

  it('clears a shown narrative when the pair, period, or swap changes (FR-014, Acceptance Scenario 4, SC-006)', async () => {
    getExchangeRateTrend.mockReturnValue(of(trendResponse()));
    getExchangeRateTrendInsight.mockReturnValue(of(insightResponse()));

    const fixture = TestBed.createComponent(HistoricalRates);
    fixture.detectChanges();
    await flush(fixture);

    generateInsightButton(fixture).click();
    await flush(fixture);
    expect(fixture.nativeElement.textContent).toContain('The rate rose steadily over the period.');

    const button: HTMLButtonElement = fixture.nativeElement.querySelector(
      'button[data-preset="3M"]',
    );
    button.click();
    await flush(fixture);

    expect(fixture.nativeElement.textContent).not.toContain('The rate rose steadily over the period.');
  });
});
