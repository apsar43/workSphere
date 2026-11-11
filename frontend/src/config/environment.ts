function normalizeApiBaseUrl(value: string | undefined): string {
  const configuredValue = value?.trim();
  const candidate =
    configuredValue === undefined || configuredValue === '' ? '/api/v1' : configuredValue;
  return candidate.endsWith('/') ? candidate.slice(0, -1) : candidate;
}

export const environment = Object.freeze({
  apiBaseUrl: normalizeApiBaseUrl(import.meta.env.VITE_API_BASE_URL),
  isProduction: import.meta.env.PROD,
});
