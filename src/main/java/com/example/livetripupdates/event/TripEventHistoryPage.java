package com.example.livetripupdates.event;

import java.util.List;

public record TripEventHistoryPage(
        List<TripEventEnvelope> events,
        long nextAfterSequence,
        long currentSequence
) {
}
