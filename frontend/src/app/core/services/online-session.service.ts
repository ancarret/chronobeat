import { Injectable } from '@angular/core';
import { OnlineSession } from '../models/room.model';

const STORAGE_KEY = 'chronobeat.online';

/** Old seats are dropped once there are more than this many, so storage can't grow without bound. */
const MAX_REMEMBERED_SEATS = 10;

/**
 * Remembers this browser's seat (player id + secret token) in each online game it joined, so a page
 * refresh, a closed tab or a phone that went to sleep can rejoin the very same seat instead of being
 * locked out. Storage can be unavailable (private windows), so it also keeps seats in memory.
 */
@Injectable({ providedIn: 'root' })
export class OnlineSessionService {
  private readonly seats = new Map<string, OnlineSession>(this.read().map((s) => [s.gameId, s]));

  forGame(gameId: string): OnlineSession | null {
    return this.seats.get(gameId) ?? null;
  }

  remember(session: OnlineSession): void {
    this.seats.delete(session.gameId); // re-insert so the newest seat is last
    this.seats.set(session.gameId, session);
    while (this.seats.size > MAX_REMEMBERED_SEATS) {
      this.seats.delete(this.seats.keys().next().value as string);
    }
    this.write();
  }

  forget(gameId: string): void {
    if (this.seats.delete(gameId)) {
      this.write();
    }
  }

  private write(): void {
    try {
      localStorage.setItem(STORAGE_KEY, JSON.stringify([...this.seats.values()]));
    } catch {
      // Storage blocked or full: the in-memory copy still serves this session.
    }
  }

  private read(): OnlineSession[] {
    try {
      const raw = localStorage.getItem(STORAGE_KEY);
      const parsed = raw ? (JSON.parse(raw) as unknown) : [];
      return Array.isArray(parsed)
        ? parsed.filter((s): s is OnlineSession => !!s && typeof s.gameId === 'string' && typeof s.playerToken === 'string')
        : [];
    } catch {
      return [];
    }
  }
}
