import { Link } from 'react-router-dom';

export function NotFoundPage() {
  return (
    <section className="not-found">
      <p className="eyebrow">404</p>
      <h1>Page not found</h1>
      <p>The requested WorkSphere page does not exist.</p>
      <Link to="/">Return home</Link>
    </section>
  );
}
