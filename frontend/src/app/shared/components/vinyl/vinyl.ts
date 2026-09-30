import { Component, input } from '@angular/core';

/**
 * Decorative vinyl record. The grooves rotate while {@link spinning} is true; the
 * light sheen and the centre mark deliberately do not, so it reads like a real
 * record catching the light instead of a spinning sticker.
 */
@Component({
  selector: 'app-vinyl',
  template: `
    <div
      class="vinyl"
      [class.spinning]="spinning()"
      [class.has-mark]="!!mark()"
      [style.--size.px]="size()"
      aria-hidden="true"
    >
      <div class="disc"><div class="label"></div></div>
      <span class="mark">{{ mark() }}</span>
    </div>
  `,
  styleUrl: './vinyl.scss',
})
export class Vinyl {
  readonly spinning = input(false);
  readonly size = input(120);
  /** Text shown on the record label, e.g. "?" for the mystery song. */
  readonly mark = input('');
}
