import { Injectable, NgZone, inject } from '@angular/core';
import { Observable, map, merge, timer } from 'rxjs';
import { ConfigService } from './config.service';

/** Even with a healthy stream, look again this often: a cheap safety net for proxies that buffer events. */
const SAFETY_POLL_MILLIS = 10_000;

/**
 * "Something changed, look again" signals for a game.
 *
 * Opens a Server-Sent Events stream, and merges a slow poll as a backstop. It emits no data, only
 * ticks: subscribers respond by re-fetching their view of the game, which is what keeps every screen
 * consistent even when the stream drops, reconnects or misses events. The browser's EventSource
 * reconnects by itself, and its first event after each reconnect makes us re-fetch too.
 */
@Injectable({ providedIn: 'root' })
export class GameEventsService {
  private readonly config = inject(ConfigService);
  private readonly zone = inject(NgZone);

  watch(gameId: string, playerId?: string | null): Observable<void> {
    return merge(
      this.stream(gameId, playerId ?? null),
      timer(SAFETY_POLL_MILLIS, SAFETY_POLL_MILLIS).pipe(map((): void => undefined)),
    );
  }

  private stream(gameId: string, playerId: string | null): Observable<void> {
    return new Observable<void>((subscriber) => {
      if (typeof EventSource === 'undefined') {
        return undefined; // very old browser: the poll alone keeps things moving
      }
      const query = playerId ? `?playerId=${encodeURIComponent(playerId)}` : '';
      // EventSource callbacks fire outside Angular's zone, so hop back in for change detection.
      const source = new EventSource(`${this.config.apiBaseUrl}/api/games/${gameId}/events${query}`);
      const tick = () => this.zone.run(() => subscriber.next());
      source.addEventListener('hello', tick);
      source.addEventListener('changed', tick);
      return () => source.close();
    });
  }
}
