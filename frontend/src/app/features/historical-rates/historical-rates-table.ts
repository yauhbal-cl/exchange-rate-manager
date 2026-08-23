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
  template: `
    @if (rows().length > 0) {
      <table class="w-full text-left text-sm">
        <thead>
          <tr class="border-b border-gray-200 text-gray-500">
            <th class="py-2 pr-4 font-medium">Date</th>
            <th class="py-2 pr-4 font-medium">Exchange rate</th>
            <th class="py-2 font-medium">Daily change</th>
          </tr>
        </thead>
        <tbody>
          @for (row of rows(); track row.rateDate) {
            <tr class="border-b border-gray-100">
              <td class="py-2 pr-4 text-gray-700">{{ row.rateDate }}</td>
              <td class="py-2 pr-4 text-gray-900">{{ row.rate }}</td>
              <td
                class="py-2"
                [class.text-green-700]="row.percent && !row.percent.startsWith('-')"
                [class.text-red-700]="row.percent && row.percent.startsWith('-')"
                [class.text-gray-400]="!row.percent"
              >
                {{ row.percent ?? '—' }}
              </td>
            </tr>
          }
        </tbody>
      </table>
    } @else {
      <div
        class="flex h-32 items-center justify-center text-gray-500"
        data-testid="table-no-data"
      >
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
