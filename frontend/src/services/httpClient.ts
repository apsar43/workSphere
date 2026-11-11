import axios, { type InternalAxiosRequestConfig } from 'axios';

import { environment } from '@/config/environment';
import { toApiClientError } from '@/lib/apiError';

const MUTATING_METHODS = new Set(['post', 'put', 'patch', 'delete']);

function readCookie(name: string): string | undefined {
  return document.cookie
    .split('; ')
    .find((entry) => entry.startsWith(`${name}=`))
    ?.slice(name.length + 1);
}

function readCsrfToken(): string | undefined {
  const token = readCookie('__Host-WS-XSRF-TOKEN') ?? readCookie('WS-XSRF-TOKEN');
  return token === undefined ? undefined : decodeURIComponent(token);
}

const csrfClient = axios.create({
  baseURL: environment.apiBaseUrl,
  timeout: 10_000,
  withCredentials: true,
});

export const httpClient = axios.create({
  baseURL: environment.apiBaseUrl,
  timeout: 15_000,
  withCredentials: true,
  headers: { Accept: 'application/json' },
});

async function attachCsrfToken(config: InternalAxiosRequestConfig) {
  if (!MUTATING_METHODS.has(config.method?.toLowerCase() ?? '')) return config;

  let token = readCsrfToken();
  if (token === undefined) {
    await csrfClient.get('/auth/csrf');
    token = readCsrfToken();
  }
  if (token === undefined) {
    throw new Error('The security token could not be established. Refresh and try again.');
  }
  config.headers.set('X-XSRF-TOKEN', token);
  return config;
}

httpClient.interceptors.request.use(attachCsrfToken);
httpClient.interceptors.response.use(
  (response) => response,
  (error: unknown) => Promise.reject(toApiClientError(error)),
);
