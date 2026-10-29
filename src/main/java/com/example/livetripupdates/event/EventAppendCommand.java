package com.example.livetripupdates.event;

import com.fasterxml.jackson.databind.JsonNode;

import java.time.Instant;

public record EventAppendCommand(
        String requestHash,
        String eventId,
        String tenantId,
        String tripId,
        String cabId,
        TripEventType eventType,
        Instant occurredAt,
        Instant acceptedAt,
        EventPriority priority,
        JsonNode payload
) {
}
