import MockAdapter from 'axios-mock-adapter';

import { authApi } from '@/services/authApi';
import { httpClient } from '@/services/httpClient';

const mock = new MockAdapter(httpClient);

describe('authApi', () => {
  beforeEach(() => {
    mock.reset();
    document.cookie = 'WS-XSRF-TOKEN=test-token; Path=/';
  });

  it('sends typed login and registration requests with CSRF', async () => {
    const user = {
      id: 'user-id',
      email: 'apsar@example.com',
      firstName: 'Apsar',
      lastName: 'Khan',
      displayName: 'Apsar Khan',
    };
    mock.onPost('/auth/login').reply(200, user);
    mock.onPost('/auth/register').reply(201, { message: 'Registration received.' });

    await expect(authApi.login(user.email, 'secure-password')).resolves.toEqual(user);
    await expect(authApi.register('Apsar', 'Khan', user.email, 'secure-password')).resolves.toEqual(
      { message: 'Registration received.' },
    );

    expect(mock.history.post[0]?.headers?.['X-XSRF-TOKEN']).toBe('test-token');
  });

  it('handles current-user, anonymous, and logout responses', async () => {
    const user = {
      id: 'user-id',
      email: 'apsar@example.com',
      firstName: 'Apsar',
      lastName: 'Khan',
      displayName: 'Apsar Khan',
    };
    mock.onGet('/users/me').replyOnce(200, user).onGet('/users/me').replyOnce(401);
    mock.onPost('/auth/logout').reply(204);

    await expect(authApi.me()).resolves.toEqual(user);
    await expect(authApi.me()).resolves.toBeNull();
    await expect(authApi.logout()).resolves.toBeUndefined();
  });

  it('surfaces safe backend validation errors', async () => {
    mock.onPost('/auth/login').reply(400, {
      message: 'Request validation failed',
      violations: [{ field: 'email', message: 'must be a well-formed email address' }],
    });

    await expect(authApi.login('invalid', 'password')).rejects.toThrow(
      'must be a well-formed email address',
    );
  });
});
