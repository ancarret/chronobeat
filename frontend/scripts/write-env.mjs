// Cloudflare Pages has no container entrypoint, so docker-entrypoint.sh can't regenerate env.js
// there. This runs right after `ng build` and writes the same file from the API_BASE_URL build
// variable set in the Cloudflare dashboard. It fails the build rather than silently shipping a
// bundle that points at localhost.
import { writeFileSync } from 'node:fs';

const raw = process.env.API_BASE_URL;
if (!raw) {
  console.error('API_BASE_URL is not set. Set it in the Cloudflare build variables (e.g. https://chronobeat-backend.onrender.com).');
  process.exit(1);
}

const apiBaseUrl = raw.trim().replace(/\/+$/, '');
if (!/^https:\/\/[^/\s]+$/.test(apiBaseUrl)) {
  console.error(`API_BASE_URL must be an https origin without a path, got "${raw}".`);
  process.exit(1);
}

const productName = process.env.PRODUCT_NAME?.trim() || 'Chronobeat';
const target = 'dist/frontend/browser/env.js';
writeFileSync(
  target,
  `window.__env = ${JSON.stringify({ apiBaseUrl, productName }, null, 2)};\n`,
);
console.log(`Wrote ${target} -> ${apiBaseUrl}`);
