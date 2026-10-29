package com.example.livetripupdates.event;

import com.fasterxml.jackson.databind.JsonNode;

import java.time.Instant;

public record TripEventEnvelope(
        int schemaVersion,
        String eventId,
        String tenantId,
        String tripId,
        String cabId,
        long sequenceNumber,
        TripEventType eventType,
        Instant occurredAt,
        Instant acceptedAt,
        EventPriority priority,
        JsonNode payload
) {
}
