import {
  Game,
  GameSettings,
  GameState,
  Player,
  RoundPending,
  RoundResult,
  RoundSummary,
  SongReveal,
  TimelineEntry,
} from '../core/models/game.model';

/** Small builders for the shapes the API returns, so specs only spell out what they care about. */

export const player = (overrides: Partial<Player> = {}): Player => ({
  id: 'p1',
  displayName: 'Ana',
  playerOrder: 0,
  score: 0,
  correctAnswers: 0,
  incorrectAnswers: 0,
  accuracy: 0,
  currentStreak: 0,
  bestStreak: 0,
  livesRemaining: 3,
  eliminated: false,
  timelineSize: 0,
  host: false,
  answered: false,
  ...overrides,
});

export const settings = (overrides: Partial<GameSettings> = {}): GameSettings => ({
  market: null,
  genre: null,
  yearFrom: null,
  yearTo: null,
  difficulty: 'NORMAL',
  maxLives: 3,
  maxRounds: null,
  playStyle: 'TURN_BASED',
  targetTimelineSize: null,
  answerSeconds: null,
  ...overrides,
});

export const game = (overrides: Partial<Game> = {}): Game => ({
  id: 'g1',
  mode: 'SOLO',
  status: 'ACTIVE',
  settings: settings(),
  currentRoundNumber: 1,
  players: [player()],
  createdAt: '2026-01-01T00:00:00Z',
  roomCode: null,
  ...overrides,
});

export const entry = (year: number, overrides: Partial<TimelineEntry> = {}): TimelineEntry => ({
  songId: `song-${year}`,
  title: `Song ${year}`,
  artist: 'Someone',
  album: null,
  year,
  artworkUrl: null,
  genre: 'POP',
  position: 0,
  ...overrides,
});

export const round = (overrides: Partial<RoundPending> = {}): RoundPending => ({
  roundId: 'r1',
  gameId: 'g1',
  playerId: 'p1',
  playerDisplayName: 'Ana',
  roundNumber: 1,
  anchorRound: false,
  previewUrl: 'https://preview.example/a.m4a',
  previewPlaySeconds: 12,
  timeline: [entry(1977)],
  allowedPositionCount: 2,
  livesRemaining: 3,
  answerSecondsRemaining: null,
  ...overrides,
});

export const reveal = (overrides: Partial<SongReveal> = {}): SongReveal => ({
  songId: 'song-x',
  title: 'Mystery',
  artist: 'Somebody',
  album: null,
  year: 1985,
  artworkUrl: null,
  genre: 'POP',
  ...overrides,
});

export const result = (overrides: Partial<RoundResult> = {}): RoundResult => ({
  roundId: 'r1',
  correct: true,
  anchorRound: false,
  submittedPosition: 1,
  validPositions: [1],
  guessCorrect: null,
  reveal: reveal(),
  player: player(),
  timeline: [entry(1977), entry(1985)],
  gameStatus: 'ACTIVE',
  ...overrides,
});

export const summary = (results: RoundResult[] = [result()], roundNumber = 1): RoundSummary => ({
  roundNumber,
  reveal: results[0]?.reveal ?? reveal(),
  results,
});

export const state = (overrides: Partial<GameState> = {}): GameState => ({
  game: game(),
  phase: 'ANSWERING',
  viewerPlayerId: 'p1',
  round: round(),
  summary: null,
  waitingOnPlayerIds: [],
  answerSecondsRemaining: null,
  connectedPlayerIds: [],
  ...overrides,
});
