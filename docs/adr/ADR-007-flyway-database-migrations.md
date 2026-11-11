# ADR-007: Flyway Database Migrations

**Status:** Proposed

## Context

Production schema changes must be repeatable, reviewable and identical across instances.

## Decision

Flyway is the only production schema migration mechanism. Hibernate uses schema validation in production and must never use `create`, `create-drop` or `update`. Applied shared migrations are immutable; corrections use a new migration. Migrations use explicit constraint/index names and are tested against PostgreSQL.

## Alternatives considered

- Hibernate automatic DDL: rejected because it is not an auditable production migration process.
- Manual SQL execution: rejected because it creates environment drift.
- Liquibase: viable, but Flyway is selected for this project.

## Consequences

Every schema change needs forward-compatibility and deployment review. Rollback is normally a corrective forward migration plus application rollback planning.

## Operational implications

Backups, restore verification and migration rehearsals are required for high-risk production changes.

