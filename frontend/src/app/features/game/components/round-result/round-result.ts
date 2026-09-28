import { Component, input, output } from '@angular/core';
import { RoundResult as RoundResultModel } from '../../../../core/models/game.model';

@Component({
  selector: 'app-round-result',
  templateUrl: './round-result.html',
  styleUrl: './round-result.scss',
})
export class RoundResult {
  readonly result = input.required<RoundResultModel>();
  readonly isGameOver = input(false);
  readonly continue = output<void>();
}
