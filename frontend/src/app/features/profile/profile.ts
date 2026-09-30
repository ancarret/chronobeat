import { DatePipe, DecimalPipe } from '@angular/common';
import { Component, computed, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { GENRE_LABELS, MusicGenre } from '../../core/models/enums';
import { ApiError } from '../../core/models/game.model';
import { AccuracyBucket, ProfileStats } from '../../core/models/profile.model';
import { ProfileService } from '../../core/services/profile.service';
import { ErrorState } from '../../shared/components/error-state/error-state';
import { LoadingState } from '../../shared/components/loading-state/loading-state';

/** A bucket needs at least this many rounds before it can be called a strength or a weakness. */
const MIN_ROUNDS_FOR_INSIGHT = 3;

interface Bar {
  label: string;
  correct: number;
  total: number;
  accuracy: number;
}

@Component({
  selector: 'app-profile',
  imports: [RouterLink, DecimalPipe, DatePipe, FormsModule, LoadingState, ErrorState],
  templateUrl: './profile.html',
  styleUrl: './profile.scss',
})
export class Profile {
  private readonly profiles = inject(ProfileService);

  protected readonly profile = this.profiles.profile;
  protected readonly stats = signal<ProfileStats | null>(null);
  protected readonly error = signal<ApiError | null>(null);

  protected readonly editing = signal(false);
  protected readonly nicknameDraft = signal('');
  protected readonly savingName = signal(false);

  protected readonly showCode = signal(false);
  protected readonly copied = signal(false);

  protected readonly restoreCode = signal('');
  protected readonly restoring = signal(false);
  protected readonly restoreError = signal<string | null>(null);

  protected readonly decadeBars = computed<Bar[]>(() =>
    (this.stats()?.byDecade ?? []).map((b) => this.toBar(b, `${b.key}s`)),
  );
  protected readonly genreBars = computed<Bar[]>(() =>
    (this.stats()?.byGenre ?? [])
      .map((b) => this.toBar(b, GENRE_LABELS[b.key as MusicGenre] ?? b.key))
      .sort((a, b) => b.total - a.total),
  );

  /** Best and worst decade among those with enough rounds to be meaningful. */
  protected readonly strongestDecade = computed(() => this.extreme(this.decadeBars(), 'best'));
  protected readonly weakestDecade = computed(() => this.extreme(this.decadeBars(), 'worst'));

  protected readonly initial = computed(() => (this.profile()?.nickname.trim().charAt(0) || '?').toUpperCase());

  constructor() {
    if (this.profile()) {
      this.loadStats();
    }
  }

  private loadStats(): void {
    this.error.set(null);
    this.stats.set(null);
    this.profiles.stats().subscribe({
      next: (stats) => this.stats.set(stats),
      // A 401 already dropped the local profile; the template then shows the signed-out state.
      error: (err: ApiError) => (this.profile() ? this.error.set(err) : undefined),
    });
  }

  protected retry(): void {
    this.loadStats();
  }

  protected startRename(): void {
    this.nicknameDraft.set(this.profile()?.nickname ?? '');
    this.editing.set(true);
  }

  protected saveRename(): void {
    const name = this.nicknameDraft().trim();
    if (!name || this.savingName()) return;
    this.savingName.set(true);
    this.profiles.rename(name).subscribe({
      next: () => {
        this.editing.set(false);
        this.savingName.set(false);
      },
      error: () => this.savingName.set(false),
    });
  }

  protected async copyCode(): Promise<void> {
    const token = this.profile()?.token;
    if (!token) return;
    try {
      await navigator.clipboard.writeText(token);
      this.copied.set(true);
      setTimeout(() => this.copied.set(false), 2000);
    } catch {
      // Clipboard blocked: the code is on screen and selectable, so the user can still copy it by hand.
    }
  }

  protected restore(): void {
    const code = this.restoreCode().trim();
    if (!code || this.restoring()) return;
    this.restoring.set(true);
    this.restoreError.set(null);
    this.profiles.restore(code).subscribe({
      next: () => {
        this.restoring.set(false);
        this.restoreCode.set('');
        this.showCode.set(false);
        this.loadStats();
      },
      error: () => {
        this.restoring.set(false);
        this.restoreError.set("That code doesn't match any profile.");
      },
    });
  }

  protected signOut(): void {
    this.profiles.signOut();
    this.stats.set(null);
    this.showCode.set(false);
  }

  private toBar(bucket: AccuracyBucket, label: string): Bar {
    return { label, correct: bucket.correct, total: bucket.total, accuracy: bucket.accuracy };
  }

  private extreme(bars: Bar[], kind: 'best' | 'worst'): Bar | null {
    const eligible = bars.filter((b) => b.total >= MIN_ROUNDS_FOR_INSIGHT);
    // Best and worst are only distinct when there are at least two eligible buckets to compare.
    if (eligible.length < 2) return null;
    return eligible.reduce((acc, b) =>
      kind === 'best' ? (b.accuracy > acc.accuracy ? b : acc) : b.accuracy < acc.accuracy ? b : acc,
    );
  }
}
