import { TestBed } from '@angular/core/testing';
import { Player } from '../../../../core/models/game.model';
import { ScorePanel } from './score-panel';

const player = (overrides: Partial<Player> = {}): Player => ({
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
  ...overrides,
});

describe('ScorePanel', () => {
  beforeEach(async () => {
    await TestBed.configureTestingModule({ imports: [ScorePanel] }).compileComponents();
  });

  function create(p: Player, maxLives: number) {
    const fixture = TestBed.createComponent(ScorePanel);
    fixture.componentRef.setInput('player', p);
    fixture.componentRef.setInput('roundNumber', 4);
    fixture.componentRef.setInput('maxLives', maxLives);
    fixture.detectChanges();
    return fixture;
  }

  it('draws lost lives as empty hearts', () => {
    const fixture = create(player({ livesRemaining: 1 }), 3);
    const hearts: HTMLElement[] = Array.from(fixture.nativeElement.querySelectorAll('.heart'));
    expect(hearts.map((h) => h.classList.contains('lost'))).toEqual([false, true, true]);
  });

  it('shows bonus lives beyond the starting total', () => {
    const fixture = create(player({ livesRemaining: 4 }), 3);
    expect(fixture.nativeElement.querySelectorAll('.heart').length).toBe(4);
    expect(fixture.nativeElement.querySelectorAll('.heart.lost').length).toBe(0);
  });

  it('shows the flame only from a streak of three', () => {
    expect(create(player({ currentStreak: 2 }), 3).nativeElement.querySelector('.flame')).toBeNull();
    expect(create(player({ currentStreak: 3 }), 3).nativeElement.querySelector('.flame')).not.toBeNull();
  });

  it('shows the round number', () => {
    const fixture = create(player(), 3);
    expect(fixture.nativeElement.textContent).toContain('Round 4');
  });
});
