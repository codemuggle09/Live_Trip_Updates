package com.example.livetripupdates.event;

public class TripEventForbiddenException extends RuntimeException {

    public TripEventForbiddenException(String message) {
        super(message);
    }
}
