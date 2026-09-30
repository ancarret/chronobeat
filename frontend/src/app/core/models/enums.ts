export type GameMode = 'SOLO' | 'LOCAL_MULTIPLAYER' | 'ONLINE_MULTIPLAYER';
/** TURN_BASED: each turn is one player's own song. SHARED_SONGS: everyone hears the same song each round. */
export type PlayStyle = 'TURN_BASED' | 'SHARED_SONGS';
export type GameStatus = 'CREATED' | 'ACTIVE' | 'FINISHED';
export type Difficulty = 'EASY' | 'NORMAL' | 'HARD';
export type MusicGenre =
  | 'POP'
  | 'ROCK'
  | 'HIP_HOP_RAP'
  | 'ELECTRONIC'
  | 'ALTERNATIVE_INDIE'
  | 'METAL'
  | 'RNB_SOUL'
  | 'LATIN'
  | 'REGGAETON_LATIN_URBAN'
  | 'OTHER';

export const GENRE_LABELS: Record<MusicGenre, string> = {
  POP: 'Pop',
  ROCK: 'Rock',
  HIP_HOP_RAP: 'Hip-Hop / Rap',
  ELECTRONIC: 'Electronic',
  ALTERNATIVE_INDIE: 'Alternative / Indie',
  METAL: 'Metal',
  RNB_SOUL: 'R&B / Soul',
  LATIN: 'Latin',
  REGGAETON_LATIN_URBAN: 'Reggaeton / Latin Urban',
  OTHER: 'Other',
};

export interface DecadePreset {
  label: string;
  yearFrom: number | null;
  yearTo: number | null;
}

export const DECADE_PRESETS: DecadePreset[] = [
  { label: 'All years', yearFrom: null, yearTo: null },
  { label: '1950s', yearFrom: 1950, yearTo: 1959 },
  { label: '1960s', yearFrom: 1960, yearTo: 1969 },
  { label: '1970s', yearFrom: 1970, yearTo: 1979 },
  { label: '1980s', yearFrom: 1980, yearTo: 1989 },
  { label: '1990s', yearFrom: 1990, yearTo: 1999 },
  { label: '2000s', yearFrom: 2000, yearTo: 2009 },
  { label: '2010s', yearFrom: 2010, yearTo: 2019 },
  { label: '2020s', yearFrom: 2020, yearTo: 2029 },
];
