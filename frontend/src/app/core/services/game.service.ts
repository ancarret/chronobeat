import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { ConfigService } from './config.service';
import {
  AnswerRequest,
  CreateGameRequest,
  Game,
  GameResults,
  RoundPending,
  RoundResult,
  StartGameResponse,
  TimelineEntry,
} from '../models/game.model';

/**
 * Thin wrapper around the backend REST API. Deliberately has no game-rule logic
 * of its own - every decision (song selection, placement validation, scoring)
 * happens server-side; this service only shapes HTTP calls.
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

  getCurrentRound(gameId: string): Observable<RoundPending> {
    return this.http.get<RoundPending>(`${this.base}/${gameId}/rounds/current`);
  }

  submitAnswer(gameId: string, roundId: string, answer: AnswerRequest): Observable<RoundResult> {
    return this.http.post<RoundResult>(`${this.base}/${gameId}/rounds/${roundId}/answer`, answer);
  }

  nextRound(gameId: string): Observable<RoundPending> {
    return this.http.post<RoundPending>(`${this.base}/${gameId}/next-round`, {});
  }

  getTimeline(gameId: string, playerId?: string): Observable<TimelineEntry[]> {
    const url = playerId ? `${this.base}/${gameId}/timeline?playerId=${playerId}` : `${this.base}/${gameId}/timeline`;
    return this.http.get<TimelineEntry[]>(url);
  }

  getResults(gameId: string): Observable<GameResults> {
    return this.http.get<GameResults>(`${this.base}/${gameId}/results`);
  }
}
