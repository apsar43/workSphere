# ADR-003: Global Users and Organization Memberships

**Status:** Proposed

## Context

A person may participate in more than one organization with different status and authorization in each.

## Decision

`users` represents global identity. `memberships` connects a user to an organization and owns tenant-specific status and role assignments. At most one membership exists per user and organization. Removed membership history is retained rather than deleted.

## Alternatives considered

- Duplicate user per organization: rejected because identity recovery and multi-organization use become fragmented.
- Store one organization and role on `users`: rejected because it prevents multi-organization membership and mixes identity with authorization.

## Consequences

Authentication identifies a user; every tenant operation additionally resolves an active membership. Rejoining normally reactivates the retained membership subject to policy.

## Security and privacy implications

Membership status is checked on every tenant operation. User erasure must anonymize or delete global personal data where legally required while preserving the minimum tenant audit evidence under documented retention rules.

