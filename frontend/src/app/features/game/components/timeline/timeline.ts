import { Component, input, output } from '@angular/core';
import { TimelineEntry } from '../../../../core/models/game.model';
import { Vinyl } from '../../../../shared/components/vinyl/vinyl';

/**
 * Renders a chronological timeline. In interactive mode it also renders a
 * clickable insertion slot before/between/after every entry (index 0..N),
 * which is how placement works on touch devices without relying on
 * drag-and-drop (see README "Timeline interaction").
 *
 * Non-interactive timelines can additionally decorate the reveal: the entry that
 * was just added ({@link freshPosition}), the slot the player wrongly picked
 * ({@link missPosition}) and the slot(s) where the song really belonged
 * ({@link hintPositions}).
 */
@Component({
  selector: 'app-timeline',
  imports: [Vinyl],
  templateUrl: './timeline.html',
  styleUrl: './timeline.scss',
})
export class Timeline {
  readonly entries = input.required<TimelineEntry[]>();
  readonly interactive = input(false);
  readonly selectedPosition = input<number | null>(null);
  readonly freshPosition = input<number | null>(null);
  readonly missPosition = input<number | null>(null);
  readonly hintPositions = input<number[]>([]);
  readonly positionSelected = output<number>();

  select(position: number, event?: Event): void {
    if (!this.interactive()) {
      return;
    }
    this.positionSelected.emit(position);
    // The chosen slot grows into a full-size ghost card; keep it in view on narrow screens.
    (event?.currentTarget as HTMLElement | null)?.scrollIntoView?.({ behavior: 'smooth', inline: 'center', block: 'nearest' });
  }

  protected slotIndices(): number[] {
    return Array.from({ length: this.entries().length + 1 }, (_, i) => i);
  }
}
