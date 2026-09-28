import { Component, computed, inject, signal } from '@angular/core';
import { ActivatedRoute, Router } from '@angular/router';
import { finalize } from 'rxjs';
import { GameService } from '../../core/services/game.service';
import { ApiError, Game as GameModel, Player, RoundPending, RoundResult as RoundResultModel } from '../../core/models/game.model';
import { AudioPlayer } from './components/audio-player/audio-player';
import { Timeline } from './components/timeline/timeline';
import { ScorePanel } from './components/score-panel/score-panel';
import { RoundResult } from './components/round-result/round-result';
import { LoadingState } from '../../shared/components/loading-state/loading-state';
import { ErrorState } from '../../shared/components/error-state/error-state';

type ViewState = 'loading' | 'error' | 'playing' | 'revealed';

@Component({
  selector: 'app-game',
  imports: [AudioPlayer, Timeline, ScorePanel, RoundResult, LoadingState, ErrorState],
  templateUrl: './game.html',
  styleUrl: './game.scss',
})
export class Game {
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);
  private readonly gameService = inject(GameService);

  private readonly gameId = this.route.snapshot.paramMap.get('gameId')!;

  protected readonly viewState = signal<ViewState>('loading');
  protected readonly error = signal<ApiError | null>(null);

  protected readonly game = signal<GameModel | null>(null);
  protected readonly round = signal<RoundPending | null>(null);
  protected readonly lastResult = signal<RoundResultModel | null>(null);
  protected readonly selectedPosition = signal<number | null>(null);
  protected readonly submitting = signal(false);
  protected readonly advancing = signal(false);

  protected readonly activePlayer = computed<Player | null>(() => {
    const round = this.round();
    const game = this.game();
    if (!round || !game) return null;
    return game.players.find((p) => p.id === round.playerId) ?? null;
  });

  constructor() {
    this.loadInitial();
  }

  private loadInitial(): void {
    this.viewState.set('loading');
    this.gameService.getGame(this.gameId).subscribe({
      next: (game) => {
        this.game.set(game);
        if (game.status === 'FINISHED') {
          this.router.navigate(['/game', this.gameId, 'results']);
          return;
        }
        this.loadCurrentRound();
      },
      error: (err: ApiError) => this.fail(err),
    });
  }

  private loadCurrentRound(): void {
    this.gameService.getCurrentRound(this.gameId).subscribe({
      next: (round) => {
        this.round.set(round);
        this.selectedPosition.set(null);
        this.viewState.set('playing');
      },
      error: (err: ApiError) => this.fail(err),
    });
  }

  private fail(err: ApiError): void {
    this.error.set(err);
    this.viewState.set('error');
  }

  protected retry(): void {
    this.loadInitial();
  }

  protected selectPosition(position: number): void {
    this.selectedPosition.set(position);
  }

  protected get canSubmit(): boolean {
    const round = this.round();
    if (!round) return false;
    if (round.anchorRound) return true;
    return this.selectedPosition() !== null;
  }

  protected submitAnswer(): void {
    const round = this.round();
    if (!round || !this.canSubmit || this.submitting()) return;

    this.submitting.set(true);
    const insertPosition = round.anchorRound ? null : this.selectedPosition();

    this.gameService
      .submitAnswer(this.gameId, round.roundId, { insertPosition })
      .pipe(finalize(() => this.submitting.set(false)))
      .subscribe({
        next: (result) => {
          this.lastResult.set(result);
          this.mergePlayer(result.player);
          this.viewState.set('revealed');
        },
        error: (err: ApiError) => this.fail(err),
      });
  }

  private mergePlayer(updated: Player): void {
    const game = this.game();
    if (!game) return;
    this.game.set({
      ...game,
      players: game.players.map((p) => (p.id === updated.id ? updated : p)),
    });
  }

  protected get isGameOver(): boolean {
    return this.lastResult()?.gameStatus === 'FINISHED';
  }

  protected continueAfterReveal(): void {
    if (this.isGameOver) {
      this.router.navigate(['/game', this.gameId, 'results']);
      return;
    }

    this.advancing.set(true);
    this.gameService
      .nextRound(this.gameId)
      .pipe(finalize(() => this.advancing.set(false)))
      .subscribe({
        next: (round) => {
          this.round.set(round);
          this.lastResult.set(null);
          this.selectedPosition.set(null);
          this.viewState.set('playing');
        },
        error: (err: ApiError) => this.fail(err),
      });
  }
}
