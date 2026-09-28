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

  it('ignores clicks when not interactive (select() is a no-op)', () => {
    const fixture = create([entry(1977)], false);
    const emitted: number[] = [];
    fixture.componentInstance.positionSelected.subscribe((i) => emitted.push(i));

    fixture.componentInstance.select(0);

    expect(emitted).toEqual([]);
  });
});
