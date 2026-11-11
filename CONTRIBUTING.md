# Contributing to WorkSphere

WorkSphere is developed incrementally. Architecture, security and tenant-isolation rules are part of the product contract, not optional implementation details.

## Branching

- Protect `main`; do not develop directly on it.
- Create short-lived branches from an up-to-date `main`.
- Use descriptive names such as `feature/identity-registration`, `fix/session-revocation`, or the required automation prefix such as `codex/identity-foundation`.
- Keep a branch focused on one reviewable outcome.
- Rebase or merge the latest `main` according to the team's chosen policy before final review. Never rewrite shared history without coordination.

## Change workflow

1. Read [the documentation index](docs/README.md) and applicable ADRs.
2. Confirm the change belongs to the current milestone and MVP.
3. Add or update tests with implementation changes.
4. Run formatting, static analysis, unit tests and relevant integration/security tests.
5. Review database migrations, authorization and tenant ownership explicitly.
6. Update documentation when a contract or decision changes.
7. Submit a small pull request explaining behavior, risks, verification and rollback considerations.

## Architecture changes

Do not change a recorded architecture decision silently. Add a new ADR that supersedes the old decision or update a `Proposed` ADR during review. Only humans responsible for the project may approve an ADR as `Accepted`.

## Database changes

- Flyway is the only schema migration mechanism.
- Never edit an applied shared migration; add a new migration.
- Hibernate schema generation may validate test/development mappings but must not update a production schema.
- Every tenant-owned query and relationship requires an explicit tenant-isolation review.

## Security and secrets

Never commit credentials, raw tokens, cookies, private keys or production data. Security-sensitive changes require negative authorization tests and cross-tenant tests where applicable.

