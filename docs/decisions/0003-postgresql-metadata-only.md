# 0003 PostgreSQL Metadata Only

## Context

The workload includes high-frequency location updates. Writing every event to
PostgreSQL would increase write pressure and require a transactional outbox or
another dual-write design to coordinate SQL and Redis safely.

## Decision

Use PostgreSQL for business metadata and authorization data. Use Redis Streams
for replayable trip event history in the initial implementation.

## Consequences

The event log durability depends on Redis persistence and retention settings.
If SQL audit retention becomes mandatory, a transactional outbox or explicit SQL
event audit table should be added with measured performance impact.
