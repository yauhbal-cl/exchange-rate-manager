import { Routes } from '@angular/router';

export const routes: Routes = [
  { path: '', pathMatch: 'full', redirectTo: 'rate-lookup' },
  {
    path: 'rate-lookup',
    loadComponent: () => import('./features/rate-lookup/rate-lookup').then((m) => m.RateLookup),
  },
  {
    path: 'usage-analytics',
    loadComponent: () =>
      import('./features/usage-analytics/usage-analytics').then((m) => m.UsageAnalytics),
  },
  {
    path: 'ai-insight',
    loadComponent: () => import('./features/ai-insight/ai-insight').then((m) => m.AiInsight),
  },
  {
    path: 'historical-rates',
    loadComponent: () =>
      import('./features/historical-rates/historical-rates').then((m) => m.HistoricalRates),
  },
  { path: '**', loadComponent: () => import('./not-found/not-found').then((m) => m.NotFound) },
];
