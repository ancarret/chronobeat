import { Component } from '@angular/core';

interface Piece {
  x: number;
  drift: number;
  delay: number;
  duration: number;
  rotation: number;
  size: number;
  color: string;
}

const COLORS = ['#ff6b4a', '#8b5cf6', '#34d399', '#fbbf24', '#f4f2fb'];

/**
 * One-shot celebratory burst. Pure CSS animation (no canvas, no library): each
 * piece gets its own randomized trajectory through custom properties, and the
 * overlay ignores pointer events so it never blocks the UI underneath. The end
 * state of every piece is fully transparent, which is what
 * prefers-reduced-motion users get instantly.
 */
@Component({
  selector: 'app-confetti',
  template: `
    <div class="confetti" aria-hidden="true">
      @for (p of pieces; track $index) {
        <span
          class="piece"
          [style.left.%]="p.x"
          [style.width.px]="p.size"
          [style.height.px]="p.size * 0.5"
          [style.background]="p.color"
          [style.--drift.px]="p.drift"
          [style.--rot.deg]="p.rotation"
          [style.animation-delay.ms]="p.delay"
          [style.animation-duration.ms]="p.duration"
        ></span>
      }
    </div>
  `,
  styleUrl: './confetti.scss',
})
export class Confetti {
  protected readonly pieces: Piece[] = Array.from({ length: 36 }, () => ({
    x: 15 + Math.random() * 70,
    drift: (Math.random() - 0.5) * 320,
    delay: Math.random() * 220,
    duration: 1400 + Math.random() * 1000,
    rotation: 240 + Math.random() * 600,
    size: 6 + Math.random() * 8,
    color: COLORS[Math.floor(Math.random() * COLORS.length)],
  }));
}
