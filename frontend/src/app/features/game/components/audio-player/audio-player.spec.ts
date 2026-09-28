import { TestBed } from '@angular/core/testing';
import { vi } from 'vitest';
import { AudioPlayer } from './audio-player';

describe('AudioPlayer', () => {
  beforeEach(async () => {
    await TestBed.configureTestingModule({ imports: [AudioPlayer] }).compileComponents();
  });

  function create(previewUrl: string, maxPlaySeconds = 12) {
    const fixture = TestBed.createComponent(AudioPlayer);
    fixture.componentRef.setInput('previewUrl', previewUrl);
    fixture.componentRef.setInput('maxPlaySeconds', maxPlaySeconds);
    fixture.detectChanges();
    return fixture;
  }

  it('caps playback at maxPlaySeconds: pauses and resets once the cap is reached', () => {
    const fixture = create('https://example.com/preview.m4a', 12);
    const component = fixture.componentInstance;
    const audio: HTMLAudioElement = fixture.nativeElement.querySelector('audio');

    const pauseSpy = vi.spyOn(audio, 'pause');
    Object.defineProperty(audio, 'currentTime', { value: 12, writable: true, configurable: true });
    (component as any).isPlaying.set(true);

    component.onTimeUpdate();

    expect(pauseSpy).toHaveBeenCalled();
    expect(audio.currentTime).toBe(0);
    expect((component as any).isPlaying()).toBe(false);
    expect((component as any).progress()).toBe(0);
  });

  it('reports progress as a 0..1 fraction of maxPlaySeconds while under the cap', () => {
    const fixture = create('https://example.com/preview.m4a', 10);
    const component = fixture.componentInstance;
    const audio: HTMLAudioElement = fixture.nativeElement.querySelector('audio');

    Object.defineProperty(audio, 'currentTime', { value: 5, writable: true, configurable: true });
    component.onTimeUpdate();

    expect((component as any).progress()).toBe(0.5);
  });

  it('resets playback state whenever previewUrl changes (a new round starts)', () => {
    const fixture = create('https://example.com/round1.m4a', 12);
    const component = fixture.componentInstance;
    (component as any).isPlaying.set(true);
    (component as any).progress.set(0.6);

    component.ngOnChanges({
      previewUrl: {
        previousValue: 'https://example.com/round1.m4a',
        currentValue: 'https://example.com/round2.m4a',
        firstChange: false,
        isFirstChange: () => false,
      },
    });

    expect((component as any).isPlaying()).toBe(false);
    expect((component as any).progress()).toBe(0);
  });
});
