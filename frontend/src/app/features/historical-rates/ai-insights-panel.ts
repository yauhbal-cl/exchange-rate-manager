import { Component, computed, input, output } from '@angular/core';
import { HttpErrorResponse } from '@angular/common/http';
import type { TrendInsightResponse } from '../../api-client';

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
    const detail = (error.error as { detail?: string } | null)?.detail;
    if (error.status === 404) {
      return { category: 'no-data', message: detail ?? UNAVAILABLE_MESSAGE };
    }
    return { category: 'unavailable', message: detail ?? UNAVAILABLE_MESSAGE };
  }
  return { category: 'unavailable', message: UNAVAILABLE_MESSAGE };
}

@Component({
  selector: 'app-ai-insights-panel',
  template: `
    <div class="rounded border border-gray-200 p-4">
      <h3 class="text-lg font-semibold text-gray-900">AI Insights</h3>

      <button
        type="button"
        class="mt-3 rounded bg-blue-600 px-3 py-1.5 text-sm text-white disabled:opacity-50"
        [disabled]="!canGenerate() || isLoading()"
        (click)="generate.emit()"
      >
        Generate insight
      </button>

      @if (isLoading()) {
        <p class="mt-3 text-gray-600">Generating insight…</p>
      } @else if (error(); as err) {
        <p class="mt-3 text-red-600" [attr.data-category]="err.category">{{ err.message }}</p>
      } @else if (value(); as insight) {
        <p class="mt-3 text-gray-800">{{ insight.narrative }}</p>
      }
    </div>
  `,
})
export class AiInsightsPanel {
  readonly value = input<TrendInsightResponse | undefined>(undefined);
  readonly isLoading = input<boolean>(false);
  readonly rawError = input<unknown>(undefined, { alias: 'error' });
  readonly canGenerate = input<boolean>(false);
  readonly generate = output<void>();

  protected readonly error = computed<AiInsightError | null>(() =>
    categorizeAiInsightError(this.rawError()),
  );
}
