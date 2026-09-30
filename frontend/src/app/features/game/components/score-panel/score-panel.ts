import { Component, input } from '@angular/core';
import { Player } from '../../../../core/models/game.model';

/** A streak this long or longer earns the flame icon. */
const HOT_STREAK = 3;

@Component({
  selector: 'app-score-panel',
  templateUrl: './score-panel.html',
  styleUrl: './score-panel.scss',
})
export class ScorePanel {
  readonly player = input.required<Player>();
  readonly roundNumber = input.required<number>();
  /** Starting lives; lost ones are drawn as empty hearts. Bonus lives can push the total above it. */
  readonly maxLives = input(0);

  protected isHot(): boolean {
    return this.player().currentStreak >= HOT_STREAK;
  }

  /** One entry per heart slot: true = life remaining, false = life lost. */
  protected hearts(): boolean[] {
    const lives = this.player().livesRemaining;
    const total = Math.max(this.maxLives(), lives);
    return Array.from({ length: total }, (_, i) => i < lives);
  }
}
