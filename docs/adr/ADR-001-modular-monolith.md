# ADR-001: Modular Monolith

**Status:** Proposed

## Context

WorkSphere needs strong business boundaries without the deployment and consistency cost of distributed services during initial delivery.

## Decision

Deploy one Spring Boot backend organized into enforceable identity, organization, authorization, project, task and audit modules. Modules own their internals and communicate through explicit application interfaces or events. A module must not call another module's internal repository.

## Alternatives considered

- Microservices: rejected initially because they add network failure modes, distributed transactions and operational overhead.
- Unstructured layered monolith: rejected because it permits boundary erosion.

## Consequences

Local development, transactions and deployment stay simple. Architecture tests and code review must prevent coupling. A module may be extracted later only when measured scale or organizational ownership justifies it.

## Operational implications

The backend scales as a unit. Database and application observability must still identify module-specific workloads and failures.

