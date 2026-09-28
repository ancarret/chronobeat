# Chronobeat — frontend

Angular client for [Chronobeat](../README.md). See the repo root README for architecture, setup, environment variables, and deployment instructions.

Quick reference for this package specifically:

```bash
npm install
npm start        # dev server on http://localhost:4200, proxies nothing - see public/env.js for the backend URL
npm test         # Vitest unit tests
npm run build    # production build to dist/frontend/browser
```

Runtime configuration (which backend to call) is read from `window.__env` at startup — see [`public/env.js`](public/env.js) and `src/app/core/services/config.service.ts` — rather than being baked into the build, so the same build works across environments.
