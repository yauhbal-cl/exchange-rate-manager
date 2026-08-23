import { Component, inject } from '@angular/core';
import { rxResource } from '@angular/core/rxjs-interop';
import { ExchangeRateLookupService } from '../../api-client';

@Component({
  selector: 'app-rate-lookup',
  template: `
    <div class="mx-auto max-w-2xl px-4 py-8">
      <h2 class="text-2xl font-semibold text-gray-900">Rate Lookup</h2>

      @if (rate.isLoading()) {
        <p class="mt-4 text-gray-600">Loading exchange rate…</p>
      } @else if (rate.error()) {
        <p class="mt-4 text-red-600">Unable to load the exchange rate right now.</p>
      } @else {
        <div class="mt-4 space-y-2">
          <p><span class="font-medium text-gray-700">From:</span> {{ rate.value()?.fromCurrency }}</p>
          <p><span class="font-medium text-gray-700">To:</span> {{ rate.value()?.toCurrency }}</p>
          <p><span class="font-medium text-gray-700">Rate:</span> {{ rate.value()?.rate }}</p>
          <p><span class="font-medium text-gray-700">Date:</span> {{ rate.value()?.rateDate }}</p>
        </div>
      }
    </div>
  `,
})
export class RateLookup {
  private readonly exchangeRateLookupService = inject(ExchangeRateLookupService);

  protected readonly rate = rxResource({
    stream: () => this.exchangeRateLookupService.getExchangeRate('USD', 'EUR'),
  });
}
