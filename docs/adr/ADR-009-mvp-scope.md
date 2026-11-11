# ADR-009: MVP Scope and Deferred Features

**Status:** Proposed

## Context

The original product vision exceeds what can be safely validated in the first release.

## Decision

Release 1 contains registration/login, email verification, opaque sessions, logout/password reset, organization creation, invitations/memberships, organization RBAC, projects/project membership, tasks/assignments, minimal audit history, a basic React dashboard, and tenant-isolation/security tests.

Teams, task comments, labels, dependencies, attachments, chat, discussions, mentions, advanced notifications, analytics, MFA, SSO, Redis, Kafka and WebSockets are deferred. Reliable transactional email needed for verification, reset and invitations is infrastructure supporting the MVP, not a general notification feature.

## Alternatives considered

- Deliver the full product vision in Release 1: rejected due to security, schedule and validation risk.
- Identity-only release: rejected because it would not validate the core tenant/project/task value proposition.

## Consequences

Documentation and APIs must not promise deferred features as Release 1 behavior. The product should ship as end-to-end vertical slices, with each slice meeting security and operability gates.

## Operational implications

Production deployment, backup/restore, monitoring and security hardening are release criteria even though they are not product features.

