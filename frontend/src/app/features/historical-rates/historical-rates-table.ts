import { Component, computed, input } from '@angular/core';
import type { RateTrendPoint } from '../../api-client';
import { formatRate } from '../../shared/rate-format';
import type { DailyChange } from './trend-metrics';

interface Row {
  rateDate: string;
  rate: string;
  percent: string | null;
}

@Component({
  selector: 'app-historical-rates-table',
  host: { class: 'block' },
  template: `
    @if (rows().length > 0) {
      <div class="table-scroll max-h-[520px] overflow-auto">
        <table class="w-full min-w-[620px] border-collapse tabular-nums">
          <thead>
            <tr>
              <th
                class="sticky top-0 z-1 border-b border-[#e4e7ec] bg-[#fafbfc] px-[22px] py-[13px] text-left text-[11px] font-bold tracking-[0.045em] text-[#667085] uppercase max-[720px]:px-4"
              >
                Date
              </th>
              <th
                class="sticky top-0 z-1 border-b border-[#e4e7ec] bg-[#fafbfc] px-[22px] py-[13px] text-right text-[11px] font-bold tracking-[0.045em] text-[#667085] uppercase max-[720px]:px-4"
              >
                Exchange rate
              </th>
              <th
                class="sticky top-0 z-1 border-b border-[#e4e7ec] bg-[#fafbfc] px-[22px] py-[13px] text-right text-[11px] font-bold tracking-[0.045em] text-[#667085] uppercase max-[720px]:px-4"
              >
                Daily change
              </th>
            </tr>
          </thead>
          <tbody>
            @for (row of rows(); track row.rateDate) {
              <tr class="[&:last-child>td]:border-b-0 [&:hover>td]:bg-[#fcfcfd]">
                <td
                  class="border-b border-[#e4e7ec] px-[22px] py-[13px] text-[13px] text-[#475467] max-[720px]:px-4"
                >
                  {{ row.rateDate }}
                </td>
                <td
                  class="rate border-b border-[#e4e7ec] px-[22px] py-[13px] text-right text-[13px] font-[650] text-[#344054] max-[720px]:px-4"
                >
                  {{ row.rate }}
                </td>
                <td
                  class="border-b border-[#e4e7ec] px-[22px] py-[13px] text-right text-[13px] text-[#475467] max-[720px]:px-4"
                >
                  <span
                    class="change inline-flex min-w-[72px] justify-end font-[650]"
                    [class]="
                      !row.percent
                        ? 'muted text-[#98a2b3]'
                        : row.percent.startsWith('-')
                          ? 'negative text-[#b42318]'
                          : 'positive text-[#18794e]'
                    "
                    >{{ row.percent ?? '—' }}</span
                  >
                </td>
              </tr>
            }
          </tbody>
        </table>
      </div>
    } @else {
      <div
        class="table-empty flex h-32 items-center justify-center text-[#667085]"
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
      rate: formatRate(point.rate),
      percent: percentByDate.get(point.rateDate) ?? null,
    }));
  });
}
