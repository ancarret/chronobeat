import { Component, computed, input } from '@angular/core';
import { Player } from '../../../../core/models/game.model';

interface Seat {
  player: Player;
  you: boolean;
  /** 0..1 progress towards the goal, or null when there is no goal. */
  progress: number | null;
  /** Shown as the seat's status: who we're still waiting on, or who already locked in. */
  status: 'thinking' | 'locked' | 'out' | null;
  offline: boolean;
}

/**
 * The table at a glance: everyone's card count (as a race to the goal when there is one), lives and,
 * while a shared-song round is open, who has locked in and who is still thinking.
 */
@Component({
  selector: 'app-player-strip',
  templateUrl: './player-strip.html',
  styleUrl: './player-strip.scss',
})
export class PlayerStrip {
  readonly players = input.required<Player[]>();
  /** Cards needed to win, or null when the game has no goal. */
  readonly target = input<number | null>(null);
  readonly viewerId = input<string | null>(null);
  /** Players whose answer is still missing; only meaningful while a round is open. */
  readonly waitingOn = input<string[]>([]);
  /** Online players with a live connection; null hides the indicator (shared-device games). */
  readonly connectedIds = input<string[] | null>(null);
  /** Whether "locked in / thinking" statuses apply (a shared-song round in progress). */
  readonly showRoundStatus = input(false);

  protected readonly seats = computed<Seat[]>(() =>
    this.players().map((player) => {
      const connected = this.connectedIds();
      let status: Seat['status'] = null;
      if (player.eliminated) {
        status = 'out';
      } else if (this.showRoundStatus()) {
        status = player.answered ? 'locked' : this.waitingOn().includes(player.id) ? 'thinking' : null;
      }
      const target = this.target();
      return {
        player,
        you: player.id === this.viewerId(),
        progress: target ? Math.min(1, player.timelineSize / target) : null,
        status,
        offline: connected !== null && !connected.includes(player.id),
      };
    }),
  );
}
