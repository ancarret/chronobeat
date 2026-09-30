import { HttpInterceptorFn } from '@angular/common/http';
import { inject } from '@angular/core';
import { ConfigService } from './config.service';
import { OnlineSessionService } from './online-session.service';

export const PLAYER_TOKEN_HEADER = 'X-Player-Token';

const GAME_PATH = /\/api\/games\/([0-9a-fA-F-]{36})(?:[/?]|$)/;

/**
 * Presents this browser's seat token on calls about an online game it has joined, so the rest of the
 * app can keep calling the game API without knowing tokens exist. Only our own API ever receives it.
 */
export const playerInterceptor: HttpInterceptorFn = (req, next) => {
  if (!req.url.startsWith(inject(ConfigService).apiBaseUrl) || req.headers.has(PLAYER_TOKEN_HEADER)) {
    return next(req);
  }
  const gameId = GAME_PATH.exec(req.url)?.[1];
  const session = gameId ? inject(OnlineSessionService).forGame(gameId) : null;
  return session ? next(req.clone({ setHeaders: { [PLAYER_TOKEN_HEADER]: session.playerToken } })) : next(req);
};
