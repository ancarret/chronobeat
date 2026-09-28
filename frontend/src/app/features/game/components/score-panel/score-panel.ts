import { Component, input } from '@angular/core';
import { Player } from '../../../../core/models/game.model';

@Component({
  selector: 'app-score-panel',
  templateUrl: './score-panel.html',
  styleUrl: './score-panel.scss',
})
export class ScorePanel {
  readonly player = input.required<Player>();
  readonly roundNumber = input.required<number>();

  protected livesArray(): number[] {
    return Array.from({ length: this.player().livesRemaining });
  }
}
