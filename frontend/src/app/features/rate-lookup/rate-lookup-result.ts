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
  host: { class: 'block min-w-0' },
  template: `
    <div
      class="card result-card flex min-h-[430px] min-w-0 flex-col rounded-2xl border border-[var(--app-border)] bg-[var(--app-surface)] p-6 shadow-[var(--app-card-shadow)] max-[900px]:min-h-[380px] max-[640px]:px-4 max-[640px]:py-[18px]"
      aria-live="polite"
    >
      <div class="result-header border-b border-[var(--app-border)] pb-[18px]">
        <div>
          <h2 class="m-0 text-base font-[750]">Exchange rate</h2>
          <p class="mt-1 mb-0 text-xs text-[var(--app-muted)]">Your lookup result</p>
        </div>
      </div>
      @switch (state().kind) {
        @case ('loading') {
          <div
            class="result-state loading-state flex min-h-[280px] flex-1 flex-col items-center justify-center p-[30px] text-center"
            role="status"
          >
            <span
              class="large-spinner size-[34px] animate-spin rounded-full border-2 border-[#e4e7ec] border-t-[var(--app-accent)]"
              aria-hidden="true"
            ></span
            ><strong class="mt-3.5 text-sm text-[#344054]">Finding exchange rate</strong>
            <p
              class="mt-[7px] mb-0 max-w-[310px] text-[12.5px] leading-normal text-[var(--app-muted)]"
            >
              Checking the selected pair and date…
            </p>
          </div>
        }
        @case ('error') {
          @if (errorState(); as error) {
            <div
              class="result-state error-state flex min-h-[280px] flex-1 flex-col items-center justify-center p-[30px] text-center"
              role="alert"
              [attr.data-category]="error.category"
            >
              <span
                class="state-icon grid size-11 place-items-center rounded-[13px] bg-[#fef3f2] text-[21px] font-[750] text-[#b42318]"
                aria-hidden="true"
                >!</span
              ><strong class="mt-3.5 text-sm text-[#344054]">Rate unavailable</strong>
              <p
                class="mt-[7px] mb-0 max-w-[310px] text-[12.5px] leading-normal text-[var(--app-muted)]"
              >
                {{ error.message }}
              </p>
            </div>
          }
        }
        @case ('success') {
          @if (successValue(); as value) {
            <div class="result-content pt-6">
              <div
                class="rate-hero relative overflow-hidden rounded-[14px] border border-[#dedffc] bg-[linear-gradient(145deg,#fafaff_0%,var(--app-accent-soft)_100%)] p-5 [container-type:inline-size] after:absolute after:top-[-34px] after:right-[-28px] after:size-[110px] after:rounded-full after:bg-[rgba(91,97,214,0.07)] after:content-['']"
              >
                <div class="pair-label relative z-1 flex items-center gap-2">
                  <span
                    class="rounded-lg border border-[#d8daf8] bg-[rgba(255,255,255,0.8)] px-2 py-[5px] text-xs font-[750] tracking-[0.04em] text-[#4449b5]"
                    >{{ value.fromCurrency }}</span
                  ><span class="pair-arrow text-[13px] text-[#7f83ca]" aria-hidden="true">→</span
                  ><span
                    class="rounded-lg border border-[#d8daf8] bg-[rgba(255,255,255,0.8)] px-2 py-[5px] text-xs font-[750] tracking-[0.04em] text-[#4449b5]"
                    >{{ value.toCurrency }}</span
                  >
                </div>
                <span
                  class="rate-caption relative z-1 mt-5 block text-[11px] font-[650] text-[var(--app-muted)]"
                  >Exchange rate</span
                >
                <div
                  class="rate-value relative z-1 mt-1 overflow-hidden text-[clamp(18px,8.5cqw,36px)] leading-[1.08] font-[760] tracking-[-0.035em] text-ellipsis whitespace-nowrap text-[#18202a]"
                >
                  {{ value.rate }}
                </div>
              </div>
              <div
                class="observation-date mt-3.5 flex gap-3 rounded-xl border border-[var(--app-border)] bg-[var(--app-surface-subtle)] p-3.5"
              >
                <span
                  class="calendar-icon grid size-8 shrink-0 place-items-center rounded-[9px] bg-[var(--app-accent-soft)] text-[var(--app-accent)] before:h-[13px] before:w-3.5 before:rounded-[3px] before:border-[1.5px] before:border-current before:bg-[linear-gradient(currentColor,currentColor)] before:bg-[length:100%_1.5px] before:bg-[position:0_3px] before:bg-no-repeat before:content-['']"
                  aria-hidden="true"
                ></span>
                <div>
                  <span
                    class="block text-[10px] font-bold tracking-[0.05em] text-[var(--app-muted)] uppercase"
                    >Rate observation date</span
                  ><strong class="mt-[3px] block text-sm text-[#344054]">{{
                    value.rateDate
                  }}</strong>
                  <p class="mt-[5px] mb-0 text-[11.5px] leading-[1.45] text-[var(--app-muted)]">
                    This is the market date on which this stored exchange rate was recorded.
                  </p>
                </div>
              </div>
            </div>
          }
        }
        @default {
          <div
            class="result-state empty-state flex min-h-[280px] flex-1 flex-col items-center justify-center p-[30px] text-center"
          >
            <span
              class="empty-icon grid size-11 place-items-center rounded-[13px] bg-[var(--app-accent-soft)] text-[21px] font-[750] text-[var(--app-accent)]"
              aria-hidden="true"
              >⇄</span
            ><strong class="mt-3.5 text-sm text-[#344054]">Your rate will appear here</strong>
            <p
              class="mt-[7px] mb-0 max-w-[310px] text-[12.5px] leading-normal text-[var(--app-muted)]"
            >
              Select two currencies and submit the form to see their exchange rate.
            </p>
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
