import { Component, inject } from '@angular/core';
import { RouterLink } from '@angular/router';
import { ConfigService } from '../../core/services/config.service';
import { Vinyl } from '../../shared/components/vinyl/vinyl';

@Component({
  selector: 'app-home',
  imports: [RouterLink, Vinyl],
  templateUrl: './home.html',
  styleUrl: './home.scss',
})
export class Home {
  protected readonly config = inject(ConfigService);
}
