import { Component, computed, input } from '@angular/core';
import type { RateTrendPoint } from '../../api-client';
import type { DailyChange } from './trend-metrics';

interface Row {
  rateDate: string;
  rate: string;
  percent: string | null;
}

@Component({
  selector: 'app-historical-rates-table',
  styleUrl: './historical-rates-table.css',
  template: `
    @if (rows().length > 0) {
      <div class="table-scroll">
        <table>
          <thead>
            <tr>
              <th>Date</th>
              <th>Exchange rate</th>
              <th>Daily change</th>
            </tr>
          </thead>
          <tbody>
            @for (row of rows(); track row.rateDate) {
              <tr>
                <td>{{ row.rateDate }}</td>
                <td class="rate">{{ row.rate }}</td>
                <td>
                  <span
                    class="change"
                    [class.positive]="row.percent && !row.percent.startsWith('-')"
                    [class.negative]="row.percent && row.percent.startsWith('-')"
                    [class.muted]="!row.percent"
                    >{{ row.percent ?? '—' }}</span
                  >
                </td>
              </tr>
            }
          </tbody>
        </table>
      </div>
    } @else {
      <div class="table-empty" data-testid="table-no-data">
        No historical rate data for this pair and period.
      </div>
    }
  `,
})
export class HistoricalRatesTable {
  readonly points = input<readonly RateTrendPoint[]>([]);
  readonly dailyChanges = input<readonly DailyChange[]>([]);

  protected readonly rows = computed<Row[]>(() => {
    const percentByDate = new Map(
      this.dailyChanges().map((change) => [change.rateDate, change.percent]),
    );
    return [...this.points()].reverse().map((point) => ({
      rateDate: point.rateDate,
      rate: point.rate,
      percent: percentByDate.get(point.rateDate) ?? null,
    }));
  });
}
