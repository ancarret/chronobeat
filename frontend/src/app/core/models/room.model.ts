import { Game, GameSettings } from './game.model';
import { GameStatus } from './enums';

/** Returned when a player creates or joins a room. `playerToken` is that player's only credential. */
export interface RoomJoined {
  gameId: string;
  roomCode: string;
  playerId: string;
  playerToken: string;
  game: Game;
}

/** What anyone holding a room code may see before joining. */
export interface RoomInfo {
  roomCode: string;
  gameId: string;
  status: GameStatus;
  hostName: string;
  playerCount: number;
  maxPlayers: number;
  settings: GameSettings;
}

/** What this browser remembers about its seat in an online game. */
export interface OnlineSession {
  gameId: string;
  roomCode: string;
  playerId: string;
  playerToken: string;
}
