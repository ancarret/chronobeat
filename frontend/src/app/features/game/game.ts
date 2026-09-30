import { Component, DestroyRef, computed, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, Router } from '@angular/router';
import {
  EMPTY,
  Subject,
  Subscription,
  catchError,
  debounceTime,
  distinctUntilChanged,
  finalize,
  interval,
  of,
  switchMap,
  timer,
} from 'rxjs';
import { GameEventsService } from '../../core/services/game-events.service';
import { GameService } from '../../core/services/game.service';
import { OnlineSessionService } from '../../core/services/online-session.service';
import { ApiError, GameState, Player, SongSearchResult, TimelineEntry } from '../../core/models/game.model';
import { AudioPlayer } from './components/audio-player/audio-player';
import { PlayerStrip } from './components/player-strip/player-strip';
import { RoundResult } from './components/round-result/round-result';
import { ScorePanel } from './components/score-panel/score-panel';
import { Timeline } from './components/timeline/timeline';
import { ErrorState } from '../../shared/components/error-state/error-state';
import { LoadingState } from '../../shared/components/loading-state/loading-state';
import { Vinyl } from '../../shared/components/vinyl/vinyl';

type Screen = 'loading' | 'error' | 'pass' | 'answering' | 'waiting' | 'reveal';

const MIN_GUESS_QUERY_LENGTH = 3;

/** Online reveals move on by themselves after this long, so one absent player can't hold the table. */
const REVEAL_AUTO_ADVANCE_SECONDS = 12;

/** Online reveals can't be skipped before this many seconds (the server enforces the same rule). */
const REVEAL_MIN_SECONDS = 3;

