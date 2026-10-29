package com.example.livetripupdates.event;

public class TripEventConflictException extends RuntimeException {

    public TripEventConflictException(String message) {
        super(message);
    }
}
