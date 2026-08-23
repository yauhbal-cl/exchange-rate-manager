import { Component, input } from '@angular/core';
import type { ExchangeRateResponse } from '../../api-client';

export interface LookupError {
  category: 'invalid' | 'no-data' | 'unreachable';
  message: string;
}

export type RateLookupResultState =
  | { kind: 'initial' }
  | { kind: 'loading' }
  | { kind: 'error'; error: LookupError }
  | { kind: 'success'; value: ExchangeRateResponse };

@Component({
  selector: 'app-rate-lookup-result',
  styleUrl: './rate-lookup-result.css',
  template: `
    <div class="card result-card" aria-live="polite">
      <div class="result-header">
        <div>
          <h2>Exchange rate</h2>
          <p>Your lookup result</p>
        </div>
      </div>
      @switch (state().kind) {
        @case ('loading') {
          <div class="result-state loading-state" role="status">
            <span class="large-spinner" aria-hidden="true"></span
            ><strong>Finding exchange rate</strong>
            <p>Checking the selected pair and date…</p>
          </div>
        }
        @case ('error') {
          @if (errorState(); as error) {
            <div
              class="result-state error-state"
              role="alert"
              [attr.data-category]="error.category"
            >
              <span class="state-icon" aria-hidden="true">!</span><strong>Rate unavailable</strong>
              <p>{{ error.message }}</p>
            </div>
          }
        }
        @case ('success') {
          @if (successValue(); as value) {
            <div class="result-content">
              <div class="rate-hero">
                <div class="pair-label">
                  <span>{{ value.fromCurrency }}</span
                  ><span class="pair-arrow" aria-hidden="true">→</span
                  ><span>{{ value.toCurrency }}</span>
                </div>
                <span class="rate-caption">Exchange rate</span>
                <div class="rate-value">{{ value.rate }}</div>
              </div>
              <div class="observation-date">
                <span class="calendar-icon" aria-hidden="true"></span>
                <div>
                  <span>Rate observation date</span><strong>{{ value.rateDate }}</strong>
                  <p>This is the market date on which this stored exchange rate was recorded.</p>
                </div>
              </div>
            </div>
          }
        }
        @default {
          <div class="result-state empty-state">
            <span class="empty-icon" aria-hidden="true">⇄</span
            ><strong>Your rate will appear here</strong>
            <p>Select two currencies and submit the form to see their exchange rate.</p>
          </div>
        }
      }
    </div>
  `,
})
export class RateLookupResult {
  readonly state = input.required<RateLookupResultState>();
  protected errorState(): LookupError | null {
    const state = this.state();
    return state.kind === 'error' ? state.error : null;
  }
  protected successValue(): ExchangeRateResponse | null {
    const state = this.state();
    return state.kind === 'success' ? state.value : null;
  }
}
