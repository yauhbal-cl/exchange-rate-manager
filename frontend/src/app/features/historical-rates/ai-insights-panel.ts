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
  styleUrl: './ai-insights-panel.css',
  template: `
    <div class="panel">
      <div class="panel-header">
        <div class="title-wrap">
          <span class="spark" aria-hidden="true">✦</span>
          <div>
            <h2>AI Insights</h2>
            <p>Context from the selected period</p>
          </div>
        </div>
        <span class="ai-badge">AI</span>
      </div>

      <button
        type="button"
        class="generate-button"
        [disabled]="!canGenerate() || isLoading()"
        (click)="generate.emit()"
      >
        Generate insight
      </button>

      @if (isLoading()) {
        <div class="message loading">Generating insight…</div>
      } @else if (error(); as err) {
        <div class="message error" [attr.data-category]="err.category">{{ err.message }}</div>
      } @else if (value(); as insight) {
        <div class="insight">
          <span>Period interpretation</span>
          <p>{{ insight.narrative }}</p>
        </div>
      } @else {
        <div class="message empty">
          Generate an AI interpretation of the visible historical series.
        </div>
      }
      <p class="note">
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
  readonly canGenerate = input<boolean>(false);
  readonly generate = output<void>();

  protected readonly error = computed<AiInsightError | null>(() =>
    categorizeAiInsightError(this.rawError()),
  );
}
