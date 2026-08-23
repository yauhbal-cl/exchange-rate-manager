import { HttpErrorResponse } from '@angular/common/http';
import { Component, computed, inject, signal } from '@angular/core';
import { rxResource } from '@angular/core/rxjs-interop';
import {
  ExchangeRateAIInsightService,
  ExchangeRateAnalyticsService,
  type RateTrendPoint,
} from '../../api-client';
import { formatIsoDateUtc } from '../../shared/date-utils';
import { problemDetail } from '../../shared/problem-detail';
import { CurrencyCombobox } from '../rate-lookup/currency-combobox';
import type { Currency } from '../rate-lookup/currencies';
import { AiInsightsPanel } from './ai-insights-panel';
import { HistoricalRatesTable } from './historical-rates-table';
import {
  customRangeError,
  PERIOD_PRESETS,
  resolveRange,
  todayIso,
  type PeriodSelection,
  type PresetId,
} from './period-presets';
import { RateTrendChart } from './rate-trend-chart';
import {
  computeDailyChanges,
  computeLatest,
  computePeriodChange,
  computePeriodHigh,
  computePeriodLow,
} from './trend-metrics';

interface TrendRequest {
  from: string;
  to: string;
  startDate: string;
  endDate: string;
}
interface HistoricalError {
  category: 'invalid' | 'no-data' | 'unreachable';
  message: string;
}
type HistoricalRequestState =
  | { kind: 'invalid' }
  | { kind: 'loading' }
  | { kind: 'error'; error: HistoricalError }
  | { kind: 'empty' }
  | { kind: 'populated'; points: readonly RateTrendPoint[] };

export function categorizeHistoricalError(error: unknown): HistoricalError {
  if (error instanceof HttpErrorResponse) {
    const detail = problemDetail(error.error);
    if (error.status === 400)
      return { category: 'invalid', message: detail ?? 'The historical-rate request is invalid.' };
    if (error.status === 404)
      return {
        category: 'no-data',
        message: detail ?? 'No historical rate data was found for this pair and period.',
      };
  }
  return {
    category: 'unreachable',
    message: 'Unable to reach the historical-rate service. Please try again later.',
  };
}

@Component({
  selector: 'app-historical-rates',
  imports: [CurrencyCombobox, RateTrendChart, HistoricalRatesTable, AiInsightsPanel],
  styleUrl: './historical-rates.css',
  templateUrl: './historical-rates.html',
})
export class HistoricalRates {
  private readonly analyticsService = inject(ExchangeRateAnalyticsService);
  private readonly insightService = inject(ExchangeRateAIInsightService);
  protected readonly today = todayIso();
  protected readonly formattedToday = formatIsoDateUtc(this.today);
  protected readonly baseCurrency = signal<Currency['code']>('USD');
  protected readonly quoteCurrency = signal<Currency['code']>('EUR');
  protected readonly presets = PERIOD_PRESETS;
  protected readonly period = signal<PeriodSelection>({ kind: 'preset', id: '7D' });

  protected readonly pairError = computed<string | null>(() =>
    this.baseCurrency() === this.quoteCurrency()
      ? 'Base and quote currency must be different.'
      : null,
  );
  protected readonly resolvedRange = computed(() => resolveRange(this.period(), this.today));
  protected readonly periodError = computed<string | null>(() => {
    const period = this.period();
    return period.kind === 'custom' ? customRangeError(period.startDate, period.endDate) : null;
  });
  protected readonly pairAndRange = computed<TrendRequest | undefined>(() => {
    if (this.pairError() !== null || this.periodError() !== null) return undefined;
    const { startDate, endDate } = this.resolvedRange();
    return { from: this.baseCurrency(), to: this.quoteCurrency(), startDate, endDate };
  });

  protected readonly trend = rxResource({
    params: () => this.pairAndRange(),
    stream: ({ params }) =>
      this.analyticsService.getExchangeRateTrend(
        params.from,
        params.to,
        params.startDate,
        params.endDate,
      ),
  });

  protected readonly requestState = computed<HistoricalRequestState>(() => {
    if (!this.pairAndRange()) return { kind: 'invalid' };
    if (this.trend.isLoading()) return { kind: 'loading' };
    const error = this.trend.error();
    if (error !== undefined) return { kind: 'error', error: categorizeHistoricalError(error) };
    const points = this.trend.value()?.points;
    if (!points || points.length === 0) return { kind: 'empty' };
    return { kind: 'populated', points };
  });

  protected readonly points = computed<readonly RateTrendPoint[]>(() => {
    const state = this.requestState();
    return state.kind === 'populated' ? state.points : [];
  });
  protected readonly latest = computed(() => computeLatest(this.points()));
  protected readonly periodChange = computed(() => computePeriodChange(this.points()));
  protected readonly periodHigh = computed(() => computePeriodHigh(this.points()));
  protected readonly periodLow = computed(() => computePeriodLow(this.points()));
  protected readonly dailyChanges = computed(() => computeDailyChanges(this.points()));
  protected readonly formattedRange = computed(() => {
    const range = this.resolvedRange();
    return `${formatIsoDateUtc(range.startDate)} – ${formatIsoDateUtc(range.endDate)}`;
  });
  protected readonly formattedLatestDate = computed(() => {
    const point = this.points().at(-1);
    return formatIsoDateUtc(point?.rateDate);
  });
  protected readonly formattedHighDate = computed(() => formatIsoDateUtc(this.periodHigh()?.date));
  protected readonly formattedLowDate = computed(() => formatIsoDateUtc(this.periodLow()?.date));
  protected readonly chartAccessibleLabel = computed(
    () =>
      `Exchange-rate trend for ${this.baseCurrency()} to ${this.quoteCurrency()} from ${this.resolvedRange().startDate} to ${this.resolvedRange().endDate}. The historical rates table provides the same data as text.`,
  );

  protected readonly aiInsight = rxResource({
    params: () => this.pairAndRange(),
    stream: ({ params }) =>
      this.insightService.getExchangeRateTrendInsight(
        params.from,
        params.to,
        params.startDate,
        params.endDate,
      ),
  });
  protected readonly aiInsightValue = computed(() =>
    this.aiInsight.hasValue() ? this.aiInsight.value() : undefined,
  );

  protected swap(): void {
    const base = this.baseCurrency();
    this.baseCurrency.set(this.quoteCurrency());
    this.quoteCurrency.set(base);
  }
  protected selectPreset(id: PresetId): void {
    this.period.set({ kind: 'preset', id });
  }
  protected isActivePreset(id: PresetId): boolean {
    const period = this.period();
    return period.kind === 'preset' && period.id === id;
  }
  protected setRangeStart(event: Event): void {
    const startDate = this.inputValue(event);
    if (startDate !== null)
      this.period.set({ kind: 'custom', startDate, endDate: this.resolvedRange().endDate });
  }
  protected setRangeEnd(event: Event): void {
    const endDate = this.inputValue(event);
    if (endDate !== null)
      this.period.set({ kind: 'custom', startDate: this.resolvedRange().startDate, endDate });
  }
  private inputValue(event: Event): string | null {
    return event.target instanceof HTMLInputElement ? event.target.value : null;
  }
}
