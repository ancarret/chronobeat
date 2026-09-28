import { Component, input, output } from '@angular/core';
import { TimelineEntry } from '../../../../core/models/game.model';

/**
 * Renders a chronological timeline. In interactive mode it also renders a
 * clickable insertion slot before/between/after every entry (index 0..N),
 * which is how placement works on touch devices without relying on
 * drag-and-drop (see README "Timeline interaction").
 */
@Component({
  selector: 'app-timeline',
  templateUrl: './timeline.html',
  styleUrl: './timeline.scss',
})
export class Timeline {
  readonly entries = input.required<TimelineEntry[]>();
  readonly interactive = input(false);
  readonly selectedPosition = input<number | null>(null);
  readonly positionSelected = output<number>();

  select(position: number): void {
    if (this.interactive()) {
      this.positionSelected.emit(position);
    }
  }

  protected slotIndices(): number[] {
    return Array.from({ length: this.entries().length + 1 }, (_, i) => i);
  }
}
