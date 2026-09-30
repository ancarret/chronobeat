import { Component, ElementRef, OnChanges, SimpleChanges, ViewChild, input, output, signal } from '@angular/core';

/**
 * Custom playback UI over a hidden native <audio> element (no ugly native
 * controls). Playback is capped at maxPlaySeconds even though the underlying
 * preview file may run longer, per the game's "short preview only" rule.
 * play() is only ever called from a click handler, respecting browser
 * autoplay restrictions.
 */
@Component({
  selector: 'app-audio-player',
  templateUrl: './audio-player.html',
  styleUrl: './audio-player.scss',
})
export class AudioPlayer implements OnChanges {
  readonly previewUrl = input.required<string>();
  readonly maxPlaySeconds = input<number>(12);
  /** Emits whenever playback starts or stops, so the surrounding UI (e.g. the spinning record) can follow it. */
  readonly playingChange = output<boolean>();

  @ViewChild('audioEl') private audioEl?: ElementRef<HTMLAudioElement>;

  protected readonly isPlaying = signal(false);
  protected readonly hasPlayed = signal(false);
  protected readonly progress = signal(0); // 0..1
  protected readonly loadError = signal(false);

  ngOnChanges(changes: SimpleChanges): void {
    if (changes['previewUrl']) {
      this.setPlaying(false);
      this.hasPlayed.set(false);
      this.progress.set(0);
      this.loadError.set(false);
      this.audioEl?.nativeElement.pause();
      if (this.audioEl) {
        this.audioEl.nativeElement.currentTime = 0;
      }
    }
  }

  private setPlaying(playing: boolean): void {
    if (this.isPlaying() === playing) return;
    this.isPlaying.set(playing);
    this.playingChange.emit(playing);
  }

  toggle(): void {
    const audio = this.audioEl?.nativeElement;
    if (!audio) return;

    if (this.isPlaying()) {
      audio.pause();
      this.setPlaying(false);
      return;
    }

    audio
      .play()
      .then(() => {
        this.setPlaying(true);
        this.hasPlayed.set(true);
      })
      .catch(() => this.loadError.set(true));
  }

  replay(): void {
    const audio = this.audioEl?.nativeElement;
    if (!audio) return;
    audio.currentTime = 0;
    this.progress.set(0);
    this.toggle();
  }

  onTimeUpdate(): void {
    const audio = this.audioEl?.nativeElement;
    if (!audio) return;
    const cap = this.maxPlaySeconds();
    this.progress.set(Math.min(audio.currentTime / cap, 1));
    if (audio.currentTime >= cap) {
      audio.pause();
      audio.currentTime = 0;
      this.setPlaying(false);
      this.progress.set(0);
    }
  }

  onEnded(): void {
    this.setPlaying(false);
    this.progress.set(0);
  }

  onError(): void {
    this.loadError.set(true);
    this.setPlaying(false);
  }
}
