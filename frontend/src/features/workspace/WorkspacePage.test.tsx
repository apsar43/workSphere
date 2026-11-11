import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { createMemoryRouter, RouterProvider } from 'react-router-dom';

import { WorkspacePage } from '@/features/workspace/WorkspacePage';

const { meMock, logoutMock } = vi.hoisted(() => ({
  meMock: vi.fn(),
  logoutMock: vi.fn(),
}));

vi.mock('@/services/authApi', () => ({
  authApi: {
    me: meMock,
    logout: logoutMock,
  },
}));

describe('WorkspacePage', () => {
  it('renders the protected coming-soon workspace for the current user', async () => {
    meMock.mockResolvedValueOnce({
      id: '7787c953-a326-4d35-89b3-617691a487a6',
      email: 'apsar@example.com',
      firstName: 'Apsar',
      lastName: 'Khan',
      displayName: 'Apsar Khan',
    });
    const router = createMemoryRouter(
      [
        { path: '/app', element: <WorkspacePage /> },
        { path: '/login', element: <p>Sign in</p> },
      ],
      { initialEntries: ['/app'] },
    );

    render(<RouterProvider router={router} />);

    expect(await screen.findByRole('heading', { name: 'Welcome, Apsar!' })).toBeInTheDocument();
    expect(screen.getByRole('heading', { name: /coming soon/i })).toBeInTheDocument();
    expect(screen.getByText('apsar@example.com')).toBeInTheDocument();
  });

  it('redirects anonymous users to sign in', async () => {
    meMock.mockResolvedValueOnce(null);
    const router = createMemoryRouter(
      [
        { path: '/app', element: <WorkspacePage /> },
        { path: '/login', element: <p>Sign in required</p> },
      ],
      { initialEntries: ['/app'] },
    );
    render(<RouterProvider router={router} />);
    expect(await screen.findByText('Sign in required')).toBeInTheDocument();
  });

  it('logs out and returns to sign in', async () => {
    const user = userEvent.setup();
    meMock.mockResolvedValueOnce({
      id: 'user-id',
      email: 'apsar@example.com',
      firstName: 'Apsar',
      lastName: 'Khan',
      displayName: 'Apsar Khan',
    });
    logoutMock.mockResolvedValueOnce(undefined);
    const router = createMemoryRouter(
      [
        { path: '/app', element: <WorkspacePage /> },
        { path: '/login', element: <p>Signed out</p> },
      ],
      { initialEntries: ['/app'] },
    );
    render(<RouterProvider router={router} />);
    await user.click(await screen.findByRole('button', { name: /sign out/i }));
    expect(await screen.findByText('Signed out')).toBeInTheDocument();
    expect(logoutMock).toHaveBeenCalledOnce();
  });

  it('shows a recoverable message when session verification fails', async () => {
    meMock.mockRejectedValueOnce(new Error('network'));
    const router = createMemoryRouter(
      [
        { path: '/app', element: <WorkspacePage /> },
        { path: '/login', element: <p>Sign in</p> },
      ],
      { initialEntries: ['/app'] },
    );
    render(<RouterProvider router={router} />);
    expect(await screen.findByRole('alert')).toHaveTextContent('could not verify your session');
  });
});
