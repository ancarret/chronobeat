import { GameMode } from './enums';

/** What the browser remembers about the player. The token is the profile's only credential. */
export interface LocalProfile {
  id: string;
  nickname: string;
  token: string;
}

export interface ProfileCreated {
  id: string;
  nickname: string;
  token: string;
}

export interface ProfileInfo {
  id: string;
  nickname: string;
  createdAt: string;
}

export interface AccuracyBucket {
  /** Decade start year ("1980") or MusicGenre name, depending on the list it comes from. */
  key: string;
  correct: number;
  total: number;
  accuracy: number;
}

export interface RecentGame {
  gameId: string;
  playedAt: string;
  mode: GameMode;
  score: number;
  timelineSize: number;
  accuracy: number;
  bestStreak: number;
}

export interface ProfileStats {
  gamesPlayed: number;
  bestScore: number;
  longestTimeline: number;
  bestStreak: number;
  totalCorrect: number;
  totalIncorrect: number;
  accuracy: number;
  byDecade: AccuracyBucket[];
  byGenre: AccuracyBucket[];
  recentGames: RecentGame[];
}

export interface RecordsBroken {
  playerId: string;
  firstGame: boolean;
  bestScore: boolean;
  longestTimeline: boolean;
  bestStreak: boolean;
}
