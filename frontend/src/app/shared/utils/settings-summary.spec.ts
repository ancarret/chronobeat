import { settings } from '../../testing/fixtures';
import { summarizeSettings } from './settings-summary';

describe('summarizeSettings', () => {
  it('describes the classic rules', () => {
    expect(summarizeSettings(settings())).toEqual(['Take turns', '3 lives', 'Normal']);
  });

  it('spells out a race on shared songs with a clock', () => {
    const labels = summarizeSettings(
      settings({ playStyle: 'SHARED_SONGS', targetTimelineSize: 10, answerSeconds: 45, maxLives: 1, difficulty: 'HARD' }),
    );
    expect(labels).toEqual(['Same songs', 'First to 10 cards', '45s per round', '1 life', 'Hard']);
  });

  it('includes the song pool filters only when they narrow it', () => {
    const labels = summarizeSettings(settings({ genre: 'ROCK', yearFrom: 1980, yearTo: 1989, market: 'ES' }));
    expect(labels).toEqual(expect.arrayContaining(['Rock', '1980–1989', 'ES']));
    expect(summarizeSettings(settings())).not.toContain('ES');
  });
});
