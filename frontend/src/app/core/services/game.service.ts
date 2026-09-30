import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { ConfigService } from './config.service';
import {
  AnswerOutcome,
  AnswerRequest,
  CreateGameRequest,
  Game,
  GameResults,
  GameState,
  RoundPending,
  SongSearchResult,
  StartGameResponse,
  TimelineEntry,
} from '../models/game.model';

/**
 * Thin wrapper around the backend REST API. Deliberately has no game-rule logic
 * of its own - every decision (song selection, placement validation, scoring)
 * happens server-side; this service only shapes HTTP calls.
 *
 * Online games additionally need the caller's seat token; the player interceptor attaches it.
 */
@Injectable({ providedIn: 'root' })
export class GameService {
  private readonly http = inject(HttpClient);
  private readonly config = inject(ConfigService);
  private readonly base = `${this.config.apiBaseUrl}/api/games`;

  createGame(request: CreateGameRequest): Observable<Game> {
    return this.http.post<Game>(this.base, request);
  }

  getGame(gameId: string): Observable<Game> {
    return this.http.get<Game>(`${this.base}/${gameId}`);
  }

  startGame(gameId: string): Observable<StartGameResponse> {
    return this.http.post<StartGameResponse>(`${this.base}/${gameId}/start`, {});
  }

  /**
   * One player's complete view of the game. On a shared device leave `playerId` out and the server
   * hands over whoever still has something to do.
   */
  getState(gameId: string, playerId?: string | null): Observable<GameState> {
    const params = playerId ? new HttpParams().set('playerId', playerId) : undefined;
    return this.http.get<GameState>(`${this.base}/${gameId}/state`, { params });
  }

  getCurrentRound(gameId: string, playerId?: string | null): Observable<RoundPending> {
    const params = playerId ? new HttpParams().set('playerId', playerId) : undefined;
    return this.http.get<RoundPending>(`${this.base}/${gameId}/rounds/current`, { params });
  }

  submitAnswer(gameId: string, roundId: string, answer: AnswerRequest): Observable<AnswerOutcome> {
    return this.http.post<AnswerOutcome>(`${this.base}/${gameId}/rounds/${roundId}/answer`, answer);
  }

  /**
   * Deals the next round. `after` is the round number you just watched: if somebody else already
   * moved the game on, the server leaves it alone, so everyone can safely press "Next" at once.
   */
  nextRound(gameId: string, after: number, playerId?: string | null): Observable<GameState> {
    let params = new HttpParams().set('after', after);
    if (playerId) {
      params = params.set('playerId', playerId);
    }
    return this.http.post<GameState>(`${this.base}/${gameId}/next-round`, {}, { params });
  }

  getTimeline(gameId: string, playerId?: string): Observable<TimelineEntry[]> {
    const params = playerId ? new HttpParams().set('playerId', playerId) : undefined;
    return this.http.get<TimelineEntry[]>(`${this.base}/${gameId}/timeline`, { params });
  }

  getResults(gameId: string): Observable<GameResults> {
    return this.http.get<GameResults>(`${this.base}/${gameId}/results`);
  }

  searchSongs(query: string): Observable<SongSearchResult[]> {
    const params = new URLSearchParams({ query });
    return this.http.get<SongSearchResult[]>(`${this.config.apiBaseUrl}/api/songs/search?${params}`);
  }
}
