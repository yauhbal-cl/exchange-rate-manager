import { Component, computed, effect, inject, signal } from '@angular/core';
import { rxResource } from '@angular/core/rxjs-interop';
import { ExchangeRateAIInsightService, ExchangeRateAnalyticsService } from '../../api-client';
import { CurrencyCombobox } from '../rate-lookup/currency-combobox';
import type { Currency } from '../rate-lookup/currencies';
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

@Component({
  selector: 'app-historical-rates',
  imports: [CurrencyCombobox, RateTrendChart, HistoricalRatesTable],
  template: `
    <div class="mx-auto max-w-6xl px-4 py-8">
      <h2 class="text-2xl font-semibold text-gray-900">Historical Exchange Rate Trends</h2>
      <p class="mt-1 text-gray-600">
        Explore how a currency pair has moved over a chosen period, with summary metrics, a
        chart, and the raw historical rates.
      </p>

      <div class="mt-4 flex flex-wrap items-end gap-4">
        <app-currency-combobox
          id="base-currency"
          name="base-currency"
          label="Base"
          [(value)]="baseCurrency"
        />
        <button
          type="button"
          aria-label="Swap currencies"
          class="rounded border border-gray-300 px-3 py-2 text-gray-700 hover:bg-gray-50"
          (click)="swap()"
        >
          ⇄
        </button>
        <app-currency-combobox
          id="quote-currency"
          name="quote-currency"
          label="Quote"
          [(value)]="quoteCurrency"
        />
      </div>

      @if (pairError()) {
        <p class="mt-2 text-amber-700">{{ pairError() }}</p>
      }

      <div class="mt-4 flex flex-wrap gap-2">
        @for (preset of presets; track preset.id) {
          <button
            type="button"
            [attr.data-preset]="preset.id"
            class="rounded border px-3 py-1.5 text-sm font-medium"
            [class.border-blue-600]="isActivePreset(preset.id)"
            [class.bg-blue-600]="isActivePreset(preset.id)"
            [class.text-white]="isActivePreset(preset.id)"
            [class.border-gray-300]="!isActivePreset(preset.id)"
            [class.text-gray-700]="!isActivePreset(preset.id)"
            (click)="selectPreset(preset.id)"
          >
            {{ preset.label }}
          </button>
        }
      </div>

      <div class="mt-3 flex flex-wrap items-end gap-4">
        <label class="flex flex-col gap-1">
          <span class="text-sm font-medium text-gray-700">Start</span>
          <input
            type="date"
            name="range-start"
            class="rounded border border-gray-300 px-3 py-2"
            [value]="resolvedRange().startDate"
            (change)="setRangeStart($any($event.target).value)"
          />
        </label>
        <label class="flex flex-col gap-1">
          <span class="text-sm font-medium text-gray-700">End</span>
          <input
            type="date"
            name="range-end"
            class="rounded border border-gray-300 px-3 py-2"
            [value]="resolvedRange().endDate"
            (change)="setRangeEnd($any($event.target).value)"
          />
        </label>
      </div>

      @if (periodError()) {
        <p class="mt-2 text-amber-700">{{ periodError() }}</p>
      }

      @if (points().length > 0) {
        <div class="mt-6 grid grid-cols-2 gap-4 sm:grid-cols-4">
          <div>
            <p class="text-sm text-gray-500">Latest rate</p>
            <p class="text-xl font-semibold text-gray-900">{{ latest()?.display }}</p>
          </div>
          <div>
            <p class="text-sm text-gray-500">Period change</p>
            @if (periodChange(); as change) {
              <p
                class="text-xl font-semibold"
                [class.text-green-700]="!change.value.isNegative()"
                [class.text-red-700]="change.value.isNegative()"
              >
                {{ change.percent }}
              </p>
            } @else {
              <p class="text-xl font-semibold text-gray-400">—</p>
            }
          </div>
          <div>
            <p class="text-sm text-gray-500">Period high</p>
            <p class="text-xl font-semibold text-gray-900">{{ periodHigh()?.display }}</p>
            <p class="text-xs text-gray-500">{{ periodHigh()?.date }}</p>
          </div>
          <div>
            <p class="text-sm text-gray-500">Period low</p>
            <p class="text-xl font-semibold text-gray-900">{{ periodLow()?.display }}</p>
            <p class="text-xs text-gray-500">{{ periodLow()?.date }}</p>
          </div>
        </div>
      } @else {
        <p class="mt-6 text-gray-500" data-testid="metrics-no-data">
          No historical rate data for this pair and period.
        </p>
      }

      <div class="mt-6">
        <app-rate-trend-chart
          [points]="points()"
          [dailyChanges]="dailyChanges()"
          [periodHigh]="periodHigh()"
          [periodLow]="periodLow()"
        />
      </div>

      <div class="mt-6">
        <app-historical-rates-table [points]="points()" [dailyChanges]="dailyChanges()" />
      </div>
    </div>
  `,
})
export class HistoricalRates {
  private readonly exchangeRateAnalyticsService = inject(ExchangeRateAnalyticsService);
  private readonly exchangeRateAIInsightService = inject(ExchangeRateAIInsightService);

