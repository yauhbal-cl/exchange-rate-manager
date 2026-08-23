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
  host: {
    class:
      'block min-w-0 rounded-2xl border border-[var(--app-border)] bg-[var(--app-surface)] px-[22px] pt-5 pb-[18px] text-[var(--app-text)] shadow-[var(--app-card-shadow)] tabular-nums max-[640px]:px-4 max-[640px]:pt-[18px] max-[640px]:pb-4',
  },
  template: `
    <!--
      FR-024 / research.md §7: a real <section> labelled by its visible <h2>, so heading navigation
      reaches the panel and the accessibility tree matches the visual hierarchy.
    -->
    <section
      class="breakdown-panel flex h-full min-w-0 flex-col"
      aria-labelledby="breakdown-heading"
    >
      <div class="panel-header mb-3.5">
        <h2 class="m-0 text-base font-[750]" id="breakdown-heading">Activity breakdown</h2>
        <p class="mt-1 mb-0 text-xs leading-[1.45] text-[var(--app-muted)]">
          Query volume by currency, most queried first.
        </p>
      </div>

      @if (view().rows.length > 0) {
        <div
          class="breakdown-list grid grid-cols-[minmax(44px,auto)_minmax(0,1fr)_minmax(0,auto)] items-center gap-x-3.5 max-[640px]:gap-x-2.5"
        >
          @for (row of view().rows; track row.currencyCode) {
            <!--
              FR-007 / FR-022: the code and the count are real text nodes either side of the bar, so
              a screen reader gets the whole row without the graphic — and gets it exactly once.
            -->
            <div
              class="breakdown-row col-span-full grid min-w-0 grid-cols-subgrid items-center gap-x-3.5 border-b border-[var(--app-border)] py-[9px] last:border-b-0 max-[640px]:gap-x-2.5"
              data-testid="breakdown-row"
              [attr.data-code]="row.currencyCode"
            >
              <span
                class="row-code min-w-0 text-[13px] font-bold tracking-[0.02em] [overflow-wrap:anywhere]"
                >{{ row.currencyCode }}</span
              >
              <!--
                FR-008 / FR-023 / INV-5: the bar is decorative reinforcement of the count text
                beside it, so the whole track is aria-hidden — no role, no aria-label, no
                aria-valuenow, no title. A screen reader gets the row exactly once, as code plus
                count. The fill's width is the only thing carrying the proportion, and it is a
                value already spelled out in the count text, never information conveyed by
                length alone.
              -->
              <span
                class="row-bar h-2 min-w-0 overflow-hidden rounded-full bg-[var(--app-accent-soft)]"
                data-testid="breakdown-bar"
                aria-hidden="true"
                ><span
                  class="row-bar-fill block h-full min-w-1.5 rounded-[inherit] bg-[var(--app-accent)]"
                  [style.width.%]="row.proportionPercent"
                ></span
              ></span>
              <span
                class="row-count min-w-0 justify-self-end text-right text-[13px] font-[650] text-[#475467] [overflow-wrap:anywhere]"
                >{{ formatCount(row.queryCount) }}</span
              >
            </div>
          }
        </div>

        @if (isCapped()) {
          <!-- FR-009: only when the cap actually hid something, never "top 3 of 3". -->
          <p class="breakdown-note mt-3 mb-0 text-xs leading-[1.45] text-[var(--app-muted)]">
            Showing the top {{ formatCount(view().displayedCount) }} of
            {{ formatCount(view().queriedTotal) }} queried currencies.
          </p>
        }
      } @else {
        <!--
          FR-013: an explicit empty state instead of an empty list — no rows, and therefore no
          zero-length bars. The footnote below still renders (US2 scenario 6).
        -->
        <p
          class="flex min-h-[148px] items-center justify-center rounded-xl border border-dashed border-[var(--app-border)] bg-[var(--app-surface-subtle)] px-4 py-6 text-center text-[13px] leading-normal text-[var(--app-muted)] max-[640px]:min-h-[120px]"
          data-testid="breakdown-empty"
        >
          No currency has been queried yet. Rows appear here after the first lookup.
        </p>
      }

      <!--
        FR-009a: always rendered, stating zero explicitly when every known currency has been
        queried, so the figure is never ambiguous between "none" and "not reported". Counted over
        all currencies, not just the displayed rows.
      -->
      <p
        class="mt-2.5 mb-0 border-t border-dashed border-[var(--app-border)] pt-2.5 text-[11.5px] leading-[1.45] text-[#98a2b3]"
        data-testid="never-queried-footnote"
      >
        {{ neverQueriedFootnote() }}
      </p>
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
