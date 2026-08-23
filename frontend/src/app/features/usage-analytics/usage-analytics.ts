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

      <!-- KPI row: T013. Breakdown panel: T021. Recent-activity panel: T029. -->
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
            a fabricated value). The panels render here too: per data-model.md §5 the empty page
            shows *both panels' own* empty states, which is what their view models produce from an
            empty entry set — so neither is gated on isPopulated().

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

          <!--
            ui-contract §Layout order 3: breakdown on the left (visibly wider), recent activity on
            the right, collapsing to one column at ≤900 px. This wrapper only fixes DOM order —
            the grid CSS itself is T031.
          -->
          <div class="panel-grid">
            <!--
              FR-005a / ui-contract behavioral rule 1: fed from the same entries() signal as the
              KPI row above — one response per page load, so the panel and the cards can never
              disagree. When entries() is empty the view model is empty too, and the panel renders
              its own empty state with the never-queried footnote intact (data-model.md §5).
            -->
            <app-usage-breakdown-panel [view]="breakdown()" />

            <!--
              ui-contract behavioral rule 10 / FR-012: the elapsed-time phrases are computed against
              a now captured once at construction and never advanced — so they don't tick while the
              page is open and a re-render reproduces the same wording. Second in DOM order, which
              is also the ≤900 px stack order (KPI → breakdown → recent activity), reached without
              any CSS order override. Renders in the empty state too, showing its own empty state.
            -->
            <app-recent-activity-panel [entries]="recentActivity()" />
          </div>
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

  // Empty and populated share one template branch: the KPI row and both panels render in each,
  // differing only in the values their view models produce (data-model.md §5). These two stay as
  // the named, testable form of that distinction rather than as template gates.

  protected readonly isEmpty = computed(() => this.isResolved() && this.entries().length === 0);

  protected readonly isPopulated = computed(() => this.isResolved() && this.entries().length > 0);

  /**
   * The three KPI values (data-model.md §2.1). Derived from the full `entries()` set — no display
   * cap is applied before this point, so the cards describe the whole system (FR-005a, INV-2).
   */
  protected readonly summary = computed(() => computeUsageSummary(this.entries()));

  /**
   * The breakdown panel's view model (data-model.md §2.2), derived from the *same* `entries()`
   * signal as `summary()` — one response drives both, which is what makes the KPI row and the
   * panel structurally incapable of disagreeing (FR-005a, ui-contract behavioral rule 1). The
   * 10-row cap lives inside `buildBreakdownView`, after the KPIs have already been computed over
   * the full set (INV-2).
   */
  protected readonly breakdown = computed(() => buildBreakdownView(this.entries()));

  /**
   * The recent-activity panel's view model (data-model.md §2.3), from the same `entries()` signal
   * again, plus the construction-time `now`. Passing that stored field — never a fresh `new Date()`
   * — is what makes the elapsed-time phrases stable for the life of the page: this computed only
   * re-runs when `entries()` changes, and even then it measures against the same instant (FR-012,
   * ui-contract behavioral rule 10). Filtering out never-queried currencies and the row cap both
   * live inside `buildRecentActivity` (FR-011).
   */
  protected readonly recentActivity = computed(() => buildRecentActivity(this.entries(), this.now));

  /** The shared locale count formatter, applied at render time only (FR-019, data-model.md §4). */
  protected readonly formatCount = formatCount;
}
