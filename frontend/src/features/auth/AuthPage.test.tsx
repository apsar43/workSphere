import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { createMemoryRouter, RouterProvider } from 'react-router-dom';

import { AuthPage } from '@/features/auth/AuthPage';

const { registerMock } = vi.hoisted(() => ({ registerMock: vi.fn() }));

vi.mock('@/services/authApi', () => ({
  authApi: {
    googleUrl: '/api/v1/auth/oauth2/google',
    login: vi.fn(),
    register: registerMock,
  },
}));

describe('AuthPage', () => {
  it('renders the secure login experience', () => {
    const router = createMemoryRouter([{ path: '/login', element: <AuthPage mode="login" /> }], {
      initialEntries: ['/login'],
    });

    render(<RouterProvider router={router} />);

    expect(screen.getByRole('heading', { name: /welcome back/i })).toBeInTheDocument();
    expect(screen.getByRole('button', { name: /sign in/i })).toBeInTheDocument();
    expect(screen.getByRole('link', { name: /continue with google/i })).toBeInTheDocument();
  });

  it('confirms registration and safely resets the submitted form', async () => {
    registerMock.mockResolvedValueOnce({ message: 'Registration received.' });
    const user = userEvent.setup();
    const router = createMemoryRouter(
      [{ path: '/register', element: <AuthPage mode="register" /> }],
      { initialEntries: ['/register'] },
    );

    render(<RouterProvider router={router} />);

    await user.type(screen.getByRole('textbox', { name: /first name/i }), 'Apsar');
    await user.type(screen.getByRole('textbox', { name: /last name/i }), 'Khan');
    await user.type(screen.getByRole('textbox', { name: /work email/i }), 'apsar@example.com');
    await user.type(screen.getByLabelText(/^password$/i), 'correct horse battery');
    await user.click(screen.getByRole('button', { name: /create account/i }));

    expect(await screen.findByRole('status')).toHaveTextContent(
      'Registration received. You can now sign in.',
    );
    expect(screen.getByRole('textbox', { name: /work email/i })).toHaveValue('');
    expect(registerMock).toHaveBeenCalledWith(
      'Apsar',
      'Khan',
      'apsar@example.com',
      'correct horse battery',
    );
  });
});
