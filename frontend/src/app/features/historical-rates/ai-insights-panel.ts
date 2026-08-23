import { Component, computed, input } from '@angular/core';
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

      @if (isLoading()) {
        <div class="message loading" role="status" aria-live="polite">
          <span class="spinner" aria-hidden="true"></span>
          <span>
            <strong>Generating insight</strong>
            <small>Reviewing the selected historical series…</small>
          </span>
        </div>
      } @else if (error(); as err) {
        <div class="message error" [attr.data-category]="err.category">{{ err.message }}</div>
      } @else if (value(); as insight) {
        <div class="insight">
          <span>Period interpretation</span>
          <p>{{ insight.narrative }}</p>
        </div>
      } @else {
        <div class="message empty">
          Select a valid currency pair and date range to generate an AI interpretation.
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

  protected readonly error = computed<AiInsightError | null>(() =>
    categorizeAiInsightError(this.rawError()),
  );
}
