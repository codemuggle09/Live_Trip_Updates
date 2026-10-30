# 0001 Redis Streams and Pub/Sub

## Context

The service needs low-latency live delivery and replay after reconnect. Redis
Pub/Sub delivers live notifications but does not retain messages. Redis Streams
retains ordered event history but is not as simple as Pub/Sub for broadcasting to
every gateway.

## Decision

Use Redis Streams as the replayable event store and Redis Pub/Sub as the live
cross-gateway notification bus.

## Consequences

Disconnected clients recover through stream replay. Gateways may receive Pub/Sub
messages for trips without local subscribers, which is acceptable for the first
release and should be measured during load testing.
