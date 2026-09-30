import { Routes } from '@angular/router';

export const routes: Routes = [
  {
    path: '',
    loadComponent: () => import('./features/home/home').then((m) => m.Home),
  },
  {
    path: 'play',
    loadComponent: () => import('./features/game-setup/game-setup').then((m) => m.GameSetup),
  },
  {
    path: 'join',
    loadComponent: () => import('./features/join/join').then((m) => m.Join),
  },
  {
    path: 'join/:code',
    loadComponent: () => import('./features/join/join').then((m) => m.Join),
  },
  {
    path: 'room/:gameId',
    loadComponent: () => import('./features/lobby/lobby').then((m) => m.Lobby),
  },
  {
    path: 'game/:gameId',
    loadComponent: () => import('./features/game/game').then((m) => m.Game),
  },
  {
    path: 'game/:gameId/results',
    loadComponent: () => import('./features/results/results').then((m) => m.Results),
  },
  {
    path: 'profile',
    loadComponent: () => import('./features/profile/profile').then((m) => m.Profile),
  },
  {
    path: '**',
    redirectTo: '',
  },
];
