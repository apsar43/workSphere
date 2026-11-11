import { environment } from '@/config/environment';
import { httpClient } from '@/services/httpClient';

export interface CurrentUser {
  id: string;
  email: string;
  firstName: string;
  lastName: string;
  displayName: string;
}

export const authApi = {
  async login(email: string, password: string) {
    const response = await httpClient.post<CurrentUser>('/auth/login', { email, password });
    return response.data;
  },
  async register(firstName: string, lastName: string, email: string, password: string) {
    const response = await httpClient.post<{ message: string }>('/auth/register', {
      firstName,
      lastName,
      email,
      password,
    });
    return response.data;
  },
  async logout() {
    await httpClient.post('/auth/logout');
  },
  async me(): Promise<CurrentUser | null> {
    try {
      const response = await httpClient.get<CurrentUser>('/users/me');
      return response.data;
    } catch (error) {
      if (
        typeof error === 'object' &&
        error !== null &&
        'status' in error &&
        error.status === 401
      ) {
        return null;
      }
      throw error;
    }
  },
  googleUrl: `${environment.apiBaseUrl}/auth/oauth2/google`,
};
