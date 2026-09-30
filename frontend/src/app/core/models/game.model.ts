import { Difficulty, GameMode, GameStatus, MusicGenre, PlayStyle } from './enums';
import { RecordsBroken } from './profile.model';

export interface GameSettingsRequest {
  market: string | null;
  genre: MusicGenre | null;
  yearFrom: number | null;
  yearTo: number | null;
  difficulty: Difficulty;
  maxLives: number | null;
  maxRounds: number | null;
  playStyle: PlayStyle;
  /** "First to N cards wins"; null means no race. */
  targetTimelineSize: number | null;
  /** Per-round time limit in seconds; null means untimed. */
  answerSeconds: number | null;
}

export interface GameSettings {
  market: string | null;
  genre: MusicGenre | null;
  yearFrom: number | null;
  yearTo: number | null;
  difficulty: Difficulty;
  maxLives: number;
  maxRounds: number | null;
  playStyle: PlayStyle;
  targetTimelineSize: number | null;
  answerSeconds: number | null;
}

export interface CreateGameRequest {
  mode: GameMode;
  playerNames: string[];
  settings: GameSettingsRequest;
  /** Which entry of playerNames is the signed-in profile; omitted for guest games. */
  profilePlayerIndex?: number | null;
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
  host: boolean;
  /** Locked in an answer for the round in progress (shared-song rounds waiting on others). */
  answered: boolean;
}

export interface Game {
  id: string;
  mode: GameMode;
  status: GameStatus;
  settings: GameSettings;
  currentRoundNumber: number;
  players: Player[];
  createdAt: string;
  /** Code others type to join; online games only. */
  roomCode: string | null;
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
  /** Seconds left on the server's clock; null when the game is untimed. */
  answerSecondsRemaining: number | null;
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

/** One player's outcome of a resolved round. */
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

/**
 * What became of a submitted answer. Turn-based rounds resolve immediately; in shared-song rounds the
 * answer is only locked until the whole table has answered, and `result` stays null until then.
 */
export interface AnswerOutcome {
  resolved: boolean;
  waitingFor: number;
  result: RoundResult | null;
}

/** The reveal of a fully resolved round: the song, and how every player fared. */
export interface RoundSummary {
  roundNumber: number;
  reveal: SongReveal;
  results: RoundResult[];
}

/** Which screen a player should be looking at. */
export type GamePhase = 'LOBBY' | 'ANSWERING' | 'WAITING' | 'REVEAL' | 'FINISHED';

/** Everything needed to render one player's view of a game; re-fetched whenever something changes. */
export interface GameState {
  game: Game;
  phase: GamePhase;
  viewerPlayerId: string | null;
  round: RoundPending | null;
  summary: RoundSummary | null;
  waitingOnPlayerIds: string[];
  answerSecondsRemaining: number | null;
  connectedPlayerIds: string[];
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
  /** Null on a genuine tie. */
  winningPlayerId: string | null;
  /** One entry per profile-linked player; guests have none. */
  records: RecordsBroken[];
}

export interface ApiError {
  timestamp: string;
  status: number;
  error: string;
  message: string;
  details: string[];
}
