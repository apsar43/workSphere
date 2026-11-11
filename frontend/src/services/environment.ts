const defaultApiBaseUrl = '/api/v1';

export const environment = Object.freeze({
  apiBaseUrl: import.meta.env.VITE_API_BASE_URL ?? defaultApiBaseUrl,
});
