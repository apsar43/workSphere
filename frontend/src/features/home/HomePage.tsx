import { useState } from 'react';

import { Button } from '@/components/Button/Button';

export function HomePage() {
  const [isReady, setIsReady] = useState(false);

  return (
    <section className="hero" aria-labelledby="home-title">
      <p className="eyebrow">Development foundation</p>
      <h1 id="home-title">One workspace for connected teams.</h1>
      <p className="hero-copy">
        The WorkSphere application shell is running. Business capabilities will be delivered in
        secure, tested vertical slices.
      </p>
      <Button onClick={() => setIsReady(true)}>Check foundation</Button>
      {isReady ? <p role="status">Frontend foundation is ready.</p> : null}
    </section>
  );
}
