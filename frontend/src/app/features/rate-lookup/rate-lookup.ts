import { Component, computed, inject, signal } from '@angular/core';
import { HttpErrorResponse } from '@angular/common/http';
import { rxResource } from '@angular/core/rxjs-interop';
import { ExchangeRateLookupService } from '../../api-client';
import { CurrencyCombobox } from './currency-combobox';

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
  imports: [CurrencyCombobox],
  styleUrl: './rate-lookup.css',
  template: `
    <main class="calculator-page">
      <header class="page-header">
        <div>
          <h1>Rate calculator</h1>
          <p>
            Look up the exchange rate for a currency pair using the latest available data or a
            specific historical date.
          </p>
        </div>
        <div class="as-of">Rates available through {{ formattedToday }}</div>
      </header>

      <section class="calculator-grid">
        <div class="card lookup-card">
          <div class="section-header">
            <span class="section-icon" aria-hidden="true">↗</span>
            <div>
              <h2>Exchange rate lookup</h2>
              <p>Choose a source and target currency</p>
            </div>
          </div>

          <form (submit)="$event.preventDefault(); onSubmit()">
            <div class="currency-row">
              <app-currency-combobox
                id="from-currency"
                name="from"
                label="From currency"
                [(value)]="fromCurrency"
              />

              <button
                type="button"
                class="swap-button"
                aria-label="Swap currencies"
                [disabled]="!fromCurrency() || !toCurrency()"
                (click)="swapCurrencies()"
              >
                ⇄
              </button>

              <app-currency-combobox
                id="to-currency"
                name="to"
                label="To currency"
                [(value)]="toCurrency"
              />
            </div>

            <label class="date-field">
              <span>Date <small>Optional</small></span>
              <input
                type="date"
                name="date"
                [attr.max]="today"
                [value]="date()"
                (change)="date.set($any($event.target).value)"
              />
              <small>Leave blank to use the latest available rate.</small>
            </label>

            @if (validationError()) {
              <p class="validation-message">{{ validationError() }}</p>
            }

            <button
              type="submit"
              class="submit-button"
              [disabled]="validationError() !== null || rate.isLoading()"
            >
              @if (rate.isLoading()) {
                <span class="spinner" aria-hidden="true"></span> Looking up rate…
              } @else {
                Look up rate <span aria-hidden="true">→</span>
              }
            </button>
          </form>
        </div>

        <div class="card result-card" aria-live="polite">
          <div class="result-header">
            <div>
              <h2>Exchange rate</h2>
              <p>Your lookup result</p>
            </div>
          </div>

          @if (rate.isLoading()) {
            <div class="result-state loading-state">
              <span class="large-spinner" aria-hidden="true"></span>
              <strong>Finding exchange rate</strong>
              <p>Checking the selected pair and date…</p>
            </div>
          } @else if (lookupError(); as error) {
            <div class="result-state error-state" [attr.data-category]="error.category">
              <span class="state-icon" aria-hidden="true">!</span>
              <strong>Rate unavailable</strong>
              <p>{{ error.message }}</p>
            </div>
          } @else if (rate.value(); as value) {
            <div class="result-content">
              <div class="rate-hero">
                <div class="pair-label">
                  <span>{{ value.fromCurrency }}</span>
                  <span class="pair-arrow" aria-hidden="true">→</span>
                  <span>{{ value.toCurrency }}</span>
                </div>
                <span class="rate-caption">Exchange rate</span>
                <div class="rate-value">{{ value.rate }}</div>
              </div>

              <div class="observation-date">
                <span class="calendar-icon" aria-hidden="true"></span>
                <div>
                  <span>Rate observation date</span>
                  <strong>{{ value.rateDate }}</strong>
                  <p>This is the market date on which this stored exchange rate was recorded.</p>
                </div>
              </div>
            </div>
          } @else {
            <div class="result-state empty-state">
              <span class="empty-icon" aria-hidden="true">⇄</span>
              <strong>Your rate will appear here</strong>
              <p>Select two currencies and submit the form to see their exchange rate.</p>
            </div>
          }
        </div>
      </section>

      <p class="footer-note">
        Exchange rates reflect stored market data and may differ from rates offered by financial
        institutions.
      </p>
    </main>
  `,
})
export class RateLookup {
  private readonly exchangeRateLookupService = inject(ExchangeRateLookupService);

  protected readonly today = todayIso();
  protected readonly formattedToday = new Intl.DateTimeFormat('en-GB', {
    day: '2-digit',
    month: 'short',
    year: 'numeric',
    timeZone: 'UTC',
  }).format(new Date(`${this.today}T00:00:00Z`));

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

  protected swapCurrencies(): void {
    const from = this.fromCurrency();
    this.fromCurrency.set(this.toCurrency());
    this.toCurrency.set(from);
  }
}
