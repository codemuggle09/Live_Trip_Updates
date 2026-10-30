# Failure Modes

## Failure Mode Summary Table

| Failure Scenario | What Fails | What the User / Client Sees | How the System Recovers |
| :--- | :--- | :--- | :--- |
| **Redis Pub/Sub Loss** | Transient network drop or gateway subscriber packet drop. | Live event delivery on WebSocket is temporarily skipped. | Client detects sequence gap in stream order; calls `RESUME trip:{tripId}, lastReceivedSequence` to replay missed events from Redis Streams. |
| **Redis Stream Write Failure** | Redis node unavailable, out of memory, or disk write error. | Ingestion API returns HTTP 500 / request failure; event rejected before acknowledgment. | Publisher retries POST with the exact same `eventId`. Ingestion is strictly idempotent. |
| **Duplicate Event Publish** | Cab driver or network client retries an already-sent payload. | HTTP 200/201 returned with existing sequence; no duplicate fan-out occurs. | Redis idempotency cache verifies `eventId` hash. If content matches, duplicate is suppressed without republishing. If content differs, returns HTTP 409 Conflict. |
| **Slow WebSocket Consumer** | Rider/Admin screen has poor network or frozen client UI. | Outbound queue fills up; client connection closed with `SERVICE_OVERLOAD (1013)`. | Other subscribers unaffected (bounded queue isolation). Reconnecting client sends `RESUME` to retrieve missed events up to current sequence. |
| **SOS Under Heavy Traffic** | System overwhelmed by high-volume cab location pings. | Location pings experience back-pressure; SOS alerts maintain < 2s delivery SLA. | SOS uses dedicated `EventPriority.CRITICAL` queue drained before normal events in `ClientConnection`. Events are persisted in Redis Streams before fan-out. |
| **Gateway Node Crash** | Gateway process dies or instance container fails. | Active WebSockets terminate (`Connection lost / reconnecting`). | Clients reconnect to surviving gateway instance via load balancer and issue `RESUME` with their `lastReceivedSequence`. |
| **Replay Window Expiry** | Client disconnected longer than stream retention TTL/trimming. | Replay returns `RESYNC_REQUIRED` message with error code `REPLAY_WINDOW_EXPIRED`. | Client fetches fresh baseline state via `GET /api/v1/trips/{tripId}/snapshot` and resumes live subscription. |

## Redis Pub/Sub Notification Loss

Redis Pub/Sub is a live notification path only. If a gateway misses a Pub/Sub
message, clients recover through Redis Stream replay by sending their last
received sequence.

## Redis Stream Write Failure

An event is not accepted unless it is appended to Redis Streams and assigned a
per-trip sequence. Failed appends surface as request failures and should be
retried by the publisher with the same `eventId`.

## Duplicate Publish

The same `eventId` with identical content returns the original logical event.
The same `eventId` with different content is rejected as a conflict.

## Slow WebSocket Client

Each WebSocket connection has bounded outbound queues. If a queue is full, the
connection is closed with a service-overload status. The client should reconnect
and replay from its last received sequence.

## SOS Delivery Under Load

SOS events are persisted before acceptance and use critical outbound priority.
If a client cannot receive them live, the events remain replayable from Redis
Streams.

## Gateway Restart

Gateway-local sessions and subscriptions are ephemeral. Clients reconnect,
resubscribe, and replay retained events from the sequence they last processed.

## Unsupported Replay Window

Snapshot recovery is planned but not implemented yet. If retained stream history
is trimmed before a client reconnects, operators should treat that as a known
gap until the snapshot endpoint is added.
