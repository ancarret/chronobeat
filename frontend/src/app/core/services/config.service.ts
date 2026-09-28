import { Injectable } from '@angular/core';

interface RuntimeEnv {
  apiBaseUrl?: string;
  productName?: string;
}

declare global {
  interface Window {
    __env?: RuntimeEnv;
  }
}

/**
 * Reads deploy-time configuration from window.__env (see assets/env.js), instead
 * of baking values into the JS bundle at build time. This lets the same built
 * artifact (and the same Docker image) point at a different backend per
 * environment just by swapping that one small file.
 */
@Injectable({ providedIn: 'root' })
export class ConfigService {
  readonly apiBaseUrl: string = window.__env?.apiBaseUrl ?? 'http://localhost:8080';

  /** Product name is intentionally not hardcoded around the app - change it here. */
  readonly productName: string = window.__env?.productName ?? 'Chronobeat';
}
