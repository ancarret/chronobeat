import { HttpInterceptorFn } from '@angular/common/http';
import { inject } from '@angular/core';
import { ConfigService } from './config.service';
import { PROFILE_TOKEN_HEADER, ProfileService } from './profile.service';

/** Attaches the player's profile token to calls to our own API (and never to third-party URLs). */
export const profileInterceptor: HttpInterceptorFn = (req, next) => {
  const token = inject(ProfileService).token();
  const isOurApi = req.url.startsWith(inject(ConfigService).apiBaseUrl);
  if (!token || !isOurApi || req.headers.has(PROFILE_TOKEN_HEADER)) {
    return next(req);
  }
  return next(req.clone({ setHeaders: { [PROFILE_TOKEN_HEADER]: token } }));
};
