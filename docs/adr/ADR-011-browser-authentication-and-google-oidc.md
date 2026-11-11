# ADR-011: Browser Authentication and Google OpenID Connect

**Status:** Accepted for incremental implementation

## Context

WorkSphere needs email/password registration and login, Google sign-in, logout, and a current-user API for its first-party React browser client. Authentication must support immediate revocation, multiple devices, password reset, account linking, auditability, tenant-aware authorization, CSRF protection, and safe operation when the frontend and backend scale independently.

The deployed frontend currently uses Cloudflare Workers Static Assets and the backend uses Render. Direct browser calls between their unrelated public origins would require cross-site cookies (`SameSite=None`) and would depend on browser third-party-cookie policy. A browser JWT stored in `localStorage` or JavaScript-readable cookies would increase the impact of XSS and make immediate revocation and device-session management harder.

Google sign-in uses OAuth 2.0 and OpenID Connect (OIDC). Google's ID token is a signed JWT, but that does not require WorkSphere to use JWTs as its application session format.

This ADR extends [ADR-005](ADR-005-server-managed-browser-sessions.md); it does not supersede its opaque-session decision.

## Decision

### 1. Authentication boundary

The Spring Boot identity module is the only authority that establishes a WorkSphere user session. The React application displays authentication state but never validates credentials, Google tokens, roles, permissions, or tenant access as a security decision.

For the first-party browser application:

- WorkSphere uses the opaque, server-managed session defined by ADR-005.
- The raw session identifier exists only in a `Secure`, `HttpOnly`, host-only cookie.
- Only a protected hash of the identifier is stored in PostgreSQL.
- The browser does not store access tokens, refresh tokens, ID tokens, or session identifiers in `localStorage`, `sessionStorage`, IndexedDB, application state, URLs, or JavaScript-readable cookies.
- Authentication identifies a global user. Organization membership and authorization are resolved separately for every tenant operation.

### 2. JWT policy

JWTs are not the WorkSphere browser session credential.

JWT use is limited initially to externally issued OIDC protocol artifacts, such as the Google ID token received and validated by the backend during the authorization callback. Google access tokens and ID tokens are not returned to or persisted by the SPA. If a future mobile client or third-party API needs WorkSphere access tokens, it requires a separate ADR defining issuer, audience, signing-key rotation, lifetimes, scopes, revocation, refresh-token rotation, and resource-server boundaries.

### 3. Same-origin browser topology

The production browser communicates through one public application origin. Cloudflare serves the SPA and proxies `/api/*` to the Render backend. The frontend uses relative API URLs.

