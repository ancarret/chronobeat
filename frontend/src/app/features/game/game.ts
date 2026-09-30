import { Component, computed, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, Router } from '@angular/router';
import { Subject, debounceTime, distinctUntilChanged, finalize, of, switchMap } from 'rxjs';
import { GameService } from '../../core/services/game.service';
import {
  ApiError,
  Game as GameModel,
  Player,
  RoundPending,
  RoundResult as RoundResultModel,
  SongSearchResult,
} from '../../core/models/game.model';
import { AudioPlayer } from './components/audio-player/audio-player';
import { Timeline } from './components/timeline/timeline';
import { ScorePanel } from './components/score-panel/score-panel';
import { RoundResult } from './components/round-result/round-result';
import { LoadingState } from '../../shared/components/loading-state/loading-state';
import { ErrorState } from '../../shared/components/error-state/error-state';

type ViewState = 'loading' | 'error' | 'playing' | 'revealed';

const MIN_GUESS_QUERY_LENGTH = 3;

@Component({
  selector: 'app-game',
  imports: [FormsModule, AudioPlayer, Timeline, ScorePanel, RoundResult, LoadingState, ErrorState],
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

  protected readonly showGuessPanel = signal(false);
  protected readonly guessQuery = signal('');
  protected readonly guessResults = signal<SongSearchResult[]>([]);
  protected readonly searchingGuess = signal(false);
  protected readonly selectedGuessSong = signal<SongSearchResult | null>(null);
  protected readonly guessYear = signal<number | null>(null);

  private readonly guessQuery$ = new Subject<string>();

  protected readonly activePlayer = computed<Player | null>(() => {
    const round = this.round();
    const game = this.game();
    if (!round || !game) return null;
    return game.players.find((p) => p.id === round.playerId) ?? null;
  });

  constructor() {
    this.loadInitial();

    this.guessQuery$
      .pipe(
        debounceTime(250),
        distinctUntilChanged(),
        switchMap((query) => {
          if (query.trim().length < MIN_GUESS_QUERY_LENGTH) {
            this.searchingGuess.set(false);
            return of([]);
          }
          this.searchingGuess.set(true);
          return this.gameService.searchSongs(query.trim()).pipe(finalize(() => this.searchingGuess.set(false)));
        }),
      )
      .subscribe((results) => this.guessResults.set(results));
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
        this.resetGuessState();
        this.viewState.set('playing');
      },
      error: (err: ApiError) => this.fail(err),
    });
  }

  private resetGuessState(): void {
    this.showGuessPanel.set(false);
    this.guessQuery.set('');
    this.guessResults.set([]);
    this.searchingGuess.set(false);
    this.selectedGuessSong.set(null);
    this.guessYear.set(null);
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
    const guessedSong = this.selectedGuessSong();

    this.gameService
      .submitAnswer(this.gameId, round.roundId, {
        insertPosition,
        guessedSongId: guessedSong?.id ?? null,
        guessedYear: this.guessYear(),
      })
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

  protected toggleGuessPanel(): void {
    this.showGuessPanel.set(!this.showGuessPanel());
  }

  protected onGuessQueryChange(value: string): void {
    this.guessQuery.set(value);
    this.guessQuery$.next(value);
  }

  protected selectGuessSong(song: SongSearchResult): void {
    this.selectedGuessSong.set(song);
    this.guessQuery.set('');
    this.guessResults.set([]);
  }

  protected clearGuessSong(): void {
    this.selectedGuessSong.set(null);
  }

  protected onGuessYearChange(value: number | null): void {
    this.guessYear.set(value);
  }

  protected get guessComplete(): boolean {
    return this.selectedGuessSong() !== null && this.guessYear() !== null;
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
          this.resetGuessState();
          this.viewState.set('playing');
        },
        error: (err: ApiError) => this.fail(err),
      });
  }
}
