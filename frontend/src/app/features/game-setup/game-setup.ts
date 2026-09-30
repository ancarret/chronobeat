import { Component, computed, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { catchError, finalize, of, switchMap } from 'rxjs';
import { GameService } from '../../core/services/game.service';
import { ProfileService } from '../../core/services/profile.service';
import { RoomService } from '../../core/services/room.service';
import { DECADE_PRESETS, Difficulty, GameMode, GENRE_LABELS, MusicGenre, PlayStyle } from '../../core/models/enums';
import { ApiError, CreateGameRequest, GameSettingsRequest } from '../../core/models/game.model';
import { ErrorState } from '../../shared/components/error-state/error-state';

const MARKETS: { code: string | null; label: string }[] = [
  { code: null, label: 'International' },
  { code: 'US', label: 'United States' },
  { code: 'ES', label: 'Spain' },
  { code: 'GB', label: 'United Kingdom' },
  { code: 'MX', label: 'Mexico' },
];

/** "First to N cards" presets; null is no goal. */
const GOALS: (number | null)[] = [null, 5, 10, 15, 20];

/** Seconds per round; null is untimed. Online games always run on a clock, so they never offer null. */
const TIME_LIMITS: (number | null)[] = [null, 20, 30, 45, 60];

const DEFAULT_ONLINE_SECONDS = 45;
const DEFAULT_RACE_GOAL = 10;

@Component({
  selector: 'app-game-setup',
  imports: [FormsModule, RouterLink, ErrorState],
  templateUrl: './game-setup.html',
  styleUrl: './game-setup.scss',
})
export class GameSetup {
  private readonly gameService = inject(GameService);
  private readonly rooms = inject(RoomService);
  private readonly profiles = inject(ProfileService);
  private readonly router = inject(Router);

  protected readonly markets = MARKETS;
  protected readonly decades = DECADE_PRESETS;
  protected readonly genres = Object.entries(GENRE_LABELS) as [MusicGenre, string][];
  protected readonly goals = GOALS;

  protected readonly mode = signal<GameMode>('SOLO');
  protected readonly playStyle = signal<PlayStyle>('TURN_BASED');
  protected readonly playerNames = signal<string[]>(['']);
  protected readonly market = signal<string | null>(null);
  protected readonly genre = signal<MusicGenre | null>(null);
  protected readonly decadeIndex = signal(0);
  protected readonly difficulty = signal<Difficulty>('NORMAL');
  protected readonly maxLives = signal(3);
  protected readonly maxRounds = signal<number | null>(null);
  protected readonly goal = signal<number | null>(null);
  protected readonly answerSeconds = signal<number | null>(null);

  protected readonly submitting = signal(false);
  protected readonly error = signal<ApiError | null>(null);

  /** Once the player picks a goal or a time limit themselves, changing the mode stops overriding it. */
  private goalTouched = false;
  private timeTouched = false;

  protected readonly isSolo = computed(() => this.mode() === 'SOLO');
  protected readonly isOnline = computed(() => this.mode() === 'ONLINE_MULTIPLAYER');
  protected readonly timeLimits = computed(() => (this.isOnline() ? TIME_LIMITS.filter((t) => t !== null) : TIME_LIMITS));

  constructor() {
    // Returning players don't retype their name; player 1 is always "you".
    const profile = this.profiles.profile();
    if (profile) {
      this.playerNames.set([profile.nickname]);
    }
  }

  setMode(mode: GameMode): void {
    this.mode.set(mode);
    const first = this.playerNames()[0] ?? '';
    if (mode === 'LOCAL_MULTIPLAYER') {
      this.playerNames.set(this.playerNames().length < 2 ? [first, ''] : this.playerNames());
    } else {
      this.playerNames.set([first]);
    }
    if (mode === 'SOLO') {
      this.playStyle.set('TURN_BASED'); // a single player has nobody to share songs with
    }
    if (!this.timeTouched) {
      this.answerSeconds.set(mode === 'ONLINE_MULTIPLAYER' ? DEFAULT_ONLINE_SECONDS : null);
    } else if (mode === 'ONLINE_MULTIPLAYER' && this.answerSeconds() === null) {
      this.answerSeconds.set(DEFAULT_ONLINE_SECONDS);
    }
  }

  setPlayStyle(style: PlayStyle): void {
    this.playStyle.set(style);
    // "Same songs" is a race on identical cards, so a goal is the natural default (unless they chose one).
    if (!this.goalTouched) {
      this.goal.set(style === 'SHARED_SONGS' ? DEFAULT_RACE_GOAL : null);
    }
  }

  setGoal(goal: number | null): void {
    this.goalTouched = true;
    this.goal.set(goal);
  }

  setTimeLimit(seconds: number | null): void {
    this.timeTouched = true;
    this.answerSeconds.set(seconds);
  }

  updatePlayerName(index: number, value: string): void {
    const names = [...this.playerNames()];
    names[index] = value;
    this.playerNames.set(names);
  }

  addPlayer(): void {
    if (this.playerNames().length < 8) {
      this.playerNames.set([...this.playerNames(), '']);
    }
  }

  removePlayer(index: number): void {
    if (this.playerNames().length > 2) {
      this.playerNames.set(this.playerNames().filter((_, i) => i !== index));
    }
  }

  get canSubmit(): boolean {
    const names = this.playerNames();
    switch (this.mode()) {
      case 'SOLO':
        return true;
      case 'ONLINE_MULTIPLAYER':
        // Other people will see this name in the lobby, so it can't be blank.
        return (names[0] ?? '').trim().length > 0;
      default:
        return names.length >= 2 && names.every((n) => n.trim().length > 0);
    }
  }

  private buildSettings(): GameSettingsRequest {
    const decade = this.decades[this.decadeIndex()];
    return {
      market: this.market(),
      genre: this.genre(),
      yearFrom: decade.yearFrom,
      yearTo: decade.yearTo,
      difficulty: this.difficulty(),
      maxLives: this.maxLives(),
      maxRounds: this.maxRounds(),
      playStyle: this.isSolo() ? 'TURN_BASED' : this.playStyle(),
      targetTimelineSize: this.goal(),
      answerSeconds: this.isSolo() ? null : this.answerSeconds(),
    };
  }

  startGame(): void {
    if (!this.canSubmit || this.submitting()) {
      return;
    }
    this.error.set(null);
    this.submitting.set(true);

    if (this.isOnline()) {
      this.openRoom();
    } else {
      this.startLocalGame();
    }
  }

  private startLocalGame(): void {
    const names = this.isSolo()
      ? [this.playerNames()[0]?.trim() || 'You']
      : this.playerNames().map((n) => n.trim());

    const request: CreateGameRequest = { mode: this.mode(), playerNames: names, settings: this.buildSettings() };

    // Player 1 is the signed-in player: their results feed the profile's records. Failing to
    // reach the profile endpoint must never stop a game, so it degrades to a guest game.
    this.profiles
      .ensureProfile(names[0])
      .pipe(
        catchError(() => of(null)),
        switchMap((profile) => this.gameService.createGame({ ...request, profilePlayerIndex: profile ? 0 : null })),
        switchMap((game) => this.gameService.startGame(game.id)),
        finalize(() => this.submitting.set(false)),
      )
      .subscribe({
        next: (started) => this.router.navigate(['/game', started.game.id]),
        error: (err: ApiError) => this.error.set(err),
      });
  }

  private openRoom(): void {
    const nickname = this.playerNames()[0].trim();
    this.profiles
      .ensureProfile(nickname)
      .pipe(
        catchError(() => of(null)),
        switchMap(() => this.rooms.createRoom(nickname, this.buildSettings())),
        finalize(() => this.submitting.set(false)),
      )
      .subscribe({
        next: (room) => this.router.navigate(['/room', room.gameId]),
        error: (err: ApiError) => this.error.set(err),
      });
  }
}
