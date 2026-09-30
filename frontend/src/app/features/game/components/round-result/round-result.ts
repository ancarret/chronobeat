import { Component, computed, input, output } from '@angular/core';
import { RoundResult as RoundResultModel } from '../../../../core/models/game.model';
import { Confetti } from '../../../../shared/components/confetti/confetti';
import { Timeline } from '../timeline/timeline';

@Component({
  selector: 'app-round-result',
  imports: [Timeline, Confetti],
  templateUrl: './round-result.html',
  styleUrl: './round-result.scss',
})
export class RoundResult {
  readonly result = input.required<RoundResultModel>();
  readonly isGameOver = input(false);
  readonly continue = output<void>();

  /** Worth a celebration: a real placement (not the free anchor) or a nailed guess. */
  protected readonly celebrate = computed(() => {
    const r = this.result();
    return (r.correct && !r.anchorRound) || r.guessCorrect === true;
  });

  /** Index of the card that just joined the timeline, when the placement was right. */
  protected readonly freshPosition = computed(() => {
    const r = this.result();
    return r.correct ? r.submittedPosition : null;
  });

  /** On a miss: where the player put it, and where it really belonged. */
  protected readonly missPosition = computed(() => {
    const r = this.result();
    return r.correct ? null : r.submittedPosition;
  });

  protected readonly hintPositions = computed(() => {
    const r = this.result();
    return r.correct ? [] : r.validPositions;
  });
}
