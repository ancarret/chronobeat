import { HttpErrorResponse, HttpInterceptorFn } from '@angular/common/http';
import { catchError, throwError } from 'rxjs';
import { ApiError } from '../models/game.model';

/** Normalizes every failed API call into the backend's ApiError shape (or a generic fallback for network failures). */
export const errorInterceptor: HttpInterceptorFn = (req, next) =>
  next(req).pipe(
    catchError((error: unknown) => {
      if (error instanceof HttpErrorResponse) {
        const apiError: ApiError = error.error?.message
          ? error.error
          : {
              timestamp: new Date().toISOString(),
              status: error.status,
              error: error.status === 0 ? 'Network Error' : 'Error',
              message:
                error.status === 0
                  ? 'Could not reach the server. Check your connection and that the backend is running.'
                  : 'Something went wrong. Please try again.',
              details: [],
            };
        return throwError(() => apiError);
      }
      return throwError(() => error);
    }),
  );
