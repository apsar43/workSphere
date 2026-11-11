# ADR-005: Server-Managed Browser Sessions

**Status:** Proposed

## Context

The initial client is a first-party browser application. WorkSphere needs revocable credentials without exposing bearer tokens to JavaScript.

## Decision

Use a cryptographically random opaque session token. Store only a keyed hash or cryptographic digest suitable for high-entropy tokens; return the raw token once in a cookie configured `Secure`, `HttpOnly`, `Path=/`, without a JavaScript-readable copy. Use `SameSite=Lax` by default; deployment requiring cross-site cookies needs a separate review and `SameSite=None; Secure`. Do not set a broad parent domain unless required.

State-changing requests use synchronizer-token or equivalent Spring Security CSRF protection. Login rotates any pre-authentication session. Privilege changes may rotate or revoke sessions. Logout revokes the server record and expires the cookie.

Initial policy assumptions, to be confirmed before implementation:

- 30-minute idle timeout and 12-hour absolute lifetime.
- Sliding activity may extend idle expiry but never absolute expiry.
- Up to 10 active sessions per user; creating another revokes the least-recently-used session.
- Password reset and security-sensitive account changes revoke all existing sessions.
- Session state is stored in PostgreSQL so all backend instances observe creation and revocation consistently.

Password-reset and email-verification tokens are at least 256 bits of CSPRNG entropy, stored only as protected hashes, expire, and are consumed with one atomic conditional update. Reset and registration responses do not disclose account existence. Login, reset and verification endpoints use layered per-IP and per-account rate limits with generic failures and security events. Repeated failures cause temporary throttling/lockout, not permanent denial controlled by an attacker.

## Alternatives considered

- JWT access tokens in browser localStorage: rejected due to JavaScript exposure and revocation complexity.
- Stateless JWT cookies: rejected initially because immediate revocation and session/device management are requirements.
- External identity provider: deferred until SSO/OIDC scope.

## Consequences

Every authenticated request performs a session lookup unless safe caching is later introduced. Cleanup jobs remove expired records after the security/audit retention window. Exact timeouts, limits, CSRF mechanism and cookie origin topology require human approval.

## Security and operational implications

TLS is mandatory outside local development. Tokens, cookies and password material are never logged. Multi-instance deployments share the PostgreSQL session source of truth; Redis is not required for correctness.

