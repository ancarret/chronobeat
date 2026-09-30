import { TestBed } from '@angular/core/testing';
import { entry, player, result, reveal, summary } from '../../../../testing/fixtures';
import { RoundResult } from './round-result';

describe('RoundResult', () => {
  beforeEach(async () => {
    await TestBed.configureTestingModule({ imports: [RoundResult] }).compileComponents();
  });

  function create(inputs: Record<string, unknown>) {
    const fixture = TestBed.createComponent(RoundResult);
    for (const [key, value] of Object.entries(inputs)) {
      fixture.componentRef.setInput(key, value);
    }
    fixture.detectChanges();
    return fixture;
  }

  const text = (fixture: { nativeElement: HTMLElement }) => fixture.nativeElement.textContent ?? '';

  it('reveals the song and celebrates a correct placement', () => {
    const fixture = create({
      summary: summary([result({ reveal: reveal({ title: 'Take On Me', artist: 'a-ha', year: 1985 }) })]),
    });

    expect(text(fixture)).toContain('Take On Me');
    expect(text(fixture)).toContain('1985');
    expect(text(fixture)).toContain('Correct!');
    expect(fixture.nativeElement.querySelector('.reveal-card.correct')).not.toBeNull();
    expect(fixture.nativeElement.querySelector('app-confetti')).not.toBeNull();
    expect(fixture.nativeElement.querySelector('.entry.fresh')).not.toBeNull();
  });

  it('marks a miss on the timeline and shows where the song belonged', () => {
    const fixture = create({
      summary: summary([result({ correct: false, submittedPosition: 0, validPositions: [2], timeline: [entry(1977), entry(1991)] })]),
    });

    expect(fixture.nativeElement.querySelector('.reveal-card.incorrect')).not.toBeNull();
    expect(text(fixture)).toContain("That one didn't make it into your timeline.");
    expect(fixture.nativeElement.querySelector('.marker.miss')).not.toBeNull();
    expect(fixture.nativeElement.querySelector('.marker.hint')).not.toBeNull();
    expect(fixture.nativeElement.querySelector('app-confetti')).toBeNull();
  });

  it('says so when the player ran out of time', () => {
    const fixture = create({
      summary: summary([result({ correct: false, submittedPosition: null, validPositions: [1] })]),
    });

    expect(text(fixture)).toContain('Out of time');
    expect(text(fixture)).toContain('Time ran out before you placed it.');
  });

  it('does not celebrate the free first card', () => {
    const fixture = create({ summary: summary([result({ anchorRound: true, submittedPosition: 0, timeline: [entry(1985)] })]) });

    expect(text(fixture)).toContain('First card');
    expect(fixture.nativeElement.querySelector('app-confetti')).toBeNull();
  });

  it('celebrates nailing the song even when the placement missed', () => {
    const fixture = create({ summary: summary([result({ correct: false, guessCorrect: true })]) });

    expect(fixture.nativeElement.querySelector('app-confetti')).not.toBeNull();
    expect(text(fixture)).toContain('Nailed the song');
  });

  describe('at a table', () => {
    const ana = player({ id: 'a', displayName: 'Ana', playerOrder: 0, score: 100 });
    const bea = player({ id: 'b', displayName: 'Bea', playerOrder: 1 });
    const table = summary([
      result({ roundId: 'ra', correct: true, player: ana }),
      result({ roundId: 'rb', correct: false, submittedPosition: 0, player: bea }),
    ]);

    it('shows every player their own row, the viewer first', () => {
      const fixture = create({ summary: table, viewerId: 'b' });

      const names = Array.from(fixture.nativeElement.querySelectorAll('.player-result .who strong')).map((e) => (e as HTMLElement).textContent);
      expect(names).toEqual(['Bea', 'Ana']);
      expect(fixture.nativeElement.querySelector('.player-result.you')).not.toBeNull();
    });

    it("colours the song card by the viewer's own outcome", () => {
      expect(create({ summary: table, viewerId: 'a' }).nativeElement.querySelector('.reveal-card.correct')).not.toBeNull();
      expect(create({ summary: table, viewerId: 'b' }).nativeElement.querySelector('.reveal-card.incorrect')).not.toBeNull();
    });

    it('stays neutral on a shared device, where the whole table is looking', () => {
      const fixture = create({ summary: table, viewerId: null });

      expect(fixture.nativeElement.querySelector('.reveal-card.correct')).toBeNull();
      expect(fixture.nativeElement.querySelector('.reveal-card.incorrect')).toBeNull();
      expect(text(fixture)).toContain('Round 1');
      expect(fixture.nativeElement.querySelector('app-confetti')).not.toBeNull(); // somebody got it
    });
  });

  describe('moving on', () => {
    it('offers the next round, or the results once the game is over', () => {
      expect(text(create({ summary: summary() }))).toContain('Next round');
      expect(text(create({ summary: summary(), isGameOver: true }))).toContain('See results');
    });

    it('emits continue, unless it is held back', () => {
      const fixture = create({ summary: summary() });
      let clicks = 0;
      fixture.componentInstance.continue.subscribe(() => clicks++);
      const button = fixture.nativeElement.querySelector('.continue-btn') as HTMLButtonElement;

      button.click();
      expect(clicks).toBe(1);

      fixture.componentRef.setInput('canContinue', false);
      fixture.detectChanges();
      expect(button.disabled).toBe(true);
    });

    it('shows the countdown to the automatic next round, but not on the last one', () => {
      expect(text(create({ summary: summary(), countdown: 7 }))).toContain('Next round in 7s');
      expect(text(create({ summary: summary(), countdown: 7, isGameOver: true }))).not.toContain('Next round in');
      expect(text(create({ summary: summary(), countdown: null }))).not.toContain('Next round in');
    });
  });
});
