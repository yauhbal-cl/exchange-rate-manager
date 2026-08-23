import { Component, inject } from '@angular/core';
import { rxResource } from '@angular/core/rxjs-interop';
import { ExchangeRateUsageAnalyticsService } from '../../api-client';

@Component({
  selector: 'app-usage-analytics',
  template: `
    <div class="mx-auto max-w-2xl px-4 py-8">
      <h2 class="text-2xl font-semibold text-gray-900">Usage Analytics</h2>

      @if (usage.isLoading()) {
        <p class="mt-4 text-gray-600">Loading usage analytics…</p>
      } @else if (usage.error()) {
        <p class="mt-4 text-red-600">Unable to load usage analytics right now.</p>
      } @else {
        <ul class="mt-4 divide-y divide-gray-200">
          @for (entry of usage.value()?.currencies; track entry.currencyCode) {
            <li class="flex items-center justify-between py-2">
              <span class="font-medium text-gray-700">{{ entry.currencyCode }}</span>
              <span class="text-gray-600">{{ entry.queryCount }} queries</span>
              <span class="text-gray-500">{{ entry.lastQueriedAt ?? 'never queried' }}</span>
            </li>
          }
        </ul>
      }
    </div>
  `,
})
export class UsageAnalytics {
  private readonly exchangeRateUsageAnalyticsService = inject(ExchangeRateUsageAnalyticsService);

  protected readonly usage = rxResource({
    stream: () => this.exchangeRateUsageAnalyticsService.getUsageAnalytics(),
  });
}
