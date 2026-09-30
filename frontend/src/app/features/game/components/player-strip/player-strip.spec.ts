import { TestBed } from '@angular/core/testing';
import { player } from '../../../../testing/fixtures';
import { PlayerStrip } from './player-strip';

describe('PlayerStrip', () => {
  beforeEach(async () => {
    await TestBed.configureTestingModule({ imports: [PlayerStrip] }).compileComponents();
  });

  function create(inputs: Record<string, unknown>) {
    const fixture = TestBed.createComponent(PlayerStrip);
    for (const [key, value] of Object.entries(inputs)) {
      fixture.componentRef.setInput(key, value);
    }
    fixture.detectChanges();
    return fixture.nativeElement as HTMLElement;
  }

  const ana = player({ id: 'a', displayName: 'Ana', timelineSize: 4 });
  const bea = player({ id: 'b', displayName: 'Bea', timelineSize: 8, livesRemaining: 1 });

  it('draws one seat per player and marks the viewer', () => {
    const el = create({ players: [ana, bea], viewerId: 'a' });

    const seats = el.querySelectorAll('.seat');
    expect(seats).toHaveLength(2);
    expect(seats[0].classList.contains('you')).toBe(true);
    expect(seats[0].textContent).toContain('you');
    expect(seats[1].textContent).not.toContain('you');
  });

  it('shows the race towards the goal as progress bars', () => {
    const el = create({ players: [ana, bea], target: 10 });

    const fills = Array.from(el.querySelectorAll<HTMLElement>('.race-fill')).map((f) => f.style.width);
    expect(fills).toEqual(['40%', '80%']);
    expect(el.textContent).toContain('4 / 10 cards');
  });

  it('shows plain counts and no bar when there is no goal', () => {
    const el = create({ players: [ana, player({ id: 'c', displayName: 'Cy', timelineSize: 1 })] });

    expect(el.querySelector('.race')).toBeNull();
    expect(el.textContent).toContain('4 cards');
    expect(el.textContent).toContain('1 card');
  });

  it('caps progress at the goal', () => {
    const el = create({ players: [player({ id: 'x', timelineSize: 14 })], target: 10 });
    expect(el.querySelector<HTMLElement>('.race-fill')!.style.width).toBe('100%');
  });

  it('shows who has locked in and who is still thinking during a round', () => {
    const locked = player({ id: 'a', displayName: 'Ana', answered: true });
    const thinking = player({ id: 'b', displayName: 'Bea' });
    const el = create({ players: [locked, thinking], waitingOn: ['b'], showRoundStatus: true });

    const seats = el.querySelectorAll('.seat');
    expect(seats[0].querySelector('.status.locked')).not.toBeNull();
    expect(seats[1].querySelector('.status.thinking')).not.toBeNull();
  });

  it('hides round statuses when no round is open', () => {
    const el = create({ players: [player({ answered: true })], showRoundStatus: false });
    expect(el.querySelector('.status')).toBeNull();
  });

  it('flags eliminated players', () => {
    const el = create({ players: [player({ eliminated: true, livesRemaining: 0 })] });
    expect(el.querySelector('.seat')!.classList.contains('out')).toBe(true);
    expect(el.querySelector('.status.out')).not.toBeNull();
  });

  it('marks disconnected players only when presence is tracked', () => {
    const tracked = create({ players: [ana, bea], connectedIds: ['a'] });
    expect(tracked.querySelectorAll('.seat.offline')).toHaveLength(1);
    expect(tracked.textContent).toContain('offline');

    const untracked = create({ players: [ana, bea], connectedIds: null });
    expect(untracked.querySelector('.offline')).toBeNull();
  });
});