```text
Browser
   |
   | https://<application-origin>
   v
Cloudflare Worker
   |-- static assets and SPA routes
   `-- /api/* reverse proxy
             |
             v
       Render Spring Boot API
             |
             v
       Neon PostgreSQL
```

This topology keeps the application session first-party, permits `SameSite=Lax` by default, avoids broad credentialed CORS, and reduces dependence on third-party-cookie behavior. Direct public Render health checks may remain available, but browser business traffic uses the application origin.

The proxy must preserve method, body, query string, approved headers, every `Set-Cookie` header, and status while applying explicit upstream allowlisting, timeouts, request-size limits, and safe failure handling. It must not cache `/api/*`, authentication responses, personalized HTML, or responses carrying `Set-Cookie`; private API responses send `Cache-Control: no-store`. Static fingerprinted assets may use long-lived immutable caching.

The Worker removes client-supplied `Forwarded` and `X-Forwarded-*` values before setting the approved forwarding metadata. The backend trusts forwarded headers only from the reviewed proxy topology, has one documented proxy hop, and never derives scheme, host, client IP, redirect URI, or security policy from arbitrary inbound forwarding headers. The upstream hostname is fixed configuration, not request-controlled input. Proxy timeouts, maximum body size, disconnects, partial responses, multiple cookies, and unavailable-upstream behavior require automated tests.

The registered Google callback uses the public application origin, not the Render hostname, so the authorization response traverses the same reviewed Worker proxy path. Exact redirect URI matching is mandatory.

Local development may use the Vite development proxy or an explicitly allowed localhost origin. Production must not use `Access-Control-Allow-Origin: *` with credentials.

### 4. Email/password registration and login

Registration accepts normalized email, password, and required profile fields. Passwords are hashed using Spring Security's current approved adaptive password encoder policy, with Argon2id preferred when operationally supported. Parameters are versioned so hashes can be upgraded after successful authentication. Plaintext passwords are never stored, logged, included in events, or returned.

Registration creates a pending global identity and a single-use email-verification token. Account-existence-sensitive operations return generic responses. Login uses a generic failure for unknown email, wrong password, disabled account, or unverified-account policy where practical.

Password credentials use Argon2id with a minimum baseline of 19 MiB memory, 2 iterations, parallelism 1, a random salt of at least 16 bytes, and an output of at least 32 bytes. Before release, production hardware is benchmarked and parameters may be increased to target approximately 250–500 ms per verification without dropping below that baseline. The encoded hash carries algorithm parameters and supports upgrade-on-success. Any application-level pepper is permitted only if stored in the deployment secret manager, versioned, and backed by an approved rotation/recovery procedure; it is not required for the initial release.

Login rotates any pre-authentication session before establishing the authenticated session. The implemented baseline uses PostgreSQL-backed counters shared by every backend instance: 5 failures per normalized-account key or 100 failures per source-address key within 10 minutes blocks further attempts for 15 minutes. Keys are scoped SHA-256 digests; normalized email and source address are not stored in the rate-limit table. A successful login clears only its account counter, not the shared source counter. Blocked requests return generic HTTP `429` with `Retry-After`; unknown, disabled, and real accounts follow the same counter path. These values are typed configuration and may be tightened after production telemetry, but production must not disable both dimensions. Broader edge abuse controls and security events remain defense-in-depth work.

### 5. Google sign-in

Google sign-in uses the backend-managed OAuth 2.0 Authorization Code flow with OIDC, PKCE, `state`, and `nonce`:

1. The browser navigates to the WorkSphere Google authorization-start endpoint.
2. The backend creates short-lived authorization transaction state, PKCE S256 material, and nonce using a CSPRNG.
3. The backend redirects to Google's authorization endpoint using an exact registered redirect URI.
4. Google redirects to the backend callback with an authorization code.
5. The backend validates `state`, exchanges the code server-to-server, and validates issuer, audience, signature, expiration, nonce, and required claims.
6. The backend resolves an external identity using the immutable tuple `(provider, provider_subject)`, never mutable email alone.
7. The backend creates and rotates the WorkSphere opaque session, then redirects to a fixed allowlisted frontend route without tokens in the URL.

The SPA never receives the Google client secret and does not exchange authorization codes or validate Google ID tokens.

The requested scopes are exactly `openid`, `email`, and `profile` for the initial release. Additional Google scopes require a new privacy/security review.

Each authorization attempt creates a PostgreSQL-backed `oauth_authorization_transactions` record containing hashes of independent high-entropy state, nonce, and browser-binding handle; the PKCE verifier is encrypted with a versioned deployment key because it must be recovered for the server-to-server code exchange. The raw browser-binding handle is placed in a short-lived `Secure`, `HttpOnly`, host-only, `SameSite=Lax` cookie. No raw state, nonce, handle, verifier, authorization code, or provider token is logged.

The transaction expires after 10 minutes. Callback processing atomically changes the record from `PENDING` to `PROCESSING` only when the state hash, browser-binding hash, provider, expiry, and unused status match. A record cannot be processed twice. Success, user cancellation, provider error, validation failure, and code-exchange failure terminate the transaction and clear the binding cookie; the user receives a generic safe error page or fixed allowlisted redirect. A scheduled cleanup removes terminal and expired transaction secrets promptly while retaining only the minimum non-secret security event. Missing or unverified Google email claims do not create or link an account.

### 6. Signup and account-linking policy

External identity is modeled separately from the global user:

- `external_identities`: user, provider, immutable provider subject, provider email snapshot, timestamps, and provider metadata needed for security operations.
- Unique constraint on `(provider, provider_subject)`.
- Password credentials are optional and stored separately from the user profile so an OIDC-only user does not need a fake password hash.

Google may create a new WorkSphere user only when Google reports a verified email and self-registration is enabled. Google verification satisfies WorkSphere email verification for that provider-backed signup, but does not prove organization membership.

If a local WorkSphere account already exists for the same normalized email but has no linked Google identity, WorkSphere does not silently link solely by email. The user must authenticate the existing account (or complete another approved step-up/recovery flow) before linking Google. This prevents accidental or provider-account-based account takeover. Linking and unlinking are security-sensitive, require recent authentication, rotate or revoke sessions according to policy, and generate audit events.

At least one usable authentication method must remain after unlinking. Provider subject is the durable external key; provider email changes do not reassign an identity.

### 7. Post-signup behavior

Successful local registration does not create an authenticated organization session until email verification completes. A verified global user may have zero memberships and can access only identity bootstrap, session management, invitation discovery/acceptance, and organization-onboarding operations.

After verification or first approved Google signup, the UI presents two explicit paths:

1. Create a new organization, making the user its initial active owner through one transaction; or
2. Accept a valid invitation whose normalized target email matches the verified authenticated identity.

No existing organization membership, role, or permission is granted merely because an email domain, Google account, or normalized email matches. Invitation acceptance performs all membership and tenant-policy checks on the backend. Organization creation and invitation acceptance generate audit events.

### 8. Session, verification, recovery, and current-user API

The initial browser contract is:

| Method | Endpoint | Purpose |
|---|---|---|
| `POST` | `/api/v1/auth/register` | Create a pending local account |
| `POST` | `/api/v1/auth/login` | Authenticate local credentials and rotate the session |
| `POST` | `/api/v1/auth/logout` | Revoke the current server session and expire its cookie |
| `GET` | `/api/v1/auth/csrf` | Establish/return the CSRF token contract without authentication data |
| `POST` | `/api/v1/auth/verify-email` | Consume a single-use email-verification token |
| `POST` | `/api/v1/auth/resend-verification` | Generically request a replacement verification message |
| `POST` | `/api/v1/auth/password/forgot` | Generically request a password-reset message |
| `POST` | `/api/v1/auth/password/reset` | Consume a reset token, replace credentials, and revoke sessions |
| `GET` | `/api/v1/auth/oauth2/google` | Begin backend-managed Google authorization |
| `GET` | `/api/v1/auth/oauth2/google/callback` | Exact Google callback handled by Spring Security |
| `GET` | `/api/v1/users/me` | Return the authenticated user's safe profile and organization-selection context |

`GET /api/v1/users/me` is the bootstrap authority for browser authentication state. It returns no password data, raw session identifier, Google tokens, internal hashes, or permissions the backend has not safely derived. Unauthenticated requests return `401`; an authenticated user lacking permission returns `403`.

The frontend initializes in an unknown/loading state, calls `/users/me` with credentials, and then resolves to authenticated or anonymous state. It must not infer authentication from the presence of a cookie because the session cookie is intentionally `HttpOnly`.

Email-verification and password-reset tokens contain at least 256 bits of CSPRNG entropy. Only a SHA-256 or keyed digest of these already-high-entropy values is stored. Verification tokens expire after 24 hours; password-reset tokens expire after 30 minutes. Issuing a replacement invalidates prior unconsumed tokens of the same purpose. Consumption is one atomic conditional update requiring the hash match, correct purpose/user, `consumed_at IS NULL`, `invalidated_at IS NULL`, and `expires_at > now()`.

Email links place the raw token in the URL fragment, not the query string. The frontend reads it once, removes it from browser history with `history.replaceState`, and submits it in a JSON POST body over HTTPS. Verification, resend, forgot-password, and reset responses do not reveal whether an account exists. Resend and forgot-password use per-IP and per-account-equivalent throttling even when the account does not exist.

A successful password reset updates the password hash, marks the token consumed, revokes every active session for the user, clears the current browser session/CSRF cookies, records a security event, and sends a notification. It does not automatically log the user in. Email verification activates the eligible pending user but grants no organization membership.

### 9. CSRF protection

Spring Security CSRF protection remains enabled for browser endpoints. WorkSphere standardizes on Spring Security's cookie/header double-submit integration using a `CookieCsrfTokenRepository`, not an implementation-time choice between mechanisms.

The backend generates a CSPRNG CSRF token and writes it to a separate JavaScript-readable, host-only cookie named `__Host-WS-XSRF-TOKEN` in production with `Secure`, `Path=/`, `SameSite=Lax`, and no `Domain`. The authentication cookie remains independently `HttpOnly`. `GET /api/v1/auth/csrf` forces token materialization and returns `204`; the SPA reads the CSRF cookie and sends the exact value in `X-XSRF-TOKEN`. Spring Security compares the submitted header through the configured repository/handler before controller logic.

The CSRF cookie is cleared and a new token is required after successful local login, Google login, logout, password reset, account linking/unlinking, and privilege-sensitive session rotation. The pre-authentication token protects local registration, login, resend, forgot-password, and reset POSTs; the authenticated token protects all subsequent state-changing requests. The frontend handles a missing/expired token by fetching `/auth/csrf` once and retrying only idempotent or explicitly safe-to-retry client operations—never blindly replaying an arbitrary mutation.

The OIDC start/callback uses its one-time state, browser binding, PKCE, and nonce transaction controls rather than the application's header token. Other `GET` endpoints remain safe and side-effect free.

CSRF defense also includes `SameSite`, exact origin/host validation for sensitive requests, and the same-origin proxy. CORS is not treated as a substitute for CSRF protection.

### 10. XSS and browser security

XSS prevention is defense in depth:

- React's escaped rendering is the default; untrusted HTML rendering is prohibited unless centrally sanitized with an approved library and reviewed.
- No authentication material is readable by JavaScript.
- User-controlled data is contextually encoded at output and validated at input without treating validation as encoding.
- A restrictive Content Security Policy is introduced and tested before production login, avoiding `unsafe-inline` and `unsafe-eval` where feasible.
- Security headers include `Content-Security-Policy`, `X-Content-Type-Options: nosniff`, a restrictive `Referrer-Policy`, appropriate `Permissions-Policy`, and clickjacking protection through CSP `frame-ancestors`.
- External redirects use a fixed allowlist; request parameters never become arbitrary post-login redirect targets.
- Sensitive responses use appropriate `Cache-Control` and are not written to browser logs or analytics.

### 11. Cookie, session, and account-state policy

The session cookie is host-only and uses `Secure`, `HttpOnly`, `Path=/`, and `SameSite=Lax` in the same-origin topology. Its name uses a `__Host-` prefix in production when framework and proxy behavior permit. No `Domain` attribute is set.

ADR-011 adopts ADR-005's initial values for implementation review: 30-minute sliding idle expiry, 12-hour absolute expiry, and at most 10 active sessions per user with least-recently-used revocation when the limit is exceeded. The PostgreSQL session table is authoritative across instances. An authenticated request renews `idle_expires_at` to `min(now + 30 minutes, absolute_expires_at)` only when `last_seen_at` is at least 5 minutes old, reducing write amplification while preserving the idle guarantee. Expired, revoked, and inactive-user sessions are never renewed. The concurrent-session limit remains a subsequent session-management slice.

Local and Google login rotate any pre-authentication session. Password reset, account disablement, suspected credential compromise, and security-administrator global logout revoke all sessions. Password change revokes every other session and rotates the current session after recent-authentication verification. Provider link/unlink and material privilege changes rotate the current session and may revoke other sessions according to the recorded risk decision. Every authenticated request rejects `LOCKED`, `DISABLED`, `ERASURE_PENDING`, or `ANONYMIZED` accounts even if a session record remains unexpired.

Logout is idempotent from the user's perspective and always expires the browser session and CSRF cookies even when the server record is already invalid.

### 12. Security events and privacy

Record security events for successful and failed login, logout, session revocation, password reset, email verification, provider link/unlink, and suspicious OIDC failures. Logs and audit records never contain passwords, raw session or CSRF tokens, authorization codes, Google tokens, client secrets, or full credential-bearing URLs.

Provider secrets and mail credentials are injected through the deployment secret store and rotated. Google redirect URIs and JavaScript origins are exact allowlisted values for each environment.

## Alternatives considered

- **JWT access token in `localStorage` or `sessionStorage`: rejected.** Any successful XSS can read and export the bearer token; revocation, device management, and logout semantics become more complex.
- **Stateless JWT in an HttpOnly cookie: rejected for the initial browser application.** It still requires CSRF protection and does not satisfy immediate revocation without server state or short lifetimes plus refresh-token infrastructure.
- **Access-token JWT plus rotating refresh token:** deferred to a future non-browser client ADR. It adds signing-key, issuer/audience, replay, rotation-family, and revocation complexity without a current requirement.
- **Google token accepted directly from the SPA on every API call: rejected.** Google is an identity provider, not WorkSphere's authorization server, and Google tokens do not encode WorkSphere tenant authorization or session policy.
- **Silent linking by matching Google email to a local account: rejected.** Email coincidence alone is insufficient for a security-sensitive link.
- **Direct cross-site browser calls from Cloudflare to Render:** rejected as the production default because they require cross-site cookie and CORS complexity and are vulnerable to changing third-party-cookie behavior.
- **Authentication entirely outsourced to a hosted identity vendor:** deferred. It may be reconsidered when enterprise federation, MFA, compliance, staffing, and total operating cost are reviewed.

## Consequences

- Every authenticated request performs a revocable server-session lookup unless a later safe cache preserves revocation guarantees.
- The Cloudflare Worker becomes a controlled browser edge/proxy component in addition to a static-asset host.
- Google sign-in requires provider configuration, callback handling, external-identity persistence, linking UX, and provider-specific security tests.
- The ERD must replace mandatory `users.password_hash` with separate optional password credentials and add external identities before migrations are created.
- The frontend must implement explicit unknown, anonymous, pending-verification, authenticated, and authentication-error states.
- Same-origin deployment simplifies cookies and CSRF but requires proxy observability, forwarded-header rules, and end-to-end testing.

## Required follow-up before implementation

1. Human review and approval of ADR-005 and this ADR.
2. Amend the ERD for password credentials, external identities, OIDC authorization transactions, and any session metadata required by the approved design.
3. Define request/response schemas, validation, RFC 9457 errors, and safe redirect behavior for every endpoint.
4. Benchmark Argon2id and the implemented rate-limit values on production-equivalent compute without weakening the minimum password-hash baseline.
5. Register separate Google OAuth clients/redirect URIs for local and production environments.
6. Design the Cloudflare `/api/*` proxy and trusted-forwarded-header boundary.
7. Define CSP and other browser security headers with automated tests.
8. Create threat-model tests for fixation, CSRF, XSS boundaries, account enumeration, OIDC state/nonce/PKCE, account linking, session revocation, cross-tenant access, and open redirects.
9. Define Google/provider outage UX, recovery for a Google-only account, account deletion, and administrative emergency-recovery controls.
10. Add deployment tests proving no private API caching, preservation of multiple `Set-Cookie` headers, correct public callback URL, forwarded-header sanitization, request limits, timeouts, and safe upstream-failure responses.

No authentication implementation or database migration begins until these decisions are reviewed.

## References

- [RFC 9700: OAuth 2.0 Security Best Current Practice](https://www.rfc-editor.org/rfc/rfc9700.html)
- [RFC 10017: OAuth 2.0 for Browser-Based Applications](https://www.rfc-editor.org/rfc/rfc10017.html)
- [Google OpenID Connect documentation](https://developers.google.com/identity/openid-connect/openid-connect)
- [Spring Security OAuth 2.0 documentation](https://docs.spring.io/spring-security/reference/servlet/oauth2/)
- [OWASP Session Management Cheat Sheet](https://cheatsheetseries.owasp.org/cheatsheets/Session_Management_Cheat_Sheet.html)
- [OWASP CSRF Prevention Cheat Sheet](https://cheatsheetseries.owasp.org/cheatsheets/Cross-Site_Request_Forgery_Prevention_Cheat_Sheet.html)

