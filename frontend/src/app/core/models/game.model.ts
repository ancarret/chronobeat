import { Difficulty, GameMode, GameStatus, MusicGenre } from './enums';

export interface GameSettingsRequest {
  market: string | null;
  genre: MusicGenre | null;
  yearFrom: number | null;
  yearTo: number | null;
  difficulty: Difficulty;
  maxLives: number | null;
  maxRounds: number | null;
}

export interface GameSettings {
  market: string | null;
  genre: MusicGenre | null;
  yearFrom: number | null;
  yearTo: number | null;
  difficulty: Difficulty;
  maxLives: number;
  maxRounds: number | null;
}

export interface CreateGameRequest {
  mode: GameMode;
  playerNames: string[];
  settings: GameSettingsRequest;
}

export interface Player {
  id: string;
  displayName: string;
  playerOrder: number;
  score: number;
  correctAnswers: number;
  incorrectAnswers: number;
  accuracy: number;
  currentStreak: number;
  bestStreak: number;
  livesRemaining: number;
  eliminated: boolean;
  timelineSize: number;
}

export interface Game {
  id: string;
  mode: GameMode;
  status: GameStatus;
  settings: GameSettings;
  currentRoundNumber: number;
  players: Player[];
  createdAt: string;
}

export interface TimelineEntry {
  songId: string;
  title: string;
  artist: string;
  album: string | null;
  year: number;
  artworkUrl: string | null;
  genre: MusicGenre;
  position: number;
}

/** No title/artist/year here on purpose - the backend never leaks the answer. */
export interface RoundPending {
  roundId: string;
  gameId: string;
  playerId: string;
  playerDisplayName: string;
  roundNumber: number;
  anchorRound: boolean;
  previewUrl: string;
  previewPlaySeconds: number;
  timeline: TimelineEntry[];
  allowedPositionCount: number;
  livesRemaining: number;
}

export interface StartGameResponse {
  game: Game;
  currentRound: RoundPending;
}

export interface SongReveal {
  songId: string;
  title: string;
  artist: string;
  album: string | null;
  year: number;
  artworkUrl: string | null;
  genre: MusicGenre;
}

export interface AnswerRequest {
  insertPosition: number | null;
  guessedSongId?: string | null;
  guessedYear?: number | null;
}

export interface RoundResult {
  roundId: string;
  correct: boolean;
  anchorRound: boolean;
  submittedPosition: number | null;
  validPositions: number[];
  /** Null when no guess was attempted; otherwise whether the guessed title/artist/year was exact. */
  guessCorrect: boolean | null;
  reveal: SongReveal;
  player: Player;
  timeline: TimelineEntry[];
  gameStatus: GameStatus;
}

/** Result row from the in-round song search. Deliberately has no year - see backend SongSearchResultResponse. */
export interface SongSearchResult {
  id: string;
  title: string;
  artist: string;
  artworkUrl: string | null;
}

export interface GameResults {
  gameId: string;
  status: GameStatus;
  totalRounds: number;
  players: Player[];
  winningPlayerId: string | null;
}

export interface ApiError {
  timestamp: string;
  status: number;
  error: string;
  message: string;
  details: string[];
}
