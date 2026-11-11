import { NavLink, Outlet } from 'react-router-dom';

export function AppLayout() {
  return (
    <div className="app-shell">
      <header className="app-header">
        <NavLink className="brand" to="/">
          WorkSphere
        </NavLink>
        <span className="foundation-badge">Foundation</span>
      </header>
      <main className="app-main">
        <Outlet />
      </main>
    </div>
  );
}
