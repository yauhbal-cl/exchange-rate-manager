import { Component, computed, inject } from '@angular/core';
import { rxResource } from '@angular/core/rxjs-interop';
import { timeout } from 'rxjs';
import { ExchangeRateUsageAnalyticsService, type CurrencyUsageEntry } from '../../api-client';
import { RecentActivityPanel } from './recent-activity-panel';
import { UsageBreakdownPanel } from './usage-breakdown-panel';
import {
  buildBreakdownView,
  buildRecentActivity,
  computeUsageSummary,
  formatCount,
} from './usage-metrics';

@Component({
  selector: 'app-usage-analytics',
  imports: [UsageBreakdownPanel, RecentActivityPanel],
  styleUrl: './usage-analytics.css',
  templateUrl: './usage-analytics.html',
})
export class UsageAnalytics {
  private readonly service = inject(ExchangeRateUsageAnalyticsService);
  protected readonly now = new Date();
  protected readonly usage = rxResource({
    stream: () => this.service.getUsageAnalytics().pipe(timeout({ each: 10_000 })),
  });
  protected readonly entries = computed<readonly CurrencyUsageEntry[]>(() =>
    this.usage.hasValue() ? (this.usage.value()?.currencies ?? []) : [],
  );
  protected readonly isLoading = computed(() => this.usage.isLoading());
  protected readonly hasError = computed(
    () => !this.isLoading() && this.usage.error() !== undefined,
  );
  protected readonly summary = computed(() => computeUsageSummary(this.entries()));
  protected readonly breakdown = computed(() => buildBreakdownView(this.entries()));
  protected readonly recentActivity = computed(() => buildRecentActivity(this.entries(), this.now));
  protected readonly formatCount = formatCount;
}
