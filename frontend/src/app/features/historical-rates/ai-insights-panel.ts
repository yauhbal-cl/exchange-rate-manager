import { Component, computed, input } from '@angular/core';
import { HttpErrorResponse } from '@angular/common/http';
import type { TrendInsightResponse } from '../../api-client';
import { problemDetail } from '../../shared/problem-detail';

export interface AiInsightError {
  category: 'no-data' | 'unavailable';
  message: string;
}

const UNAVAILABLE_MESSAGE = 'AI interpretation unavailable right now. Please try again later.';

export function categorizeAiInsightError(error: unknown): AiInsightError | null {
  if (!error) {
    return null;
  }
  if (error instanceof HttpErrorResponse) {
    const detail = problemDetail(error.error);
    if (error.status === 404) {
      return { category: 'no-data', message: detail ?? UNAVAILABLE_MESSAGE };
    }
    return { category: 'unavailable', message: detail ?? UNAVAILABLE_MESSAGE };
  }
  return { category: 'unavailable', message: UNAVAILABLE_MESSAGE };
}

@Component({
  selector: 'app-ai-insights-panel',
  host: { class: 'block' },
  template: `
    <div class="panel flex min-h-full flex-col p-5 max-[720px]:p-4">
      <div class="panel-header flex items-center justify-between gap-2.5">
        <div class="title-wrap flex items-center gap-[9px]">
          <span
            class="spark grid size-[30px] place-items-center rounded-[9px] bg-[#f1f2ff] font-extrabold text-[#5b61d6]"
            aria-hidden="true"
            >✦</span
          >
          <div>
            <h2 class="m-0 text-base font-[750] text-[#18202a]">AI Insights</h2>
            <p class="mt-1 mb-0 text-xs text-[#667085]">Context from the selected period</p>
          </div>
        </div>
        <span
          class="ai-badge rounded-full bg-[#f1f2ff] px-2 py-[5px] text-[11px] font-bold text-[#4a4fb8]"
          >AI</span
        >
      </div>

      @if (isLoading()) {
        <div
          class="message loading mt-3.5 flex min-h-[58px] items-center gap-3 rounded-xl border border-[#e4e7ec] bg-[#f9fafb] p-[13px] text-[12.5px] leading-[1.55] text-[#475467]"
          role="status"
          aria-live="polite"
        >
          <span
            class="spinner size-[22px] shrink-0 animate-spin rounded-full border-[3px] border-[#dfe1ff] border-t-[#5b61d6] motion-reduce:[animation-duration:1.8s]"
            aria-hidden="true"
          ></span>
          <span>
            <strong class="block text-[13px] text-[#344054]">Generating insight</strong>
            <small class="mt-0.5 block text-xs text-[#667085]"
              >Reviewing the selected historical series…</small
            >
          </span>
        </div>
      } @else if (error(); as err) {
        <div
          class="message error mt-3.5 rounded-xl border border-[#e4e7ec] bg-[#f9fafb] p-[13px] text-[12.5px] leading-[1.55] text-[#b42318]"
          [attr.data-category]="err.category"
        >
          {{ err.message }}
        </div>
      } @else if (value(); as insight) {
        <div
          class="insight mt-3.5 rounded-xl border border-[#e4e7ec] bg-[#f9fafb] p-[13px] text-[12.5px] leading-[1.55] text-[#667085]"
        >
          <span class="mb-[7px] block text-xs font-[750] text-[#475467]"
            >Period interpretation</span
          >
          <p class="m-0 text-[13px] leading-relaxed text-[#344054]">{{ insight.narrative }}</p>
        </div>
      } @else {
        <div
          class="message empty mt-3.5 rounded-xl border border-[#e4e7ec] bg-[#f9fafb] p-[13px] text-[12.5px] leading-[1.55] text-[#667085]"
        >
          Select a valid currency pair and date range to generate an AI interpretation.
        </div>
      }
      <p class="note mt-auto mb-0 pt-4 text-[11px] leading-[1.45] text-[#98a2b3]">
        Insights are descriptive, generated from the selected historical series, and are not
        financial advice.
      </p>
    </div>
  `,
})
export class AiInsightsPanel {
  readonly value = input<TrendInsightResponse | undefined>(undefined);
  readonly isLoading = input<boolean>(false);
  readonly rawError = input<unknown>(undefined, { alias: 'error' });

  protected readonly error = computed<AiInsightError | null>(() =>
    categorizeAiInsightError(this.rawError()),
  );
}
