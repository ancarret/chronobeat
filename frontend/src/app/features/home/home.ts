import { DecimalPipe } from '@angular/common';
import { Component, inject, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { ProfileStats } from '../../core/models/profile.model';
import { ConfigService } from '../../core/services/config.service';
import { ProfileService } from '../../core/services/profile.service';
import { Vinyl } from '../../shared/components/vinyl/vinyl';

@Component({
  selector: 'app-home',
  imports: [RouterLink, DecimalPipe, Vinyl],
  templateUrl: './home.html',
  styleUrl: './home.scss',
})
export class Home {
  protected readonly config = inject(ConfigService);
  private readonly profiles = inject(ProfileService);

  /** Returning players see their record before they even start; failures are silent, it's a nicety. */
  protected readonly stats = signal<ProfileStats | null>(null);

  constructor() {
    if (this.profiles.profile()) {
      this.profiles.stats().subscribe({
        next: (stats) => this.stats.set(stats),
        error: () => undefined,
      });
    }
  }
}
