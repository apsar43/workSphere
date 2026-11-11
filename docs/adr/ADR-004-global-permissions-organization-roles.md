# ADR-004: Global Permissions and Organization-Scoped Roles

**Status:** Proposed

## Context

Application capabilities are defined by shipped code, while organizations need their own role groupings and assignments.

## Decision

Permissions are global, predefined, immutable capability definitions with no `organization_id`. Roles belong to one organization. `role_permissions` maps an organization role to global permissions. `membership_roles` assigns roles only to memberships in the same organization. Resource-level policy remains necessary in addition to RBAC.

## Alternatives considered

- Organization-owned permissions: rejected because tenants must not invent capabilities the application cannot enforce.
- Hardcoded job-title checks: rejected because they are inflexible and difficult to audit.

## Consequences

Permission codes require controlled application/database seeding. Organizations can define roles but cannot define executable capabilities. Role deletion must be restricted or handled through explicit lifecycle rules.

## Security implications

The backend checks permission plus tenant/resource relationship. UI visibility is not authorization.

