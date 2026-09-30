import { Component, DestroyRef, computed, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { EMPTY, Subject, catchError, finalize, switchMap } from 'rxjs';
import { ApiError, GameState, Player } from '../../core/models/game.model';
import { GameEventsService } from '../../core/services/game-events.service';
import { GameService } from '../../core/services/game.service';
import { OnlineSessionService } from '../../core/services/online-session.service';
import { RoomService } from '../../core/services/room.service';
import { ErrorState } from '../../shared/components/error-state/error-state';
import { LoadingState } from '../../shared/components/loading-state/loading-state';
import { Vinyl } from '../../shared/components/vinyl/vinyl';
import { summarizeSettings } from '../../shared/utils/settings-summary';

/**
 * The waiting room of an online game: share the code, watch friends arrive, and (as host) start.
 * It listens to the game's live events, so joins, departures and connection changes show up at once.
 */
@Component({
  selector: 'app-lobby',
  imports: [RouterLink, LoadingState, ErrorState, Vinyl],
  templateUrl: './lobby.html',
  styleUrl: './lobby.scss',
})
export class Lobby {
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);
  private readonly gameService = inject(GameService);
  private readonly rooms = inject(RoomService);
  private readonly events = inject(GameEventsService);
  private readonly sessions = inject(OnlineSessionService);
  private readonly destroyRef = inject(DestroyRef);

  private readonly gameId = this.route.snapshot.paramMap.get('gameId')!;
  private readonly seat = this.sessions.forGame(this.gameId);
  private readonly refresh$ = new Subject<void>();

  protected readonly state = signal<GameState | null>(null);
  protected readonly error = signal<ApiError | null>(null);
  protected readonly busy = signal(false);
  protected readonly copied = signal<'code' | 'link' | null>(null);
  protected readonly actionError = signal<string | null>(null);

  protected readonly game = computed(() => this.state()?.game ?? null);
  protected readonly players = computed(() => this.game()?.players ?? []);
  protected readonly me = computed<Player | null>(() => this.players().find((p) => p.id === this.seat?.playerId) ?? null);
  protected readonly isHost = computed(() => this.me()?.host === true);
  protected readonly connected = computed(() => new Set(this.state()?.connectedPlayerIds ?? []));
  protected readonly rules = computed(() => {
    const game = this.game();
    return game ? summarizeSettings(game.settings) : [];
  });
  protected readonly canStart = computed(() => this.isHost() && this.players().length >= 2 && !this.busy());
  protected readonly joinLink = computed(() => {
    const code = this.game()?.roomCode;
    return code ? `${window.location.origin}/join/${code}` : '';
  });
  protected readonly canShare = typeof navigator !== 'undefined' && typeof navigator.share === 'function';

  constructor() {
    if (!this.seat) {
      // No seat in this game on this browser: the way in is the join screen.
      this.router.navigate(['/join'], { replaceUrl: true });
      return;
    }

    this.refresh$
      .pipe(
        switchMap(() =>
          this.gameService.getState(this.gameId, this.seat!.playerId).pipe(
            catchError((err: ApiError) => {
              this.onLoadFailed(err);
              return EMPTY;
            }),
          ),
        ),
        takeUntilDestroyed(),
      )
      .subscribe((state) => this.apply(state));

    this.events
      .watch(this.gameId, this.seat.playerId)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe(() => this.refresh$.next());

    this.refresh$.next();
  }

  private apply(state: GameState): void {
    this.error.set(null);
    if (state.phase !== 'LOBBY') {
      this.router.navigate(['/game', this.gameId], { replaceUrl: true });
      return;
    }
    if (!state.game.players.some((p) => p.id === this.seat?.playerId)) {
      // The host removed us.
      this.sessions.forget(this.gameId);
      this.error.set(this.synthetic(403, "You've been removed from this room."));
      return;
    }
    this.state.set(state);
  }

  private onLoadFailed(err: ApiError): void {
    if (err.status === 401 || err.status === 403 || err.status === 404) {
      this.sessions.forget(this.gameId);
    }
    this.error.set(err);
  }

  private synthetic(status: number, message: string): ApiError {
    return { timestamp: new Date().toISOString(), status, error: 'Removed', message, details: [] };
  }

  protected retry(): void {
    this.error.set(null);
    this.refresh$.next();
  }

  protected start(): void {
    if (!this.canStart()) return;
    this.busy.set(true);
    this.actionError.set(null);
    this.gameService
      .startGame(this.gameId)
      .pipe(finalize(() => this.busy.set(false)))
      .subscribe({
        next: () => this.router.navigate(['/game', this.gameId]),
        error: (err: ApiError) => this.actionError.set(err.message),
      });
  }

  protected kick(player: Player): void {
    this.rooms.kick(this.gameId, player.id).subscribe({
      next: () => this.refresh$.next(),
      error: (err: ApiError) => this.actionError.set(err.message),
    });
  }

  protected leave(): void {
    this.rooms.leave(this.gameId).subscribe({
      next: () => this.router.navigate(['/']),
      // Even if the server can't be reached, drop our seat locally and go home.
      error: () => {
        this.sessions.forget(this.gameId);
        this.router.navigate(['/']);
      },
    });
  }

  protected async copy(what: 'code' | 'link'): Promise<void> {
    const text = what === 'code' ? (this.game()?.roomCode ?? '') : this.joinLink();
    try {
      await navigator.clipboard.writeText(text);
      this.copied.set(what);
      setTimeout(() => this.copied.set(null), 2000);
    } catch {
      // Clipboard blocked: the code is big and on screen, so it can still be read out loud.
    }
  }

  protected async share(): Promise<void> {
    try {
      await navigator.share({
        title: 'Join my Chronobeat game',
        text: `Room code ${this.game()?.roomCode}`,
        url: this.joinLink(),
      });
    } catch {
      // Dismissed by the user; nothing to do.
    }
  }
}
