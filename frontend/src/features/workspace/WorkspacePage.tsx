import { useEffect, useState } from 'react';
import { Navigate, useNavigate } from 'react-router-dom';

import { authApi, type CurrentUser } from '@/services/authApi';

const navigation = ['Dashboard', 'Projects', 'Tasks', 'Team', 'Reports'];

export function WorkspacePage() {
  const navigate = useNavigate();
  const [user, setUser] = useState<CurrentUser | null>();
  const [error, setError] = useState<string>();
  const [isLoggingOut, setIsLoggingOut] = useState(false);

  useEffect(() => {
    let active = true;
    void authApi
      .me()
      .then((currentUser) => {
        if (active) setUser(currentUser);
      })
      .catch(() => {
        if (active) setError('We could not verify your session. Please sign in again.');
      });
    return () => {
      active = false;
    };
  }, []);

  async function logout() {
    setIsLoggingOut(true);
    try {
      await authApi.logout();
    } finally {
      await navigate('/login', { replace: true });
    }
  }

  if (user === null) return <Navigate to="/login" replace />;

  if (error) {
    return (
      <main className="session-screen">
        <p role="alert">{error}</p>
        <button className="workspace-logout" type="button" onClick={() => void navigate('/login')}>
          Return to sign in
        </button>
      </main>
    );
  }

  if (user === undefined) {
    return (
      <main className="session-screen">
        <div className="session-spinner" aria-hidden="true" />
        <p role="status">Opening your WorkSphere…</p>
      </main>
    );
  }

  const initials = `${user.firstName.charAt(0)}${user.lastName.charAt(0)}`.toUpperCase();

  return (
    <div className="workspace-shell">
      <aside className="workspace-sidebar">
        <div className="workspace-brand">
          <span className="workspace-brand-mark">W</span>
          <span>WorkSphere</span>
        </div>

        <nav aria-label="Primary navigation">
          {navigation.map((item, index) => (
            <button
              className={`workspace-nav-item${index === 0 ? ' is-active' : ''}`}
              disabled={index !== 0}
              key={item}
              type="button"
            >
              <span aria-hidden="true">{index === 0 ? '⌂' : '○'}</span>
              {item}
            </button>
          ))}
        </nav>

        <div className="workspace-sidebar-note">
          <strong>Authentication ready</strong>
          <span>Product modules will arrive in secure vertical slices.</span>
        </div>
      </aside>

      <div className="workspace-main">
        <header className="workspace-topbar">
          <div className="workspace-search" aria-hidden="true">
            <span>⌕</span>
            <span>Search WorkSphere</span>
          </div>
          <div className="workspace-user">
            <span className="workspace-avatar" aria-hidden="true">
              {initials}
            </span>
            <span className="workspace-user-copy">
              <strong>{user.displayName}</strong>
              <small>{user.email}</small>
            </span>
            <button
              className="workspace-logout"
              disabled={isLoggingOut}
              onClick={() => void logout()}
              type="button"
            >
              {isLoggingOut ? 'Signing out…' : 'Sign out'}
            </button>
          </div>
        </header>

        <main className="workspace-content">
          <div className="workspace-welcome">
            <p>Dashboard</p>
            <h1>Welcome, {user.firstName}!</h1>
            <span>Your secure WorkSphere account is ready.</span>
          </div>

          <section className="coming-soon-card" aria-labelledby="coming-soon-title">
            <div className="coming-soon-orbit" aria-hidden="true">
              <span>WS</span>
            </div>
            <p className="coming-soon-kicker">Workspace foundation</p>
            <h2 id="coming-soon-title">Coming soon</h2>
            <p>
              Projects, tasks, teams, reports, and collaboration tools will be delivered in the next
              secure vertical slices.
            </p>
          </section>
        </main>
      </div>
    </div>
  );
}
