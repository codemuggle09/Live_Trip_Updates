package com.example.livetripupdates.event;

public interface TripEventPublisher {

    void publish(TripEventEnvelope envelope);
}
