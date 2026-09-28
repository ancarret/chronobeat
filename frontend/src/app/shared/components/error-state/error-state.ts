import { Component, input, output } from '@angular/core';

@Component({
  selector: 'app-error-state',
  template: `
    <div class="error-state" role="alert">
      <div class="icon" aria-hidden="true">♪</div>
      <h3>{{ title() }}</h3>
      <p>{{ message() }}</p>
      @if (retryLabel()) {
        <button class="btn btn-primary" type="button" (click)="retry.emit()">{{ retryLabel() }}</button>
      }
    </div>
  `,
  styles: [
    `
      .error-state {
        display: flex;
        flex-direction: column;
        align-items: center;
        text-align: center;
        gap: 10px;
        padding: 48px 24px;
        color: var(--text-muted);
      }

      .icon {
        width: 56px;
        height: 56px;
        display: flex;
        align-items: center;
        justify-content: center;
        border-radius: 50%;
        background: var(--danger-bg);
        color: var(--danger);
        font-size: 1.5rem;
        margin-bottom: 8px;
      }

      h3 {
        color: var(--text);
        font-size: 1.1rem;
      }

      p {
        max-width: 40ch;
      }

      button {
        margin-top: 12px;
      }
    `,
  ],
})
export class ErrorState {
  readonly title = input('Something went wrong');
  readonly message = input('Please try again.');
  readonly retryLabel = input<string | null>('Try again');
  readonly retry = output<void>();
}
