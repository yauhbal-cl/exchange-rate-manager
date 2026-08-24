import { Component, computed, inject, signal } from '@angular/core';
import { rxResource } from '@angular/core/rxjs-interop';
import { timeout } from 'rxjs';
import { ExchangeRateUsageAnalyticsService, type CurrencyUsageEntry } from '../../api-client';
import { STANDARD_BACKEND_TIMEOUT_MS } from '../../shared/http-policy';
import {
  DEFAULT_USAGE_WINDOW_DAYS,
  USAGE_WINDOW_OPTIONS,
  buildUsageTableRows,
  computeUsageWindowSummary,
  formatCount,
} from './usage-metrics';
import { UsageAnalyticsTable } from './usage-analytics-table';

@Component({
  selector: 'app-usage-analytics',
  imports: [UsageAnalyticsTable],
  host: {
    class:
      'block min-h-[calc(100vh-57px)] bg-[var(--app-page-bg)] text-[var(--app-text)] tabular-nums',
  },
  templateUrl: './usage-analytics.html',
})
export class UsageAnalytics {
  private readonly service = inject(ExchangeRateUsageAnalyticsService);
  protected readonly now = new Date();
  protected readonly windowOptions = USAGE_WINDOW_OPTIONS;
  protected readonly windowDays = signal<number>(DEFAULT_USAGE_WINDOW_DAYS);
  protected readonly usage = rxResource({
    stream: () =>
      this.service.getUsageAnalytics().pipe(timeout({ each: STANDARD_BACKEND_TIMEOUT_MS })),
  });
  protected readonly entries = computed<readonly CurrencyUsageEntry[]>(() =>
    this.usage.hasValue() ? (this.usage.value()?.currencies ?? []) : [],
  );
  protected readonly isLoading = computed(() => this.usage.isLoading());
  protected readonly hasError = computed(
    () => !this.isLoading() && this.usage.error() !== undefined,
  );
  protected readonly rows = computed(() =>
    buildUsageTableRows(this.entries(), this.windowDays(), this.now),
  );
  protected readonly windowSummary = computed(() => computeUsageWindowSummary(this.rows()));
  protected readonly formatCount = formatCount;

  protected selectWindow(event: Event): void {
    const value = Number((event.target as HTMLSelectElement).value);
    if (USAGE_WINDOW_OPTIONS.some((option) => option === value)) {
      this.windowDays.set(value);
    }
  }
}
