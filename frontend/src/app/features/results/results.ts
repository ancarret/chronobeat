import { DecimalPipe } from '@angular/common';
import { Component, inject, signal } from '@angular/core';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { forkJoin } from 'rxjs';
import { GameService } from '../../core/services/game.service';
import { ApiError, GameResults, TimelineEntry } from '../../core/models/game.model';
import { LoadingState } from '../../shared/components/loading-state/loading-state';
import { ErrorState } from '../../shared/components/error-state/error-state';
import { Timeline } from '../game/components/timeline/timeline';

@Component({
  selector: 'app-results',
  imports: [RouterLink, DecimalPipe, LoadingState, ErrorState, Timeline],
  templateUrl: './results.html',
  styleUrl: './results.scss',
})
export class Results {
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);
  private readonly gameService = inject(GameService);

  private readonly gameId = this.route.snapshot.paramMap.get('gameId')!;

  protected readonly results = signal<GameResults | null>(null);
  protected readonly error = signal<ApiError | null>(null);
  /** Final timeline per player id; fills in after the stats, which are the essential content. */
  protected readonly timelines = signal<Record<string, TimelineEntry[]>>({});

  constructor() {
    this.load();
  }

  private load(): void {
    this.error.set(null);
    this.gameService.getResults(this.gameId).subscribe({
      next: (results) => {
        this.results.set(results);
        this.loadTimelines(results);
      },
      error: (err: ApiError) => this.error.set(err),
    });
  }

  private loadTimelines(results: GameResults): void {
    forkJoin(results.players.map((p) => this.gameService.getTimeline(this.gameId, p.id))).subscribe({
      next: (lists) => this.timelines.set(Object.fromEntries(results.players.map((p, i) => [p.id, lists[i]]))),
      // A missing recap isn't worth an error screen over the scores that already loaded.
      error: () => undefined,
    });
  }

  protected retry(): void {
    this.load();
  }

  protected playAgain(): void {
    this.router.navigate(['/play']);
  }

  protected sortedPlayers(results: GameResults) {
    return [...results.players].sort((a, b) => b.score - a.score);
  }
}
