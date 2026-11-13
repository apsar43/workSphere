import { type FormEvent, useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';

import { authApi } from '@/services/authApi';

interface AuthPageProps {
  mode: 'login' | 'register';
}

export function AuthPage({ mode }: AuthPageProps) {
  const navigate = useNavigate();
  const [isSubmitting, setIsSubmitting] = useState(false);
  const [showPassword, setShowPassword] = useState(false);
  const [error, setError] = useState<string>();
  const [message, setMessage] = useState<string>();

  function field(data: FormData, name: string): string {
    const value = data.get(name);
    return typeof value === 'string' ? value : '';
  }

  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const form = event.currentTarget;
    setError(undefined);
    setMessage(undefined);
    setIsSubmitting(true);

    const data = new FormData(form);
    try {
      if (mode === 'login') {
        await authApi.login(field(data, 'email'), field(data, 'password'));
        await navigate('/app', { replace: true });
      } else {
        const result = await authApi.register(
          field(data, 'firstName'),
          field(data, 'lastName'),
          field(data, 'email'),
          field(data, 'password'),
        );
        setMessage(`${result.message} You can now sign in.`);
        form.reset();
      }
    } catch (requestError) {
      setError(requestError instanceof Error ? requestError.message : 'Something went wrong.');
    } finally {
      setIsSubmitting(false);
    }
  }

  const isLogin = mode === 'login';

  return (
    <main className="auth-page">
      <section className="auth-card" aria-labelledby="auth-title">
        <div className="auth-form-panel">
          <Link className="auth-brand" to="/" aria-label="WorkSphere home">
            <span className="auth-brand-mark" aria-hidden="true">
              W
            </span>
            <span>WorkSphere</span>
          </Link>

          <div className="auth-form-wrap">
            <p className="auth-kicker">Secure team workspace</p>
            <h1 id="auth-title">{isLogin ? 'Welcome back' : 'Create your account'}</h1>
            <p className="auth-intro">
              {isLogin
                ? 'Sign in to continue to your organization.'
                : 'Start with your identity. You can create or join an organization next.'}
            </p>

            <form
              className="auth-form"
              onSubmit={(event) => {
                void submit(event);
              }}
            >
              {!isLogin ? (
                <div className="field-row">
                  <label>
                    First name
                    <input autoComplete="given-name" name="firstName" required maxLength={100} />
                  </label>
                  <label>
                    Last name
                    <input autoComplete="family-name" name="lastName" required maxLength={100} />
                  </label>
                </div>
              ) : null}

              <label>
                Work email
                <input
                  autoComplete="email"
                  inputMode="email"
                  name="email"
                  placeholder="you@company.com"
                  required
                  type="email"
                />
              </label>

              <div className="password-field">
                <div className="field-label-row">
                  <label htmlFor="password">Password</label>
                  {isLogin ? (
                    <span className="muted-link" aria-disabled="true">
                      Forgot password?
                    </span>
                  ) : null}
                </div>
                <span className="password-input-wrap">
                  <input
                    autoComplete={isLogin ? 'current-password' : 'new-password'}
                    id="password"
                    minLength={isLogin ? undefined : 12}
                    name="password"
                    placeholder={isLogin ? 'Enter your password' : 'At least 12 characters'}
                    required
                    type={showPassword ? 'text' : 'password'}
                  />
                  <button
                    aria-label={showPassword ? 'Hide password' : 'Show password'}
                    aria-pressed={showPassword}
                    className="password-visibility-button"
                    onClick={() => setShowPassword((visible) => !visible)}
                    type="button"
                  >
                    {showPassword ? (
                      <svg aria-hidden="true" viewBox="0 0 24 24">
                        <path d="M3 3l18 18M10.6 10.7a2 2 0 002.7 2.7M9.9 4.2A10.7 10.7 0 0112 4c5.5 0 9 5.5 9 5.5a15.8 15.8 0 01-3 3.6M6.2 6.2C4.2 7.6 3 9.5 3 9.5S6.5 15 12 15c1 0 2-.2 2.8-.5" />
                      </svg>
                    ) : (
                      <svg aria-hidden="true" viewBox="0 0 24 24">
                        <path d="M3 12s3.5-5.5 9-5.5 9 5.5 9 5.5-3.5 5.5-9 5.5S3 12 3 12z" />
                        <circle cx="12" cy="12" r="2.5" />
                      </svg>
                    )}
                  </button>
                </span>
              </div>

              {error ? (
                <p className="form-alert form-alert-error" role="alert">
                  {error}
                </p>
              ) : null}
              {message ? (
                <p className="form-alert form-alert-success" role="status">
                  {message}
                </p>
              ) : null}

              <button className="auth-submit" disabled={isSubmitting} type="submit">
                {isSubmitting ? 'Please wait…' : isLogin ? 'Sign in' : 'Create account'}
              </button>
            </form>

            <div className="auth-divider">
              <span>or</span>
            </div>

            <a className="google-button" href={authApi.googleUrl}>
              <span className="google-mark" aria-hidden="true">
                G
              </span>
              Continue with Google
            </a>

            <p className="auth-switch">
              {isLogin ? "Don't have an account?" : 'Already have an account?'}{' '}
              <Link to={isLogin ? '/register' : '/login'}>
                {isLogin ? 'Create one' : 'Sign in'}
              </Link>
            </p>
          </div>
        </div>

        <aside className="auth-art-panel" aria-label="WorkSphere security illustration">
          <div className="art-copy">
            <span className="art-pill">Identity protected</span>
            <h2>Your work belongs in one trusted sphere.</h2>
            <p>
              Secure sessions, clear permissions, and connected teams—without browser-stored tokens.
            </p>
          </div>
          <div className="sphere-art" aria-hidden="true">
            <div className="orbit orbit-one" />
            <div className="orbit orbit-two" />
            <div className="sphere-core">WS</div>
            <div className="art-node node-one">✓</div>
            <div className="art-node node-two">•••</div>
            <div className="art-node node-three">↗</div>
          </div>
        </aside>
      </section>
    </main>
  );
}
