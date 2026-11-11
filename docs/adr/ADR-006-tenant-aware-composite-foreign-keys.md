# ADR-006: Tenant-Aware Composite Foreign Keys

**Status:** Proposed

## Context

Application bugs must not create relationships between rows owned by different organizations.

## Decision

Tenant-owned parent tables expose a unique `(organization_id, id)` key. Child tables include `organization_id` and reference that composite key. Tenant-owned actor fields reference `(organization_id, membership_id)`, not a global user ID. Task parent references include organization, project and task identity so parent and child share both tenant and project.

## Alternatives considered

- UUID-only FKs: rejected because they prove existence but not tenant consistency.
- Application validation only: rejected because concurrency and defects can bypass it.

## Consequences

Mappings and migrations are more verbose. Duplicate `organization_id` is intentional integrity data. Composite indexes and consistent column ordering are required.

## Security implications

Constraints prevent invalid persisted relationships but do not authorize reads or writes; tenant predicates and policy checks remain mandatory.

