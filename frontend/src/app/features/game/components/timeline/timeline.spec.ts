import { TestBed } from '@angular/core/testing';
import { TimelineEntry } from '../../../../core/models/game.model';
import { Timeline } from './timeline';

const entry = (year: number): TimelineEntry => ({
  songId: `song-${year}`,
  title: `Song ${year}`,
  artist: 'Someone',
  album: null,
  year,
  artworkUrl: null,
  genre: 'POP',
  position: 0,
});

describe('Timeline', () => {
  beforeEach(async () => {
    await TestBed.configureTestingModule({ imports: [Timeline] }).compileComponents();
  });

  function create(entries: TimelineEntry[], interactive = true) {
    const fixture = TestBed.createComponent(Timeline);
    fixture.componentRef.setInput('entries', entries);
    fixture.componentRef.setInput('interactive', interactive);
    fixture.detectChanges();
    return fixture;
  }

  it('offers exactly N+1 insertion slots for N timeline entries', () => {
    const fixture = create([entry(1977), entry(1991), entry(2008)]);
    const slots = fixture.nativeElement.querySelectorAll('.slot');
    expect(slots.length).toBe(4);
  });

  it('does not render insertion slots when non-interactive', () => {
    const fixture = create([entry(1977)], false);
    const slots = fixture.nativeElement.querySelectorAll('.slot');
    expect(slots.length).toBe(0);
  });

  it('emits the clicked slot index via positionSelected', () => {
    const fixture = create([entry(1977), entry(1991)]);
    const emitted: number[] = [];
    fixture.componentInstance.positionSelected.subscribe((i) => emitted.push(i));

    const slots: HTMLButtonElement[] = Array.from(fixture.nativeElement.querySelectorAll('.slot'));
    slots[1].click();

    expect(emitted).toEqual([1]);
  });

  it('grows the selected slot into a ghost card', () => {
    const fixture = create([entry(1977), entry(1991)]);
    fixture.componentRef.setInput('selectedPosition', 2);
    fixture.detectChanges();

    const slots: HTMLElement[] = Array.from(fixture.nativeElement.querySelectorAll('.slot'));
    expect(slots[2].classList.contains('selected')).toBe(true);
    expect(slots[2].querySelector('.ghost')).not.toBeNull();
    expect(slots[0].querySelector('.ghost')).toBeNull();
  });

  it('highlights the freshly added entry on a correct reveal', () => {
    const fixture = create([entry(1977), entry(1985), entry(1991)], false);
    fixture.componentRef.setInput('freshPosition', 1);
    fixture.detectChanges();

    const cards: HTMLElement[] = Array.from(fixture.nativeElement.querySelectorAll('.entry'));
    expect(cards.map((c) => c.classList.contains('fresh'))).toEqual([false, true, false]);
  });

  it('marks the wrong pick and where the song belonged on a missed reveal', () => {
    const fixture = create([entry(1977), entry(1991)], false);
    fixture.componentRef.setInput('missPosition', 0);
    fixture.componentRef.setInput('hintPositions', [2]);
    fixture.detectChanges();

    expect(fixture.nativeElement.querySelectorAll('.marker.miss').length).toBe(1);
    expect(fixture.nativeElement.querySelectorAll('.marker.hint').length).toBe(1);
    // Markers sit in the slot positions: miss before the first card, hint after the last.
    const children: HTMLElement[] = Array.from(fixture.nativeElement.querySelector('.timeline').children);
    expect(children[0].classList.contains('miss')).toBe(true);
    expect(children[children.length - 1].classList.contains('hint')).toBe(true);
  });

  it('ignores clicks when not interactive (select() is a no-op)', () => {
    const fixture = create([entry(1977)], false);
    const emitted: number[] = [];
    fixture.componentInstance.positionSelected.subscribe((i) => emitted.push(i));

    fixture.componentInstance.select(0);

    expect(emitted).toEqual([]);
  });
});
