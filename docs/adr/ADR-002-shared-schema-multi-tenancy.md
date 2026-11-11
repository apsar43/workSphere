# ADR-002: Shared-Schema Multi-Tenancy

**Status:** Proposed

## Context

WorkSphere serves independent organizations while keeping initial infrastructure and migrations manageable.

## Decision

Use one PostgreSQL database and shared schema. Every tenant-owned row carries `organization_id`. Authorization derives an authorized organization from an active membership; client-supplied identifiers never establish access. Queries and relationships are tenant-aware. PostgreSQL RLS is deferred until transaction-scoped tenant context is designed and tested.

## Alternatives considered

- Database per tenant: stronger physical isolation but high provisioning and migration cost.
- Schema per tenant: complicates migrations and connection management.
- RLS immediately: useful defense in depth, but unsafe if pooled-connection context is mishandled.

## Consequences

Operations remain simpler, but a missing tenant predicate is a critical vulnerability. Cross-tenant integration tests and composite constraints are mandatory.

## Security implications

Tenant isolation is enforced by authentication, active membership, authorization policy, tenant-filtered queries and database constraints. None replaces another.

