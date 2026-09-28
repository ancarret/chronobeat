import { Component, input } from '@angular/core';

@Component({
  selector: 'app-loading-state',
  template: `
    <div class="loading">
      <div class="spinner" aria-hidden="true"></div>
      <p>{{ message() }}</p>
    </div>
  `,
  styles: [
    `
      .loading {
        display: flex;
        flex-direction: column;
        align-items: center;
        gap: 16px;
        padding: 64px 20px;
        color: var(--text-muted);
      }

      .spinner {
        width: 40px;
        height: 40px;
        border-radius: 50%;
        border: 3px solid var(--border);
        border-top-color: var(--brand-primary);
        animation: spin 0.8s linear infinite;
      }

      @keyframes spin {
        to {
          transform: rotate(360deg);
        }
      }
    `,
  ],
})
export class LoadingState {
  readonly message = input('Loading…');
}