@Component({
  selector: 'app-game',
  imports: [FormsModule, AudioPlayer, Timeline, ScorePanel, PlayerStrip, RoundResult, Vinyl, LoadingState, ErrorState],
  templateUrl: './game.html',
  styleUrl: './game.scss',
})
export class Game {
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);
  private readonly gameService = inject(GameService);
  private readonly events = inject(GameEventsService);
  private readonly sessions = inject(OnlineSessionService);
  private readonly destroyRef = inject(DestroyRef);

  private readonly gameId = this.route.snapshot.paramMap.get('gameId')!;
  /** This browser's seat, when the game is online. */
  private readonly seat = this.sessions.forGame(this.gameId);

  protected readonly state = signal<GameState | null>(null);
  protected readonly error = signal<ApiError | null>(null);

  protected readonly selectedPosition = signal<number | null>(null);
  protected readonly submitting = signal(false);
  protected readonly advancing = signal(false);
  /** Mirrors the audio player so the mystery record only spins while the preview plays. */
  protected readonly audioPlaying = signal(false);

  /** Shared-device games hand the screen from player to player; this is the round whose turn was accepted. */
  protected readonly acknowledgedRoundId = signal<string | null>(null);
  protected readonly secondsLeft = signal<number | null>(null);
  protected readonly revealSeconds = signal(0);
  /** Timeline of the player everyone is waiting on, shown to spectators of a turn-based online game. */
  protected readonly watchedTimeline = signal<TimelineEntry[]>([]);

  protected readonly showGuessPanel = signal(false);
  protected readonly guessQuery = signal('');
  protected readonly guessResults = signal<SongSearchResult[]>([]);
  protected readonly searchingGuess = signal(false);
  protected readonly selectedGuessSong = signal<SongSearchResult | null>(null);
  protected readonly guessYear = signal<number | null>(null);

  private readonly guessQuery$ = new Subject<string>();
  private readonly refresh$ = new Subject<void>();
  private watchingEvents = false;
  private lastRoundId: string | null = null;
  private countdown?: Subscription;
  private revealTimer?: Subscription;
  private watchedPlayerId: string | null = null;
  private timedRevealRound: number | null = null;

  protected readonly game = computed(() => this.state()?.game ?? null);
  protected readonly round = computed(() => this.state()?.round ?? null);
  protected readonly summary = computed(() => this.state()?.summary ?? null);
  protected readonly isOnline = computed(() => this.game()?.mode === 'ONLINE_MULTIPLAYER');
  protected readonly isLocalTable = computed(() => this.game()?.mode === 'LOCAL_MULTIPLAYER');
  protected readonly isShared = computed(() => this.game()?.settings.playStyle === 'SHARED_SONGS');
  protected readonly isTable = computed(() => (this.game()?.players.length ?? 0) > 1);
  protected readonly target = computed(() => this.game()?.settings.targetTimelineSize ?? null);

  /** The player this screen is about: the viewer online, whoever must act on a shared device. */
  protected readonly viewer = computed<Player | null>(() => {
    const id = this.state()?.viewerPlayerId;
    return this.game()?.players.find((p) => p.id === id) ?? null;
  });

  protected readonly waitingOn = computed(() => {
    const ids = this.state()?.waitingOnPlayerIds ?? [];
    return (this.game()?.players ?? []).filter((p) => ids.includes(p.id));
  });

  protected readonly isGameOver = computed(() => this.state()?.phase === 'FINISHED');

  /** "Bea", "Bea and Cy", "Bea, Cy and Dee": who the table is waiting on, for the waiting screen. */
  protected readonly waitingNames = computed(() => {
    const names = this.waitingOn().map((p) => p.displayName);
    return names.length <= 1 ? (names[0] ?? 'someone') : `${names.slice(0, -1).join(', ')} and ${names[names.length - 1]}`;
  });

  /** How much of the round's time limit is left, as a percentage for the clock bar. */
  protected readonly clockPercent = computed(() => {
    const limit = this.game()?.settings.answerSeconds;
    const left = this.secondsLeft();
    return limit && left !== null ? Math.max(0, Math.min(100, (left / limit) * 100)) : 0;
  });

  protected readonly screen = computed<Screen>(() => {
    if (this.error()) return 'error';
    const state = this.state();
    if (!state) return 'loading';
    switch (state.phase) {
      case 'ANSWERING':
        return this.isLocalTable() && this.acknowledgedRoundId() !== state.round?.roundId ? 'pass' : 'answering';
      case 'WAITING':
        return 'waiting';
      case 'REVEAL':
      case 'FINISHED':
        return state.summary ? 'reveal' : 'loading';
      default:
        return 'loading'; // LOBBY: the redirect below is on its way
    }
  });

  constructor() {
    this.refresh$
      .pipe(
        // A newer request supersedes an older one still in flight, so a slow reply can't overwrite fresher state.
        switchMap(() =>
          this.gameService.getState(this.gameId, this.seat?.playerId).pipe(
            catchError((err: ApiError) => {
              this.fail(err);
              return EMPTY;
            }),
          ),
        ),
        takeUntilDestroyed(),
      )
      .subscribe((state) => this.apply(state));

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
        takeUntilDestroyed(),
      )
      .subscribe((results) => this.guessResults.set(results));

    this.destroyRef.onDestroy(() => {
      this.countdown?.unsubscribe();
      this.revealTimer?.unsubscribe();
    });

    this.refresh();
  }

  // ------------------------------------------------------------------ state

  private refresh(): void {
    this.refresh$.next();
  }

  private apply(state: GameState): void {
    this.error.set(null);

    if (state.phase === 'LOBBY') {
      this.router.navigate(['/room', this.gameId], { replaceUrl: true });
      return;
    }
    if (state.phase === 'FINISHED' && !state.summary) {
      this.router.navigate(['/game', this.gameId, 'results'], { replaceUrl: true });
      return;
    }

    if (state.round?.roundId !== this.lastRoundId) {
      this.lastRoundId = state.round?.roundId ?? null;
      this.selectedPosition.set(null);
      this.resetGuessState();
    }

    this.state.set(state);
    this.watchLiveEventsOnce(state);
    this.startRoundClock(state);
    this.trackSpectatedPlayer(state);
    this.manageRevealTimer(state);
  }

  /** Online tables are told the moment anything changes; a shared device has nobody else to hear from. */
  private watchLiveEventsOnce(state: GameState): void {
    if (this.watchingEvents || state.game.mode !== 'ONLINE_MULTIPLAYER') return;
    this.watchingEvents = true;
    this.events
      .watch(this.gameId, this.seat?.playerId)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe(() => this.refresh());
  }

  /** Counts the round clock down locally from the server's figure, and re-checks state as it runs out. */
  private startRoundClock(state: GameState): void {
    this.countdown?.unsubscribe();
    const remaining = state.answerSecondsRemaining;
    if (remaining === null || (state.phase !== 'ANSWERING' && state.phase !== 'WAITING')) {
      this.secondsLeft.set(null);
      return;
    }
    this.secondsLeft.set(remaining);
    this.countdown = interval(1000).subscribe(() => {
      const next = Math.max(0, (this.secondsLeft() ?? 0) - 1);
      this.secondsLeft.set(next);
      if (next === 0) {
        this.countdown?.unsubscribe();
        // The server times players out a moment after zero; ask again once it has had the chance.
        timer(2500).pipe(takeUntilDestroyed(this.destroyRef)).subscribe(() => this.refresh());
      }
    });
  }

  /** A spectator of a turn-based online game gets to watch the active player's timeline grow. */
  private trackSpectatedPlayer(state: GameState): void {
    const target = state.phase === 'WAITING' && !this.isSharedIn(state) ? (state.waitingOnPlayerIds[0] ?? null) : null;
    if (target === this.watchedPlayerId) return;
    this.watchedPlayerId = target;
    this.watchedTimeline.set([]);
    if (target) {
      this.gameService
        .getTimeline(this.gameId, target)
        .pipe(takeUntilDestroyed(this.destroyRef))
        .subscribe({ next: (timeline) => this.watchedTimeline.set(timeline), error: () => undefined });
    }
  }

  private isSharedIn(state: GameState): boolean {
    return state.game.settings.playStyle === 'SHARED_SONGS';
  }

  /**
   * Online: moves the table on after a pause, and drives the "next round in Ns" hint and the skip lock.
   * Keyed by the round on show, so the many events that arrive during a reveal (someone connecting,
   * a duplicate refresh) can't keep restarting the clock.
   */
  private manageRevealTimer(state: GameState): void {
    const revealedRound = state.game.mode === 'ONLINE_MULTIPLAYER' && state.phase === 'REVEAL' ? (state.summary?.roundNumber ?? null) : null;
    if (revealedRound === this.timedRevealRound) return;

    this.timedRevealRound = revealedRound;
    this.revealTimer?.unsubscribe();
    this.revealSeconds.set(0);
    if (revealedRound === null) return;

    this.revealTimer = interval(1000)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe((tick) => {
        this.revealSeconds.set(tick + 1);
        if (tick + 1 >= REVEAL_AUTO_ADVANCE_SECONDS) {
          this.revealTimer?.unsubscribe();
          this.continueAfterReveal();
        }
      });
  }

  protected readonly revealCountdown = computed(() =>
    this.isOnline() && this.state()?.phase === 'REVEAL' ? Math.max(0, REVEAL_AUTO_ADVANCE_SECONDS - this.revealSeconds()) : null,
  );

  /**
   * The skip lock only exists to give slower readers a moment before the *next round* is dealt. The final
   * reveal has no next round to protect, and nothing advances on its own there, so it must never stay locked.
   */
  protected readonly canSkipReveal = computed(
    () => !this.isOnline() || this.isGameOver() || this.revealSeconds() >= REVEAL_MIN_SECONDS,
  );

  private fail(err: ApiError): void {
    this.error.set(err);
  }

  protected retry(): void {
    this.error.set(null);
    this.refresh();
  }

  // ------------------------------------------------------------------ answering

  protected acknowledgeTurn(): void {
    this.acknowledgedRoundId.set(this.round()?.roundId ?? null);
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
    this.audioPlaying.set(false);
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
        // Whatever happened (resolved, or locked and waiting), the server's state says what to show next.
        next: () => this.refresh(),
        // e.g. time ran out or the answer was already in: the fresh state explains it.
        error: () => this.refresh(),
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

  private resetGuessState(): void {
    this.showGuessPanel.set(false);
    this.guessQuery.set('');
    this.guessResults.set([]);
    this.searchingGuess.set(false);
    this.selectedGuessSong.set(null);
    this.guessYear.set(null);
  }

  // ------------------------------------------------------------------ moving on

  protected continueAfterReveal(): void {
    const summary = this.summary();
    if (!summary || this.advancing()) return;

    if (this.isGameOver()) {
      this.router.navigate(['/game', this.gameId, 'results']);
      return;
    }

    this.advancing.set(true);
    this.gameService
      .nextRound(this.gameId, summary.roundNumber, this.seat?.playerId)
      .pipe(finalize(() => this.advancing.set(false)))
      .subscribe({
        next: (state) => this.apply(state),
        // Too early (the reveal is still being read) or someone else got there first: just look again.
        error: () => this.refresh(),
      });
  }
}
