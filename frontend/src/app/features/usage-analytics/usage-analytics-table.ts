import { Component, input, signal } from '@angular/core';
import { formatCount, type UsageTableRow } from './usage-metrics';
import { UsageActivityChart } from './usage-activity-chart';
import { UsageActivityDetails } from './usage-activity-details';

@Component({
  selector: 'app-usage-analytics-table',
  imports: [UsageActivityChart, UsageActivityDetails],
  host: { class: 'block min-w-0' },
  template: `
    <div class="overflow-x-auto">
      <table class="w-full min-w-[820px] border-collapse tabular-nums">
        <thead>
          <tr>
            <th scope="col" class="table-heading text-left">Currency</th>
            <th scope="col" class="table-heading text-right">Total queries</th>
            <th scope="col" class="table-heading text-right">In window</th>
            <th scope="col" class="table-heading text-left">Last queried</th>
            <th scope="col" class="table-heading w-[34%] min-w-[250px] text-left">Activity</th>
            <th scope="col" class="table-heading text-right">Details</th>
          </tr>
        </thead>
        <tbody>
          @for (row of rows(); track row.currencyCode) {
            <tr class="group" data-testid="usage-row" [attr.data-code]="row.currencyCode">
              <td class="table-cell font-bold tracking-[0.035em] text-[#344054]">
                {{ row.currencyCode }}
              </td>
              <td class="table-cell text-right font-[650] text-[#344054]">
                {{ formatCount(row.totalQueries) }}
              </td>
              <td class="table-cell text-right font-[650] text-[#344054]">
                {{ formatCount(row.queriesInWindow) }}
              </td>
              <td class="table-cell text-[#667085]">
                @if (row.lastQueriedAt) {
                  <time [attr.datetime]="row.lastQueriedAt" [title]="row.lastQueriedAbsolute">
                    {{ row.lastQueriedRelative }}
                  </time>
                } @else {
                  <span>Never</span>
                }
              </td>
              <td class="table-cell">
                <app-usage-activity-chart
                  [activity]="row.activity"
                  [currencyCode]="row.currencyCode"
                  [windowDays]="windowDays()"
                  [queriesInWindow]="row.queriesInWindow"
                />
              </td>
              <td class="table-cell text-right">
                <button
                  type="button"
                  class="rounded-lg border border-[var(--app-border)] bg-white px-3 py-2 text-xs font-[700] text-[#475467] hover:border-[#c7c9ee] hover:bg-[#fafaff] focus:outline-none focus:ring-2 focus:ring-[var(--app-accent-soft)]"
                  (click)="selectedRow.set(row)"
                  [attr.aria-label]="'View detailed activity for ' + row.currencyCode"
                  data-testid="activity-details-button"
                >
                  Details
                </button>
              </td>
            </tr>
          } @empty {
            <tr>
              <td colspan="6" class="px-6 py-16 text-center text-sm text-[#667085]">
                No currencies are available.
              </td>
            </tr>
          }
        </tbody>
      </table>
    </div>
    @if (selectedRow(); as row) {
      <app-usage-activity-details
        [row]="row"
        [windowDays]="windowDays()"
        (closed)="selectedRow.set(null)"
      />
    }
  `,
  styles: `
    .table-heading {
      border-bottom: 1px solid var(--app-border);
      background: #fafbfc;
      padding: 13px 20px;
      color: #667085;
      font-size: 11px;
      font-weight: 700;
      letter-spacing: 0.045em;
      text-transform: uppercase;
      white-space: nowrap;
    }

    .table-cell {
      border-bottom: 1px solid var(--app-border);
      padding: 14px 20px;
      font-size: 13px;
    }

    tbody tr:last-child .table-cell {
      border-bottom: 0;
    }

    tbody tr:hover .table-cell {
      background: #fcfcfd;
    }
  `,
})
export class UsageAnalyticsTable {
  readonly rows = input<readonly UsageTableRow[]>([]);
  readonly windowDays = input.required<number>();
  protected readonly selectedRow = signal<UsageTableRow | null>(null);
  protected readonly formatCount = formatCount;
}
