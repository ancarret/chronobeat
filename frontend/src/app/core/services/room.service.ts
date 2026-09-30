import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable, tap } from 'rxjs';
import { GameSettingsRequest } from '../models/game.model';
import { RoomInfo, RoomJoined } from '../models/room.model';
import { ConfigService } from './config.service';
import { OnlineSessionService } from './online-session.service';

/** Online rooms: open one, look one up by code, join, leave, and (host) remove someone. */
@Injectable({ providedIn: 'root' })
export class RoomService {
  private readonly http = inject(HttpClient);
  private readonly sessions = inject(OnlineSessionService);
  private readonly api = inject(ConfigService).apiBaseUrl;

  createRoom(nickname: string, settings: GameSettingsRequest): Observable<RoomJoined> {
    return this.http.post<RoomJoined>(`${this.api}/api/rooms`, { nickname, settings }).pipe(tap((room) => this.remember(room)));
  }

  lookup(roomCode: string): Observable<RoomInfo> {
    return this.http.get<RoomInfo>(`${this.api}/api/rooms/${encodeURIComponent(roomCode.trim())}`);
  }

  join(roomCode: string, nickname: string): Observable<RoomJoined> {
    return this.http
      .post<RoomJoined>(`${this.api}/api/rooms/${encodeURIComponent(roomCode.trim())}/join`, { nickname })
      .pipe(tap((room) => this.remember(room)));
  }

  leave(gameId: string): Observable<void> {
    return this.http.post<void>(`${this.api}/api/games/${gameId}/leave`, {}).pipe(tap(() => this.sessions.forget(gameId)));
  }

  kick(gameId: string, playerId: string): Observable<void> {
    return this.http.delete<void>(`${this.api}/api/games/${gameId}/players/${playerId}`);
  }

  private remember(room: RoomJoined): void {
    this.sessions.remember({
      gameId: room.gameId,
      roomCode: room.roomCode,
      playerId: room.playerId,
      playerToken: room.playerToken,
    });
  }
}
