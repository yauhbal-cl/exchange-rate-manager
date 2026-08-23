import { Component } from '@angular/core';

@Component({
  selector: 'app-not-found',
  template: `
    <div class="mx-auto max-w-2xl px-4 py-16 text-center">
      <h2 class="text-2xl font-semibold text-gray-900">Page not found</h2>
      <p class="mt-2 text-gray-600">The address you navigated to doesn't match any known view.</p>
    </div>
  `,
})
export class NotFound {}
