package com.example.livetripupdates.event;

import java.util.List;

public interface TripEventStore {

    AcceptedTripEvent append(EventAppendCommand command);

    List<TripEventEnvelope> readAfter(String tripId, long afterSequence, int limit);

    long currentSequence(String tripId);
}
