import { Component, computed, input } from '@angular/core';
import { type BreakdownView, formatCount } from './usage-metrics';

/** Rendered when the panel is mounted without data — the same shape an empty response produces. */
const EMPTY_VIEW: BreakdownView = {
  rows: [],
  displayedCount: 0,
  queriedTotal: 0,
  neverQueriedCount: 0,
};

/**
 * The "Activity breakdown" panel (FR-006 … FR-009a, FR-013): ranked rows of currency code, a
 * proportional bar, and the query count.
 *
 * Purely presentational — one signal input, no outputs, no state of its own, and nothing focusable
 * or interactive (FR-026). Every ranking, capping and counting decision was already made by
 * `buildBreakdownView` (data-model.md §2.2), so this component only formats and lays out.
 */
@Component({
  selector: 'app-usage-breakdown-panel',
  styleUrl: './usage-breakdown-panel.css',
  template: `
    <!--
      FR-024 / research.md §7: a real <section> labelled by its visible <h2>, so heading navigation
      reaches the panel and the accessibility tree matches the visual hierarchy.
    -->
    <section class="breakdown-panel" aria-labelledby="breakdown-heading">
      <div class="panel-header">
        <h2 id="breakdown-heading">Activity breakdown</h2>
        <p>Query volume by currency, most queried first.</p>
      </div>

      @if (view().rows.length > 0) {
        <div class="breakdown-list">
          @for (row of view().rows; track row.currencyCode) {
            <!--
              FR-007 / FR-022: the code and the count are real text nodes either side of the bar, so
              a screen reader gets the whole row without the graphic — and gets it exactly once.
            -->
            <div
              class="breakdown-row"
              data-testid="breakdown-row"
              [attr.data-code]="row.currencyCode"
            >
              <span class="row-code">{{ row.currencyCode }}</span>
              <!-- Bar track + fill; the FR-008 / FR-023 details land in T019. -->
              <span class="row-bar"><span class="row-bar-fill"></span></span>
              <span class="row-count">{{ formatCount(row.queryCount) }}</span>
            </div>
          }
        </div>

        @if (isCapped()) {
          <!-- FR-009: only when the cap actually hid something, never "top 3 of 3". -->
          <p class="breakdown-note">
            Showing the top {{ formatCount(view().displayedCount) }} of
            {{ formatCount(view().queriedTotal) }} queried currencies.
          </p>
        }
      } @else {
        <!--
          FR-013: an explicit empty state instead of an empty list — no rows, and therefore no
          zero-length bars. The footnote below still renders (US2 scenario 6).
        -->
        <p data-testid="breakdown-empty">
          No currency has been queried yet. Rows appear here after the first lookup.
        </p>
      }

      <!--
        FR-009a: always rendered, stating zero explicitly when every known currency has been
        queried, so the figure is never ambiguous between "none" and "not reported". Counted over
        all currencies, not just the displayed rows.
      -->
      <p data-testid="never-queried-footnote">{{ neverQueriedFootnote() }}</p>
    </section>
  `,
})
export class UsageBreakdownPanel {
  readonly view = input<BreakdownView>(EMPTY_VIEW);

  /** FR-009: the "top N of M" line belongs only where queried currencies were left out. */
  protected readonly isCapped = computed(
    () => this.view().queriedTotal > this.view().displayedCount,
  );

  protected readonly neverQueriedFootnote = computed(() => {
    const count = this.view().neverQueriedCount;
    const noun = count === 1 ? 'known currency has' : 'known currencies have';
    return `${formatCount(count)} ${noun} never been queried.`;
  });

  /** The shared locale count formatter, applied at render time only (FR-019, data-model.md §4). */
  protected readonly formatCount = formatCount;
}
