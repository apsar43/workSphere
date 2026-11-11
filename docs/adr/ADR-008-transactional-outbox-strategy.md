# ADR-008: Transactional Outbox Introduction Strategy

**Status:** Proposed

## Context

Email and notification delivery cannot participate atomically in the database transaction that changes business state.

## Decision

Introduce `outbox_events` with the first reliable asynchronous email workflow, expected to be registration verification or organization invitation. Persist the business change and event in one PostgreSQL transaction. A worker claims pending events safely, performs delivery, and records attempts and outcome.

Delivery is at least once. Handlers and provider requests use a stable event/idempotency key. Retry transient failures with exponential backoff and jitter. Permanent or exhausted failures move to a terminal failed state, emit operational alerts and support controlled replay. Retain successful event metadata provisionally for 30 days and failed metadata for 90 days; approve final retention with privacy/operations policy.

Invitation, reset and verification events never store raw secrets in general event metadata or logs. Prefer an event reference that lets the delivery worker obtain the one-time delivery material through a narrowly scoped mechanism. If encrypted transient payload storage is required, use managed keys, strict access, short retention and redaction.

## Alternatives considered

- Send directly inside the transaction: rejected because external delivery cannot be rolled back atomically.
- Publish after commit without an outbox: rejected because a crash can lose the event.
- Kafka immediately: rejected because PostgreSQL polling is sufficient for the initial workload.

## Consequences

The system is eventually consistent for email delivery and may attempt delivery more than once. Worker lag, attempts, failures and oldest pending age require metrics and alerts. This ADR is an introduction strategy; no outbox is currently implemented.

