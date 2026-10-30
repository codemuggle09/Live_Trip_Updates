# Protocol

## REST API

All application APIs use the `/api/v1` prefix.

### Publish Trip Event

`POST /api/v1/trips/{tripId}/events`

```json
{
  "eventId": "01JEXAMPLEULID000000000001",
  "eventType": "LOCATION_UPDATE",
  "occurredAt": "2026-10-29T10:30:00Z",
  "payload": {
    "latitude": 28.6139,
    "longitude": 77.209,
    "accuracyMeters": 8.0
  }
}
```

Expected behavior:

- Validate authentication and publish authorization.
- Resolve tenant and cab from server-side trip metadata.
- Reject invalid payloads with `400 Bad Request`.
- Reject unauthorized requests with `401 Unauthorized` or `403 Forbidden`.
- Reject duplicate event IDs with different content using `409 Conflict`.
- Return the accepted event envelope after sequencing.

### Read Event History

`GET /api/v1/trips/{tripId}/events?afterSequence=100&limit=100`

Events are returned in ascending per-trip sequence order. The server enforces a
maximum page size and repeats authorization checks. Current response shape:

```json
{
  "events": [],
  "nextAfterSequence": 100,
  "currentSequence": 105
}
```

### Trip Snapshot

`GET /api/v1/trips/{tripId}/snapshot`

Planned endpoint. It will be used when a client's requested replay point is
outside the retained Redis Stream window.

### SOS

`POST /api/v1/trips/{tripId}/sos`

SOS follows the same authorization, idempotency, sequencing, persistence, and
fan-out path as other events, but it is critical priority and cannot be
conflated.

## Event Envelope

```json
{
  "schemaVersion": 1,
  "eventId": "01JEXAMPLEULID000000000001",
  "tenantId": "tenant-1",
  "tripId": "trip-123",
  "cabId": "cab-45",
  "sequenceNumber": 101,
  "eventType": "LOCATION_UPDATE",
  "occurredAt": "2026-10-29T10:30:00Z",
  "acceptedAt": "2026-10-29T10:30:01Z",
  "priority": "NORMAL",
  "payload": {
    "latitude": 28.6139,
    "longitude": 77.209,
    "accuracyMeters": 8.0
  }
}
```

Initial event types:

- `LOCATION_UPDATE`
- `TRIP_STATUS_CHANGED`
- `SOS_ALERT`
- `TRIP_COMPLETED`
- `TRIP_CANCELLED`
- `RESYNC_REQUIRED`

## WebSocket

Endpoint: `/ws`

Local development may use `ws://`. Deployed environments should use `wss://`.

### Subscribe

```json
{
  "action": "SUBSCRIBE",
  "requestId": "req-1",
  "topic": "trip:trip-123"
}
```

### Unsubscribe

```json
{
  "action": "UNSUBSCRIBE",
  "requestId": "req-2",
  "topic": "trip:trip-123"
}
```

### Resume

```json
{
  "action": "RESUME",
  "requestId": "req-3",
  "topic": "trip:trip-123",
  "lastReceivedSequence": 105
}
```

Current implementation supports `RESUME` for `trip:{tripId}` topics. The server
returns a `SUBSCRIBED` message followed by retained event envelopes after the
client's `lastReceivedSequence`.

### Acknowledgement

```json
{
  "action": "ACK",
  "topic": "trip:trip-123",
  "lastProcessedSequence": 109
}
```

ACK means the client claims the application processed through a sequence. It is
not proof that a person saw the event. ACK is documented but not implemented
yet; the server currently returns a `NOT_IMPLEMENTED` error.

### Server Messages

```json
{
  "type": "SUBSCRIBED",
  "requestId": "req-1",
  "topic": "trip:trip-123",
  "currentSequence": 105
}
```

```json
{
  "type": "ERROR",
  "requestId": "req-1",
  "code": "FORBIDDEN",
  "message": "Subscription is not permitted."
}
```

```json
{
  "type": "RESYNC_REQUIRED",
  "topic": "trip:trip-123",
  "reason": "REPLAY_WINDOW_EXPIRED",
  "snapshotEndpoint": "/api/v1/trips/trip-123/snapshot"
}
```

## Topic Model

- `trip:{tripId}` for one trip.
- `cab:{cabId}` for cab-scoped events.
- `office:{officeId}` for authorized transport administrators.

Topic syntax and authorization are checked independently. A valid topic string
does not grant access.
