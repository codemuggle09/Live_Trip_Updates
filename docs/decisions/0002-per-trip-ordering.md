# 0002 Per-Trip Ordering

## Context

Clients need events for a single trip in order. A global order across all trips
would add coordination and contention without improving the main user workflow.

## Decision

Guarantee monotonically increasing sequence numbers per trip stream only.

## Consequences

Concurrent publishers for different trips do not block each other. Consumers must
not infer ordering between events from different trips.