  protected readonly today = todayIso();

  protected readonly baseCurrency = signal<Currency['code']>('USD');
  protected readonly quoteCurrency = signal<Currency['code']>('EUR');

  protected readonly pairError = computed<string | null>(() =>
    this.baseCurrency() === this.quoteCurrency()
      ? 'Base and quote currency must be different.'
      : null,
  );

  protected swap(): void {
    const base = this.baseCurrency();
    const quote = this.quoteCurrency();
    this.baseCurrency.set(quote);
    this.quoteCurrency.set(base);
  }

  protected readonly presets = PERIOD_PRESETS;
  protected readonly period = signal<PeriodSelection>({ kind: 'preset', id: '1M' });

  protected selectPreset(id: PresetId): void {
    this.period.set({ kind: 'preset', id });
  }

  protected isActivePreset(id: PresetId): boolean {
    const period = this.period();
    return period.kind === 'preset' && period.id === id;
  }

  protected readonly resolvedRange = computed(() => resolveRange(this.period(), this.today));

  protected setRangeStart(startDate: string): void {
    this.period.set({ kind: 'custom', startDate, endDate: this.resolvedRange().endDate });
  }

  protected setRangeEnd(endDate: string): void {
    this.period.set({ kind: 'custom', startDate: this.resolvedRange().startDate, endDate });
  }

  protected readonly periodError = computed<string | null>(() => {
    const period = this.period();
    return period.kind === 'custom' ? customRangeError(period.startDate, period.endDate) : null;
  });

  protected readonly pairAndRange = computed<TrendRequest | undefined>(() => {
    if (this.pairError() !== null || this.periodError() !== null) {
      return undefined;
    }
    const { startDate, endDate } = resolveRange(this.period(), this.today);
    return { from: this.baseCurrency(), to: this.quoteCurrency(), startDate, endDate };
  });

  protected readonly trend = rxResource({
    params: () => this.pairAndRange(),
    stream: ({ params }) =>
      this.exchangeRateAnalyticsService.getExchangeRateTrend(
        params.from,
        params.to,
        params.startDate,
        params.endDate,
      ),
  });

  protected readonly points = computed(() => this.trend.value()?.points ?? []);
  protected readonly latest = computed(() => computeLatest(this.points()));
  protected readonly periodChange = computed(() => computePeriodChange(this.points()));
  protected readonly periodHigh = computed(() => computePeriodHigh(this.points()));
  protected readonly periodLow = computed(() => computePeriodLow(this.points()));
  protected readonly dailyChanges = computed(() => computeDailyChanges(this.points()));

  protected readonly aiRequest = signal<TrendRequest | undefined>(undefined);

  protected readonly aiInsight = rxResource({
    params: () => this.aiRequest(),
    stream: ({ params }) =>
      this.exchangeRateAIInsightService.getExchangeRateTrendInsight(
        params.from,
        params.to,
        params.startDate,
        params.endDate,
      ),
  });

  constructor() {
    effect(() => {
      this.pairAndRange();
      this.aiRequest.set(undefined);
    });
  }
}
