import { Component, computed, inject, signal } from '@angular/core';
import { takeUntilDestroyed, toObservable } from '@angular/core/rxjs-interop';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { catchError, debounceTime, distinctUntilChanged, finalize, of, switchMap, tap } from 'rxjs';
import { ApiError } from '../../core/models/game.model';
import { RoomInfo } from '../../core/models/room.model';
import { OnlineSessionService } from '../../core/services/online-session.service';
import { ProfileService } from '../../core/services/profile.service';
import { RoomService } from '../../core/services/room.service';
import { summarizeSettings } from '../../shared/utils/settings-summary';

const MIN_CODE_LENGTH = 4;

/**
 * Enter a room code and a name to take a seat. The code can arrive in the link a friend shared
 * (/join/BKQT), in which case the room is looked up straight away.
 */
@Component({
  selector: 'app-join',
  imports: [FormsModule, RouterLink],
  templateUrl: './join.html',
  styleUrl: './join.scss',
})
export class Join {
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);
  private readonly rooms = inject(RoomService);
  private readonly profiles = inject(ProfileService);
  private readonly sessions = inject(OnlineSessionService);

  protected readonly code = signal((this.route.snapshot.paramMap.get('code') ?? '').toUpperCase().trim());
  protected readonly nickname = signal(this.profiles.profile()?.nickname ?? '');

  protected readonly room = signal<RoomInfo | null>(null);
  protected readonly lookingUp = signal(false);
  protected readonly lookupError = signal<string | null>(null);
  protected readonly joining = signal(false);
  protected readonly joinError = signal<string | null>(null);

  protected readonly rules = computed(() => {
    const room = this.room();
    return room ? summarizeSettings(room.settings) : [];
  });
  protected readonly canJoin = computed(
    () => this.room() !== null && this.nickname().trim().length > 0 && !this.joining() && this.room()!.playerCount < this.room()!.maxPlayers,
  );

  constructor() {
    toObservable(this.code)
      .pipe(
        debounceTime(300),
        distinctUntilChanged(),
        tap(() => {
          this.room.set(null);
          this.lookupError.set(null);
        }),
        switchMap((code) => {
          if (code.length < MIN_CODE_LENGTH) {
            this.lookingUp.set(false);
            return of(null);
          }
          this.lookingUp.set(true);
          return this.rooms.lookup(code).pipe(
            catchError((err: ApiError) => {
              this.lookupError.set(err.status === 404 ? 'No open room with that code.' : err.message);
              return of(null);
            }),
            finalize(() => this.lookingUp.set(false)),
          );
        }),
        takeUntilDestroyed(),
      )
      .subscribe((room) => {
        this.room.set(room);
        // Already seated here (say, on a refresh): go straight back to the room.
        if (room && this.sessions.forGame(room.gameId)) {
          this.router.navigate(['/room', room.gameId], { replaceUrl: true });
        }
      });
  }

  protected onCodeChange(value: string): void {
    this.code.set(value.toUpperCase().replace(/[^A-Z]/g, '').slice(0, 8));
  }

  protected join(): void {
    const room = this.room();
    if (!room || !this.canJoin()) return;
    this.joining.set(true);
    this.joinError.set(null);

    const nickname = this.nickname().trim();
    // Creating a profile first means the game counts towards their records; if that fails they still play as a guest.
    this.profiles
      .ensureProfile(nickname)
      .pipe(
        catchError(() => of(null)),
        switchMap(() => this.rooms.join(room.roomCode, nickname)),
        finalize(() => this.joining.set(false)),
      )
      .subscribe({
        next: (joined) => this.router.navigate(['/room', joined.gameId]),
        error: (err: ApiError) => this.joinError.set(err.message),
      });
  }
}
