import { Component, computed, input, output } from '@angular/core';
import { RoundResult as RoundResultModel, RoundSummary } from '../../../../core/models/game.model';
import { Confetti } from '../../../../shared/components/confetti/confetti';
import { Timeline } from '../timeline/timeline';

type Verdict = 'first' | 'correct' | 'missed' | 'neutral';

interface Row {
  result: RoundResultModel;
  you: boolean;
  verdict: Verdict;
  label: string;
  /** Index of the card that just joined the timeline, when the placement was right. */
  fresh: number | null;
  miss: number | null;
  hints: number[];
}

/**
 * The reveal of one resolved round: the song, then how every player fared, each with their own
 * updated timeline. Works the same for a solo run (one row) and a full table (one row per player).
 */
@Component({
  selector: 'app-round-result',
  imports: [Timeline, Confetti],
  templateUrl: './round-result.html',
  styleUrl: './round-result.scss',
})
export class RoundResult {
  readonly summary = input.required<RoundSummary>();
  /** The player looking at this screen; null on a shared device, where the whole table looks at once. */
  readonly viewerId = input<string | null>(null);
  readonly isGameOver = input(false);
  /** Seconds until the game moves on by itself (online games); null when it waits for a click. */
  readonly countdown = input<number | null>(null);
  readonly canContinue = input(true);
  readonly continue = output<void>();

  protected readonly rows = computed<Row[]>(() => {
    const viewer = this.viewerId();
    const rows = this.summary().results.map((result): Row => {
      const verdict: Verdict = result.anchorRound ? 'first' : result.correct ? 'correct' : 'missed';
      return {
        result,
        you: result.player.id === viewer,
        verdict,
        label: verdict === 'first' ? 'First card' : verdict === 'correct' ? 'Correct' : result.submittedPosition === null ? 'Out of time' : 'Not quite',
        fresh: result.correct ? result.submittedPosition : null,
        miss: result.correct ? null : result.submittedPosition,
        hints: result.correct ? [] : result.validPositions,
      };
    });
    // The viewer's own row comes first; everyone else keeps seating order.
    return rows.sort((a, b) => Number(b.you) - Number(a.you) || a.result.player.playerOrder - b.result.player.playerOrder);
  });

  /** The row that colours the song card: the viewer's, or the only player's; neutral for a whole table. */
  protected readonly headline = computed<Row | null>(() => {
    const rows = this.rows();
    return rows.find((r) => r.you) ?? (rows.length === 1 ? rows[0] : null);
  });

  /** Worth a celebration: a real placement (not the free anchor) or a nailed guess, by whoever is looking. */
  protected readonly celebrate = computed(() => {
    const relevant = this.headline() ? [this.headline()!] : this.rows();
    return relevant.some((r) => (r.verdict === 'correct') || r.result.guessCorrect === true);
  });

  protected readonly showAllNames = computed(() => this.rows().length > 1);
}
