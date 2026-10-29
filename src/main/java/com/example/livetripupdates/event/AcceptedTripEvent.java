package com.example.livetripupdates.event;

public record AcceptedTripEvent(TripEventEnvelope envelope, boolean duplicate) {
}
