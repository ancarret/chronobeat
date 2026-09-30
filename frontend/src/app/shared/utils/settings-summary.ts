import { GENRE_LABELS } from '../../core/models/enums';
import { GameSettings } from '../../core/models/game.model';

const DIFFICULTY_LABELS = { EASY: 'Easy', NORMAL: 'Normal', HARD: 'Hard' } as const;

/**
 * The house rules of a game as short labels ("Same songs", "First to 10", "45s per round"...),
 * for lobbies and join previews where players agree on what they're about to play.
 */
export function summarizeSettings(settings: GameSettings): string[] {
  const labels: string[] = [settings.playStyle === 'SHARED_SONGS' ? 'Same songs' : 'Take turns'];

  if (settings.targetTimelineSize) {
    labels.push(`First to ${settings.targetTimelineSize} cards`);
  }
  if (settings.answerSeconds) {
    labels.push(`${settings.answerSeconds}s per round`);
  }
  labels.push(`${settings.maxLives} ${settings.maxLives === 1 ? 'life' : 'lives'}`);
  labels.push(DIFFICULTY_LABELS[settings.difficulty]);
  if (settings.genre) {
    labels.push(GENRE_LABELS[settings.genre]);
  }
  if (settings.yearFrom !== null && settings.yearTo !== null) {
    labels.push(`${settings.yearFrom}–${settings.yearTo}`);
  }
  if (settings.market) {
    labels.push(settings.market);
  }
  return labels;
}
