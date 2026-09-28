import { DecimalPipe } from '@angular/common';
import { Component, inject, signal } from '@angular/core';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { GameService } from '../../core/services/game.service';
import { ApiError, GameResults } from '../../core/models/game.model';
import { LoadingState } from '../../shared/components/loading-state/loading-state';
import { ErrorState } from '../../shared/components/error-state/error-state';

@Component({
  selector: 'app-results',
  imports: [RouterLink, DecimalPipe, LoadingState, ErrorState],
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

  constructor() {
    this.load();
  }

  private load(): void {
    this.error.set(null);
    this.gameService.getResults(this.gameId).subscribe({
      next: (results) => this.results.set(results),
      error: (err: ApiError) => this.error.set(err),
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
