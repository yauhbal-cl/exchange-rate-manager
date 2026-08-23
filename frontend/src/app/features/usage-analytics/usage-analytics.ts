import { Component, computed, inject } from '@angular/core';
import { rxResource } from '@angular/core/rxjs-interop';
import { timeout } from 'rxjs';
import { ExchangeRateUsageAnalyticsService, type CurrencyUsageEntry } from '../../api-client';

@Component({
  selector: 'app-usage-analytics',
  styleUrl: './usage-analytics.css',
  template: `
    <main class="usage-page">
      <!--
        FR-001: title + one-line subtitle, first in DOM order so they sit above every data section
        (ui-contract §Layout order 1). Deliberately outside the data area's state chain — the
        header is identical in all four states — and carries no controls (FR-026).
      -->
      <header class="page-header">
        <h1>Usage analytics</h1>
        <p>An overview of query activity across every currency the system tracks.</p>
      </header>

      <!-- KPI row: T013. Breakdown / recent-activity panels: T021 / T029. -->
      <div class="data-area">
        @if (isLoading()) {
          <!--
            FR-015: the sole content of the data area while the request is in flight — no KPI
            markup, no zeros, no empty states, so there is never a flash of fabricated values.
          -->
          <div
            class="card usage-state usage-state-loading"
            data-testid="usage-loading"
            role="status"
          >
            <span class="usage-spinner" aria-hidden="true"></span>
            <strong>Loading usage analytics</strong>
            <p>Gathering query activity across all currencies…</p>
          </div>
        } @else if (hasError()) {
          <!--
            FR-014 / FR-015a: one message replacing all three data sections. An HTTP failure and
            the 10 s timeout land here identically — the copy deliberately does not distinguish
            them — and no value is shown, not even a zero.
          -->
          <div class="card usage-state usage-state-error" data-testid="usage-error" role="alert">
            <span class="usage-state-icon" aria-hidden="true">!</span>
            <strong>Usage analytics unavailable</strong>
            <p>We couldn't load query activity right now. Please try again later.</p>
          </div>
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
