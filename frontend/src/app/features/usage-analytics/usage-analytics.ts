import { Component, computed, inject } from '@angular/core';
import { rxResource } from '@angular/core/rxjs-interop';
import { timeout } from 'rxjs';
import { ExchangeRateUsageAnalyticsService, type CurrencyUsageEntry } from '../../api-client';

@Component({
  selector: 'app-usage-analytics',
  styleUrl: './usage-analytics.css',
  template: `
    <main class="usage-page">
      <!-- Page header: T012. KPI row: T013. Breakdown / recent-activity panels: T021 / T029. -->
      <div class="data-area">
        @if (isLoading()) {
          <!-- Loading indication (FR-015): T007 -->
        } @else if (hasError()) {
          <!-- Single error message replacing all data sections (FR-014, FR-015a): T007 -->
        } @else if (isEmpty()) {
          <!-- Zero known currencies: KPI zeros + panel empty states (FR-013): T013 / T021 / T029 -->
        } @else if (isPopulated()) {
          <!-- KPI row + breakdown + recent activity: T013 / T021 / T029 -->
        }
      </div>
    </main>
  `,
})
export class UsageAnalytics {
  private readonly exchangeRateUsageAnalyticsService = inject(ExchangeRateUsageAnalyticsService);

  /**
   * Load-time `now`, captured once at component creation and never advanced, so elapsed-time
   * phrases are fixed at page load and repeat renders are identical (data-model.md §3, SC-006).
   */
  protected readonly now = new Date();

  /**
   * The single `GET /exchange/usage` call for this page — no `limit`, no `recentDays`, so the
   * response is the complete entry set including never-queried currencies (research.md §1,
   * ui-contract §Backend calls). `timeout({ each: 10_000 })` turns a stalled request into a
   * resource error, which is exactly the FR-015a behavior: stop waiting, show the FR-014 error
   * state (research.md §3).
   */
  protected readonly usage = rxResource({
    stream: () =>
      this.exchangeRateUsageAnalyticsService.getUsageAnalytics().pipe(timeout({ each: 10_000 })),
  });

  /** Every currency entry from the single response; the sole data source for all three sections. */
  protected readonly entries = computed<readonly CurrencyUsageEntry[]>(() =>
    this.usage.hasValue() ? (this.usage.value()?.currencies ?? []) : [],
  );

  // The four mutually exclusive page states (data-model.md §5, SC-004). Exactly one is true at a
  // time: loading wins over everything, an error is only an error once the request has settled,
  // and empty/populated require a resolved response.

  protected readonly isLoading = computed(() => this.usage.isLoading());

  protected readonly hasError = computed(
    () => !this.isLoading() && this.usage.error() !== undefined,
  );

  private readonly isResolved = computed(
    () => !this.isLoading() && !this.hasError() && this.usage.hasValue(),
  );

  protected readonly isEmpty = computed(() => this.isResolved() && this.entries().length === 0);

  protected readonly isPopulated = computed(() => this.isResolved() && this.entries().length > 0);
}
