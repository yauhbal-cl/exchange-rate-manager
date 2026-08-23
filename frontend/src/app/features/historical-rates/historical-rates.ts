import { Component, computed, inject, signal } from '@angular/core';
import { rxResource } from '@angular/core/rxjs-interop';
import { ExchangeRateAIInsightService, ExchangeRateAnalyticsService } from '../../api-client';
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

@Component({
  selector: 'app-historical-rates',
  imports: [CurrencyCombobox, RateTrendChart, HistoricalRatesTable, AiInsightsPanel],
  styleUrl: './historical-rates.css',
  template: `
    <main class="history-page">
      <header class="page-header">
        <div>
          <h1>Historical rates</h1>
          <p>
            Explore historical exchange-rate movements, compare period highs and lows, and review
            contextual AI-generated insights for the selected currency pair.
          </p>
        </div>
        <div class="as-of">Data through {{ formattedToday() }}</div>
      </header>

      <section class="card filters" aria-label="Historical rate filters">
        <div class="filter-grid">
          <div class="currency-field">
            <app-currency-combobox
              id="base-currency"
              name="base-currency"
              label="Base currency"
              [(value)]="baseCurrency"
            />
          </div>
          <button type="button" aria-label="Swap currencies" class="swap-button" (click)="swap()">
            ⇄
          </button>
          <div class="currency-field">
            <app-currency-combobox
              id="quote-currency"
              name="quote-currency"
              label="Quote currency"
              [(value)]="quoteCurrency"
            />
          </div>
          <div class="date-field">
            <span class="field-label">Custom date range</span>
            <div class="date-range">
              <input
                type="date"
                name="range-start"
                [value]="resolvedRange().startDate"
                (change)="setRangeStart($any($event.target).value)"
              />
              <input
                type="date"
                name="range-end"
                [value]="resolvedRange().endDate"
                (change)="setRangeEnd($any($event.target).value)"
              />
            </div>
          </div>
          <div class="preset-field">
            <span class="field-label">Date-range presets</span>
            <div class="preset-list">
              @for (preset of presets; track preset.id) {
                <button
                  type="button"
                  [attr.data-preset]="preset.id"
                  class="preset-button"
                  [class.active]="isActivePreset(preset.id)"
                  (click)="selectPreset(preset.id)"
                >
                  {{ preset.label }}
                </button>
              }
            </div>
          </div>
        </div>
        @if (pairError() || periodError()) {
          <p class="validation-message">{{ pairError() ?? periodError() }}</p>
        }
      </section>

      @if (points().length > 0) {
        <section class="card summary" aria-label="Period summary">
          <div class="pair-summary">
            <span class="eyebrow">Currency pair</span>
            <strong>{{ baseCurrency() }} / {{ quoteCurrency() }}</strong>
            <small>{{ formattedRange() }}</small>
          </div>
          <div class="metric">
            <span>Latest rate</span>
            <strong>{{ latest()?.display }}</strong>
            <small>{{ formattedLatestDate() }}</small>
          </div>
          <div class="metric">
            <span>Period change</span>
            @if (periodChange(); as change) {
              <strong
                [class.positive]="!change.value.isNegative()"
                [class.negative]="change.value.isNegative()"
              >
                {{ change.percent }}
              </strong>
            } @else {
              <strong>—</strong>
            }
            <small>Start to end</small>
          </div>
          <div class="metric">
            <span>Period high</span>
            <strong>{{ periodHigh()?.display }}</strong>
            <small>{{ formattedHighDate() }}</small>
          </div>
          <div class="metric">
            <span>Period low</span>
            <strong>{{ periodLow()?.display }}</strong>
            <small>{{ formattedLowDate() }}</small>
          </div>
        </section>
      } @else {
        <section class="card no-data-summary" data-testid="metrics-no-data">
          No historical rate data for this pair and period.
        </section>
      }

      <section class="main-grid">
        <div class="card chart-card">
          <div class="section-header">
            <div>
              <h2>Exchange-rate movement</h2>
              <p>1 {{ baseCurrency() }} in {{ quoteCurrency() }}</p>
            </div>
            <div class="legend"><span></span>{{ baseCurrency() }} / {{ quoteCurrency() }}</div>
          </div>
          <app-rate-trend-chart
            [points]="points()"
            [dailyChanges]="dailyChanges()"
            [periodHigh]="periodHigh()"
            [periodLow]="periodLow()"
          />
          <div class="annotation-key"><span></span>Period high / low</div>
        </div>

        <app-ai-insights-panel
          class="card insights-card"
          [value]="aiInsightValue()"
          [isLoading]="aiInsight.isLoading()"
          [error]="aiInsight.error()"
        />
      </section>

      <section class="card table-card">
        <div class="table-header">
          <div>
            <h2>Historical rates table</h2>
            <p>Most recent observations first</p>
          </div>
          <span>{{ points().length }} daily observations</span>
        </div>
        <app-historical-rates-table [points]="points()" [dailyChanges]="dailyChanges()" />
      </section>
    </main>
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
  protected readonly period = signal<PeriodSelection>({ kind: 'preset', id: '7D' });

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
  protected readonly formattedToday = computed(() => this.formatDate(this.today));
  protected readonly formattedRange = computed(() => {
    const range = this.resolvedRange();
    return `${this.formatDate(range.startDate)} – ${this.formatDate(range.endDate)}`;
  });
  protected readonly formattedLatestDate = computed(() =>
    this.points().length ? this.formatDate(this.points()[this.points().length - 1].rateDate) : '—',
  );
  protected readonly formattedHighDate = computed(() => this.formatDate(this.periodHigh()?.date));
  protected readonly formattedLowDate = computed(() => this.formatDate(this.periodLow()?.date));

  protected readonly aiInsight = rxResource({
    params: () => this.pairAndRange(),
    stream: ({ params }) =>
      this.exchangeRateAIInsightService.getExchangeRateTrendInsight(
        params.from,
        params.to,
        params.startDate,
        params.endDate,
      ),
  });

  protected readonly aiInsightValue = computed(() =>
    this.aiInsight.hasValue() ? this.aiInsight.value() : undefined,
  );

  private formatDate(value: string | undefined): string {
    if (!value) return '—';
    return new Intl.DateTimeFormat('en-GB', {
      day: '2-digit',
      month: 'short',
      year: 'numeric',
      timeZone: 'UTC',
    }).format(new Date(`${value}T00:00:00Z`));
  }
}
