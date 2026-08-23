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
  styleUrl: './recent-activity-panel.css',
  template: `
    <!--
      FR-024 / research.md §7: a real <section> labelled by its visible <h2>, so heading navigation
      reaches the panel and the accessibility tree matches the visual hierarchy.
    -->
    <section class="recent-panel" aria-labelledby="recent-heading">
      <div class="panel-header">
        <h2 id="recent-heading">Recent activity</h2>
        <p>The most recently queried currencies, newest first.</p>
      </div>

      @if (entries().length > 0) {
        <div class="recent-list">
          @for (entry of entries(); track entry.currencyCode) {
            <!--
              FR-022: the code and the time are real text nodes, so a screen reader gets the whole
              entry — and gets it exactly once, as code plus time.
            -->
            <div data-testid="recent-entry" [attr.data-code]="entry.currencyCode">
              <span class="entry-code">{{ entry.currencyCode }}</span>
              <!--
                FR-025: the datetime attribute carries the raw ISO instant verbatim, so the exact
                moment stays machine-readable however the phrase beside it is worded. FR-012a: the
                title supplements it with the absolute local date-time for inspection — a supplement
                only, never the sole carrier of a value, since the phrase is already readable text
                (FR-022, FR-026).
              -->
              <time
                class="entry-time"
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
        <p data-testid="recent-empty">
          No currency has been queried yet. Recent lookups appear here as they happen.
        </p>
      }
    </section>
  `,
})
export class RecentActivityPanel {
  readonly entries = input<readonly RecentActivityEntry[]>([]);
}
