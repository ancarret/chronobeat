import { HttpClient, HttpHeaders } from '@angular/common/http';
import { Injectable, computed, inject, signal } from '@angular/core';
import { Observable, catchError, map, of, tap, throwError } from 'rxjs';
import { ApiError } from '../models/game.model';
import { LocalProfile, ProfileCreated, ProfileInfo, ProfileStats } from '../models/profile.model';
import { ConfigService } from './config.service';

const STORAGE_KEY = 'chronobeat.profile';
export const PROFILE_TOKEN_HEADER = 'X-Profile-Token';

/**
 * The player's account-less identity. The server issues a secret token once; we keep it in
 * localStorage and the profile interceptor attaches it to API calls. Storage can be blocked or
 * cleared (private windows, "clear site data"), so every access is guarded and the app must
 * work as a guest without it - which is also why the token can be copied out as a recovery code.
 */
@Injectable({ providedIn: 'root' })
export class ProfileService {
  private readonly http = inject(HttpClient);
  private readonly config = inject(ConfigService);
  private readonly base = `${this.config.apiBaseUrl}/api/profiles`;

  readonly profile = signal<LocalProfile | null>(this.readStored());
  readonly token = computed(() => this.profile()?.token ?? null);

  /** Returns the stored profile, creating one from {@code nickname} the first time. */
  ensureProfile(nickname: string): Observable<LocalProfile> {
    const existing = this.profile();
    if (existing) {
      return of(existing);
    }
    return this.http
      .post<ProfileCreated>(this.base, { nickname: nickname.trim() || 'Player' })
      .pipe(tap((created) => this.store(created)));
  }

  rename(nickname: string): Observable<ProfileInfo> {
    return this.http.put<ProfileInfo>(`${this.base}/me`, { nickname: nickname.trim() }).pipe(
      tap((info) => {
        const current = this.profile();
        if (current) {
          this.store({ ...current, nickname: info.nickname });
        }
      }),
      catchError((err) => this.dropIfRejected(err)),
    );
  }

  stats(): Observable<ProfileStats> {
    return this.http.get<ProfileStats>(`${this.base}/me/stats`).pipe(catchError((err) => this.dropIfRejected(err)));
  }

  /** Adopts the profile that owns {@code token} (a recovery code copied from another device). */
  restore(token: string): Observable<LocalProfile> {
    const trimmed = token.trim();
    const headers = new HttpHeaders({ [PROFILE_TOKEN_HEADER]: trimmed });
    return this.http.get<ProfileInfo>(`${this.base}/me`, { headers }).pipe(
      map((info) => ({ id: info.id, nickname: info.nickname, token: trimmed })),
      tap((restored) => this.store(restored)),
    );
  }

  /** Forgets the profile on this device only; the server keeps it (recoverable with the code). */
  signOut(): void {
    this.profile.set(null);
    try {
      localStorage.removeItem(STORAGE_KEY);
    } catch {
      // Storage unavailable: nothing persisted, nothing to remove.
    }
  }

  /** A 401 means the server no longer knows this token (e.g. a reset database): stop sending it. */
  private dropIfRejected(err: ApiError): Observable<never> {
    if (err?.status === 401) {
      this.signOut();
    }
    return throwError(() => err);
  }

  private store(profile: LocalProfile): void {
    this.profile.set(profile);
    try {
      localStorage.setItem(STORAGE_KEY, JSON.stringify(profile));
    } catch {
      // Private mode / quota: keep it in memory for this session at least.
    }
  }

  private readStored(): LocalProfile | null {
    try {
      const raw = localStorage.getItem(STORAGE_KEY);
      if (!raw) return null;
      const parsed = JSON.parse(raw) as Partial<LocalProfile>;
      return parsed.id && parsed.token && parsed.nickname ? (parsed as LocalProfile) : null;
    } catch {
      return null;
    }
  }
}
