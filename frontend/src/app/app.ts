import { Component, OnInit, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { StatusService } from './api-client';
import { environment } from '../environments/environment';

@Component({
  selector: 'app-root',
  standalone: true,
  imports: [CommonModule],
  template: `
    <div class="min-h-screen bg-gray-50">
      <header class="bg-white shadow">
        <div class="max-w-7xl mx-auto py-6 px-4">
          <h1 class="text-3xl font-bold text-gray-900">Exchange Rate Management System</h1>
        </div>
      </header>
      <main class="max-w-7xl mx-auto py-12 px-4">
        <div class="bg-white rounded-lg shadow p-6">
          <h2 class="text-xl font-semibold text-gray-900 mb-4">Service Status</h2>
          <div *ngIf="!loading && status" class="space-y-4">
            <div class="flex items-center gap-3">
              <span class="text-lg font-medium text-gray-700">Status:</span>
              <span [class]="'px-3 py-1 rounded-full text-white font-medium ' + (status.status === 'UP' ? 'bg-green-500' : 'bg-red-500')">
                {{ status.status }}
              </span>
            </div>
            <div class="flex items-center gap-3">
              <span class="text-lg font-medium text-gray-700">Database:</span>
              <span [class]="'px-3 py-1 rounded-full text-white font-medium ' + (status.databaseConnected ? 'bg-green-500' : 'bg-red-500')">
                {{ status.databaseConnected ? 'Connected' : 'Disconnected' }}
              </span>
            </div>
            <div class="flex items-center gap-3">
              <span class="text-lg font-medium text-gray-700">Timestamp:</span>
              <span class="text-gray-600">{{ status.timestamp | date:'medium' }}</span>
            </div>
          </div>
          <div *ngIf="loading" class="text-center text-gray-600">
            Loading...
          </div>
          <div *ngIf="error" class="text-center text-red-600">
            Error: {{ error }}
          </div>
        </div>
      </main>
    </div>
  `,
})
export class App implements OnInit {
  private statusService = inject(StatusService);
  loading = true;
  error: string | null = null;
  status: any = null;

  ngOnInit() {
    this.statusService.getStatus().subscribe({
      next: (response) => {
        this.status = response;
        this.loading = false;
      },
      error: (error) => {
        this.error = error.message || 'Failed to fetch status';
        this.loading = false;
      },
    });
  }
}
