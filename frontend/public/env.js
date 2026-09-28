// Runtime configuration, loaded before the Angular bundle (see index.html).
// In Docker this file is regenerated from the API_BASE_URL env var at container
// startup (see frontend/docker-entrypoint.sh) so one built image works across
// environments without rebuilding. This checked-in copy is the local-dev default.
window.__env = {
  apiBaseUrl: 'http://localhost:8080',
  productName: 'Chronobeat',
};
