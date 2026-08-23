import { Component, input } from '@angular/core';
import { type RecentActivityEntry } from './usage-metrics';

/**
 * The "Recent activity" panel (FR-010 … FR-013): the most recently queried currencies, newest
 * first, each as a currency code beside the elapsed time since that query.
 *
 * Purely presentational — one signal input, no outputs, no state of its own, and nothing focusable
 * or interactive (FR-026). Every filtering, ordering and capping decision was already made by
 * `buildRecentActivity` (data-model.md §2.3), and both the relative phrase and the absolute local
 * date-time arrive pre-formatted on the entry, so this component only lays them out.
 */
@Component({
  selector: 'app-recent-activity-panel',
  host: {
    class:
      'block min-w-0 rounded-2xl border border-[var(--app-border)] bg-[var(--app-surface)] px-[22px] pt-5 pb-[18px] text-[var(--app-text)] shadow-[var(--app-card-shadow)] tabular-nums max-[640px]:px-4 max-[640px]:pt-[18px] max-[640px]:pb-4',
  },
  template: `
    <!--
      FR-024 / research.md §7: a real <section> labelled by its visible <h2>, so heading navigation
      reaches the panel and the accessibility tree matches the visual hierarchy.
    -->
    <section class="recent-panel flex h-full min-w-0 flex-col" aria-labelledby="recent-heading">
      <div class="panel-header mb-3.5">
        <h2 class="m-0 text-base font-[750]" id="recent-heading">Recent activity</h2>
        <p class="mt-1 mb-0 text-xs leading-[1.45] text-[var(--app-muted)]">
          The most recently queried currencies, newest first.
        </p>
      </div>

      @if (entries().length > 0) {
        <div class="recent-list flex min-w-0 flex-col">
          @for (entry of entries(); track entry.currencyCode) {
            <!--
              FR-022: the code and the time are real text nodes, so a screen reader gets the whole
              entry — and gets it exactly once, as code plus time.
            -->
            <div
              class="grid min-w-0 grid-cols-[minmax(0,auto)_minmax(0,1fr)] items-baseline gap-x-3 border-b border-[var(--app-border)] py-[11px] last:border-b-0 max-[640px]:gap-x-2.5"
              data-testid="recent-entry"
              [attr.data-code]="entry.currencyCode"
            >
              <span
                class="entry-code min-w-0 text-[13px] font-bold tracking-[0.02em] [overflow-wrap:anywhere]"
                >{{ entry.currencyCode }}</span
              >
              <!--
                FR-025: the datetime attribute carries the raw ISO instant verbatim, so the exact
                moment stays machine-readable however the phrase beside it is worded. FR-012a: the
                title supplements it with the absolute local date-time for inspection — a supplement
                only, never the sole carrier of a value, since the phrase is already readable text
                (FR-022, FR-026).
              -->
              <time
                class="entry-time min-w-0 justify-self-end text-right text-[12.5px] text-[var(--app-muted)] [overflow-wrap:anywhere]"
                [attr.datetime]="entry.lastQueriedAt"
                [title]="entry.absoluteLocal"
                >{{ entry.relativePhrase }}</time
              >
            </div>
          }
        </div>
      } @else {
        <!--
          FR-013: an explicit empty state instead of an empty list, and distinct from the page-level
          error state — nothing has been queried yet, which is not a failure.
        -->
        <p
          class="flex min-h-[148px] items-center justify-center rounded-xl border border-dashed border-[var(--app-border)] bg-[var(--app-surface-subtle)] px-4 py-6 text-center text-[13px] leading-normal text-[var(--app-muted)] max-[640px]:min-h-[120px]"
          data-testid="recent-empty"
        >
          No currency has been queried yet. Recent lookups appear here as they happen.
        </p>
      }
    </section>
  `,
})
export class RecentActivityPanel {
  readonly entries = input<readonly RecentActivityEntry[]>([]);
}
