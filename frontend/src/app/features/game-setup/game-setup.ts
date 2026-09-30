import { Component, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Router } from '@angular/router';
import { catchError, finalize, of, switchMap } from 'rxjs';
import { GameService } from '../../core/services/game.service';
import { ProfileService } from '../../core/services/profile.service';
import { DECADE_PRESETS, Difficulty, GameMode, GENRE_LABELS, MusicGenre } from '../../core/models/enums';
import { ApiError, CreateGameRequest } from '../../core/models/game.model';
import { ErrorState } from '../../shared/components/error-state/error-state';

const MARKETS: { code: string | null; label: string }[] = [
  { code: null, label: 'International' },
  { code: 'US', label: 'United States' },
  { code: 'ES', label: 'Spain' },
  { code: 'GB', label: 'United Kingdom' },
  { code: 'MX', label: 'Mexico' },
];

@Component({
  selector: 'app-game-setup',
  imports: [FormsModule, ErrorState],
  templateUrl: './game-setup.html',
  styleUrl: './game-setup.scss',
})
export class GameSetup {
  private readonly gameService = inject(GameService);
  private readonly profiles = inject(ProfileService);
  private readonly router = inject(Router);

  protected readonly markets = MARKETS;
  protected readonly decades = DECADE_PRESETS;
  protected readonly genres = Object.entries(GENRE_LABELS) as [MusicGenre, string][];

  protected readonly mode = signal<GameMode>('SOLO');
  protected readonly playerNames = signal<string[]>(['']);
  protected readonly market = signal<string | null>(null);
  protected readonly genre = signal<MusicGenre | null>(null);
  protected readonly decadeIndex = signal(0);
  protected readonly difficulty = signal<Difficulty>('NORMAL');
  protected readonly maxLives = signal(3);
  protected readonly maxRounds = signal<number | null>(null);

  protected readonly submitting = signal(false);
  protected readonly error = signal<ApiError | null>(null);

  constructor() {
    // Returning players don't retype their name; player 1 is always "you".
    const profile = this.profiles.profile();
    if (profile) {
      this.playerNames.set([profile.nickname]);
    }
  }

  setMode(mode: GameMode): void {
    this.mode.set(mode);
    if (mode === 'SOLO') {
      this.playerNames.set([this.playerNames()[0] ?? '']);
    } else if (this.playerNames().length < 2) {
      this.playerNames.set([this.playerNames()[0] ?? '', '']);
    }
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
    if (this.mode() === 'SOLO') {
      return true;
    }
    return names.length >= 2 && names.every((n) => n.trim().length > 0);
  }

  startGame(): void {
    if (!this.canSubmit || this.submitting()) {
      return;
    }
    this.error.set(null);
    this.submitting.set(true);

    const names =
      this.mode() === 'SOLO'
        ? [this.playerNames()[0]?.trim() || 'You']
        : this.playerNames().map((n) => n.trim());

    const decade = this.decades[this.decadeIndex()];
    const request: CreateGameRequest = {
      mode: this.mode(),
      playerNames: names,
      settings: {
        market: this.market(),
        genre: this.genre(),
        yearFrom: decade.yearFrom,
        yearTo: decade.yearTo,
        difficulty: this.difficulty(),
        maxLives: this.maxLives(),
        maxRounds: this.maxRounds(),
      },
    };

    // Player 1 is the signed-in player: their results feed the profile's records. Failing to
    // reach the profile endpoint must never stop a game, so it degrades to a guest game.
    this.profiles
      .ensureProfile(names[0])
      .pipe(
        catchError(() => of(null)),
        switchMap((profile) =>
          this.gameService.createGame({ ...request, profilePlayerIndex: profile ? 0 : null }),
        ),
        switchMap((game) => this.gameService.startGame(game.id)),
        finalize(() => this.submitting.set(false)),
      )
      .subscribe({
        next: (started) => this.router.navigate(['/game', started.game.id]),
        error: (err: ApiError) => this.error.set(err),
      });
  }
}
