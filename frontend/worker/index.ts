interface AssetsBinding {
  fetch(request: Request): Promise<Response>;
}

interface Environment {
  API_ORIGIN: string;
  ASSETS: AssetsBinding;
}

const API_PREFIX = '/api/';
const MAX_DECLARED_BODY_BYTES = 1_048_576;

function apiRequest(request: Request, environment: Environment): Promise<Response> {
  const incomingUrl = new URL(request.url);
  const apiOrigin = new URL(environment.API_ORIGIN);
  const upstreamUrl = new URL(`${incomingUrl.pathname}${incomingUrl.search}`, apiOrigin);

  if (upstreamUrl.origin !== apiOrigin.origin) {
    return Promise.resolve(new Response('Invalid upstream request.', { status: 400 }));
  }

  const declaredLength = Number(request.headers.get('content-length') ?? '0');
  if (Number.isFinite(declaredLength) && declaredLength > MAX_DECLARED_BODY_BYTES) {
    return Promise.resolve(new Response('Request body is too large.', { status: 413 }));
  }

  const headers = new Headers(request.headers);
  headers.delete('host');
  headers.delete('cf-connecting-ip');
  headers.delete('x-forwarded-for');
  headers.set('X-Forwarded-Host', incomingUrl.host);
  headers.set('X-Forwarded-Proto', 'https');

  const upstreamRequest = new Request(upstreamUrl, {
    method: request.method,
    headers,
    body: request.body,
    redirect: 'manual',
  });

  return fetch(upstreamRequest).then((upstreamResponse) => {
    const responseHeaders = new Headers(upstreamResponse.headers);
    responseHeaders.set('Cache-Control', 'no-store');
    responseHeaders.set('Vary', 'Cookie');
    return new Response(upstreamResponse.body, {
      status: upstreamResponse.status,
      statusText: upstreamResponse.statusText,
      headers: responseHeaders,
    });
  });
}

export default {
  fetch(request: Request, environment: Environment): Promise<Response> {
    const url = new URL(request.url);
    if (url.pathname.startsWith(API_PREFIX)) {
      return apiRequest(request, environment);
    }
    return environment.ASSETS.fetch(request);
  },
};
