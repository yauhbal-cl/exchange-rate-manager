import { Component, computed, inject, signal } from '@angular/core';
import { HttpErrorResponse } from '@angular/common/http';
import { rxResource } from '@angular/core/rxjs-interop';
import { ExchangeRateLookupService } from '../../api-client';
import { CURRENCY_CODES } from './currencies';

interface RateLookupRequest {
  from: string;
  to: string;
  date: string | undefined;
}

interface LookupError {
  category: 'invalid' | 'no-data' | 'unreachable';
  message: string;
}

const UNREACHABLE_MESSAGE = 'Unable to reach the exchange rate service. Please try again later.';

function todayIso(): string {
  return new Date().toISOString().slice(0, 10);
}

@Component({
  selector: 'app-rate-lookup',
  template: `
    <div class="mx-auto max-w-2xl px-4 py-8">
      <h2 class="text-2xl font-semibold text-gray-900">Rate Lookup</h2>

      <div class="mt-4 flex flex-wrap gap-4">
        <label class="flex flex-col gap-1">
          <span class="text-sm font-medium text-gray-700">From</span>
          <select
            name="from"
            class="rounded border border-gray-300 px-3 py-2"
            [value]="fromCurrency()"
            (change)="fromCurrency.set($any($event.target).value)"
          >
            <option value=""></option>
            @for (code of currencyCodes; track code) {
              <option [value]="code">{{ code }}</option>
            }
          </select>
        </label>

        <label class="flex flex-col gap-1">
          <span class="text-sm font-medium text-gray-700">To</span>
          <select
            name="to"
            class="rounded border border-gray-300 px-3 py-2"
            [value]="toCurrency()"
            (change)="toCurrency.set($any($event.target).value)"
          >
            <option value=""></option>
            @for (code of currencyCodes; track code) {
              <option [value]="code">{{ code }}</option>
            }
          </select>
        </label>

        <label class="flex flex-col gap-1">
          <span class="text-sm font-medium text-gray-700">Date (optional)</span>
          <input
            type="date"
            name="date"
            class="rounded border border-gray-300 px-3 py-2"
            [attr.max]="today"
            [value]="date()"
            (change)="date.set($any($event.target).value)"
          />
        </label>
      </div>

      @if (validationError()) {
        <p class="mt-2 text-amber-700">{{ validationError() }}</p>
      }

      <div class="mt-4">
        <button
          type="submit"
          class="rounded bg-blue-600 px-4 py-2 text-white disabled:opacity-50"
          [disabled]="validationError() !== null || rate.isLoading()"
          (click)="onSubmit()"
        >
          Look up rate
        </button>
      </div>

      @if (rate.isLoading()) {
        <p class="mt-4 text-gray-600">Loading exchange rate…</p>
      } @else if (lookupError(); as error) {
        <p class="mt-4 text-red-600" [attr.data-category]="error.category">{{ error.message }}</p>
      } @else if (rate.value(); as value) {
        <div class="mt-4 space-y-2">
          <p><span class="font-medium text-gray-700">From:</span> {{ value.fromCurrency }}</p>
          <p><span class="font-medium text-gray-700">To:</span> {{ value.toCurrency }}</p>
          <p><span class="font-medium text-gray-700">Rate:</span> {{ value.rate }}</p>
          <p><span class="font-medium text-gray-700">Date:</span> {{ value.rateDate }}</p>
          <p><span class="font-medium text-gray-700">From-currency usage count:</span> {{ value.fromCurrencyUsageCount }}</p>
          <p><span class="font-medium text-gray-700">To-currency usage count:</span> {{ value.toCurrencyUsageCount }}</p>
        </div>
      }
    </div>
  `,
})
export class RateLookup {
  private readonly exchangeRateLookupService = inject(ExchangeRateLookupService);

  protected readonly currencyCodes = CURRENCY_CODES;
  protected readonly today = todayIso();

  protected readonly fromCurrency = signal('');
  protected readonly toCurrency = signal('');
  protected readonly date = signal('');
  protected readonly submittedRequest = signal<RateLookupRequest | undefined>(undefined);

  protected readonly rate = rxResource({
    params: () => this.submittedRequest(),
    stream: ({ params }) =>
      this.exchangeRateLookupService.getExchangeRate(params.from, params.to, params.date),
  });

  protected readonly validationError = computed<string | null>(() => {
    if (!this.fromCurrency() || !this.toCurrency()) {
      return 'Select both a source and a target currency.';
    }
    if (this.fromCurrency() === this.toCurrency()) {
      return 'Source and target currency must be different.';
    }
    if (this.date() && this.date() > this.today) {
      return 'Date cannot be in the future.';
    }
    return null;
  });

  protected readonly lookupError = computed<LookupError | null>(() => {
    const error = this.rate.error();
    if (!error) {
      return null;
    }
    if (error instanceof HttpErrorResponse) {
      const detail = (error.error as { detail?: string } | null)?.detail;
      if (error.status === 400) {
        return { category: 'invalid', message: detail ?? UNREACHABLE_MESSAGE };
      }
      if (error.status === 404) {
        return { category: 'no-data', message: detail ?? UNREACHABLE_MESSAGE };
      }
    }
    return { category: 'unreachable', message: UNREACHABLE_MESSAGE };
  });

  protected onSubmit(): void {
    if (this.validationError() !== null) {
      return;
    }
    this.submittedRequest.set({
      from: this.fromCurrency(),
      to: this.toCurrency(),
      date: this.date() || undefined,
    });
  }
}
