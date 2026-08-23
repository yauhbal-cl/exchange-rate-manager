import { Component, inject } from '@angular/core';
import { rxResource } from '@angular/core/rxjs-interop';
import { ExchangeRateAIInsightService } from '../../api-client';

@Component({
  selector: 'app-ai-insight',
  template: `
    <div class="mx-auto max-w-2xl px-4 py-8">
      <h2 class="text-2xl font-semibold text-gray-900">AI Insight</h2>

      @if (insight.isLoading()) {
        <p class="mt-4 text-gray-600">Loading trend insight…</p>
      } @else if (insight.error()) {
        <p class="mt-4 text-red-600">Unable to generate a trend insight right now.</p>
      } @else {
        <div class="mt-4 space-y-2">
          <p class="text-gray-800">{{ insight.value()?.narrative }}</p>
          <p class="text-sm text-gray-500">
            {{ insight.value()?.startDate }} – {{ insight.value()?.endDate }}
          </p>
        </div>
      }
    </div>
  `,
})
export class AiInsight {
  private readonly exchangeRateAIInsightService = inject(ExchangeRateAIInsightService);

  protected readonly insight = rxResource({
    stream: () => this.exchangeRateAIInsightService.getExchangeRateTrendInsight('USD', 'EUR'),
  });
}
