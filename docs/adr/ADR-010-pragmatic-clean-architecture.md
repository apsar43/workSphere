# ADR-010: Pragmatic Clean Architecture

**Status:** Proposed

## Context

WorkSphere needs testable business rules and replaceable infrastructure without multiplying representations and interfaces for simple CRUD.

## Decision

Organize by business feature/module. Keep HTTP and persistence concerns out of core business rules. Use application use cases for transactional workflows and ports where an external dependency or meaningful substitution boundary exists. Do not create empty layers, one-method wrappers or duplicate models without a concrete isolation/testing benefit. Domain-driven patterns are applied to complex invariants, not mandated for every record.

## Alternatives considered

- Strict Clean Architecture everywhere: rejected because ceremony can exceed value.
- Controller-service-repository without boundaries: rejected because business and tenant rules become coupled to frameworks.

## Consequences

Teams must exercise judgment and document exceptions. Module boundaries are strict; internal layering is proportional to complexity. Architecture tests should enforce high-value rules rather than folder shape alone.

## Security implications

Authorization and tenant context live in explicit application policies and repository contracts, never solely in controllers or the UI.

