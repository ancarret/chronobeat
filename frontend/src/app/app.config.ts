import { ApplicationConfig, provideBrowserGlobalErrorListeners } from '@angular/core';
import { provideHttpClient, withInterceptors } from '@angular/common/http';
import { provideRouter } from '@angular/router';

import { routes } from './app.routes';
import { errorInterceptor } from './core/services/error.interceptor';
import { playerInterceptor } from './core/services/player.interceptor';
import { profileInterceptor } from './core/services/profile.interceptor';

export const appConfig: ApplicationConfig = {
  providers: [
    provideBrowserGlobalErrorListeners(),
    provideRouter(routes),
    provideHttpClient(withInterceptors([profileInterceptor, playerInterceptor, errorInterceptor])),
  ],
};
