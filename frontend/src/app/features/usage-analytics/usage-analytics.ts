import { Component, computed, inject } from '@angular/core';
import { rxResource } from '@angular/core/rxjs-interop';
import { timeout } from 'rxjs';
import { ExchangeRateUsageAnalyticsService, type CurrencyUsageEntry } from '../../api-client';
import { computeUsageSummary, formatCount } from './usage-metrics';

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
        } @else {
          <!--
            Resolved: empty and populated share this branch because the KPI row renders in both
            (data-model.md §5 — the empty page reads 0 / 0 / explicit none, which is real data, not
            a fabricated value). Only the panels differ between the two, so the isEmpty() /
            isPopulated() split lives inside, below the row.

            FR-002 / FR-024: a real <section> labelled by a visible eyebrow-style <h2> rather than
            an sr-only one (research.md §7), so heading navigation and the visual hierarchy agree.
            Nothing here is focusable or interactive (FR-026).
          -->
          <section class="card kpi-row" data-testid="kpi-row" aria-labelledby="kpi-row-heading">
            <h2 class="kpi-row-heading" id="kpi-row-heading">Summary</h2>

            <div class="kpi-card" data-testid="kpi-total-queries">
              <p class="kpi-label">Total queries</p>
              <p class="kpi-value">{{ formatCount(summary().totalQueries) }}</p>
              <p class="kpi-hint">Every recorded lookup, across all known currencies</p>
            </div>

            <div class="kpi-card" data-testid="kpi-queried-currencies">
              <p class="kpi-label">Currencies queried</p>
              <p class="kpi-value">{{ formatCount(summary().queriedCurrencyCount) }}</p>
              <p class="kpi-hint">Distinct currencies looked up at least once</p>
            </div>

            <div class="kpi-card" data-testid="kpi-most-queried">
              <p class="kpi-label">Most queried currency</p>
              @if (summary().mostQueried; as top) {
                <p class="kpi-value">{{ top.currencyCode }}</p>
                <!-- FR-005: the code alone is not the value — its count is text beside it. -->
                <p class="kpi-hint">Query count: {{ formatCount(top.queryCount) }}</p>
              } @else {
                <!--
                  FR-013: an explicit indication, never blank and never a zero presented as if
                  some currency held the top spot.
                -->
                <p class="kpi-value kpi-value-empty">No currency queried yet</p>
                <p class="kpi-hint">A currency appears here after its first lookup</p>
              }
            </div>
          </section>

          @if (isEmpty()) {
            <!-- No usage records at all: both panels' empty states (FR-013): T021 / T029 -->
          } @else if (isPopulated()) {
            <!-- Breakdown + recent activity, in the two-column grid: T021 / T029 -->
          }
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

  /**
   * The three KPI values (data-model.md §2.1). Derived from the full `entries()` set — no display
   * cap is applied before this point, so the cards describe the whole system (FR-005a, INV-2).
   */
  protected readonly summary = computed(() => computeUsageSummary(this.entries()));

  /** The shared locale count formatter, applied at render time only (FR-019, data-model.md §4). */
  protected readonly formatCount = formatCount;
}
