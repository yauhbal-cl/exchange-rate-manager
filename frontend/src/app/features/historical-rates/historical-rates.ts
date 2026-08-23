import { Component, computed, inject, signal } from '@angular/core';
import { rxResource } from '@angular/core/rxjs-interop';
import { ExchangeRateAnalyticsService } from '../../api-client';
import type { Currency } from '../rate-lookup/currencies';
import { resolveRange, todayIso, type PeriodSelection } from './period-presets';

interface TrendRequest {
  from: string;
  to: string;
  startDate: string;
  endDate: string;
}

@Component({
  selector: 'app-historical-rates',
  template: `
    <div class="mx-auto max-w-6xl px-4 py-8">
      <h2 class="text-2xl font-semibold text-gray-900">Historical Exchange Rate Trends</h2>
      <p class="mt-1 text-gray-600">
        Explore how a currency pair has moved over a chosen period, with summary metrics, a
        chart, and the raw historical rates.
      </p>
    </div>
  `,
})
export class HistoricalRates {
  private readonly exchangeRateAnalyticsService = inject(ExchangeRateAnalyticsService);

  protected readonly today = todayIso();

  protected readonly baseCurrency = signal<Currency['code']>('USD');
  protected readonly quoteCurrency = signal<Currency['code']>('EUR');

  protected readonly pairError = computed<string | null>(() =>
    this.baseCurrency() === this.quoteCurrency()
      ? 'Base and quote currency must be different.'
      : null,
  );

  protected readonly period = signal<PeriodSelection>({ kind: 'preset', id: '1M' });

  protected readonly pairAndRange = computed<TrendRequest | undefined>(() => {
    if (this.pairError() !== null) {
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
}
