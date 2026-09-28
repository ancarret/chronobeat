#!/bin/sh
# Regenerates env.js from container env vars at STARTUP (not at image build
# time), so the same built image can point at a different backend in every
# environment without rebuilding. See src/index.html / ConfigService.
set -e

cat > /usr/share/nginx/html/env.js <<EOF
window.__env = {
  apiBaseUrl: "${API_BASE_URL:-http://localhost:8080}",
  productName: "${PRODUCT_NAME:-Chronobeat}"
};
EOF

exec "$@"
