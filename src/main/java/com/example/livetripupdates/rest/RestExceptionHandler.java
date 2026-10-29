package com.example.livetripupdates.rest;

import com.example.livetripupdates.event.TripEventConflictException;
import com.example.livetripupdates.event.TripEventForbiddenException;
import com.example.livetripupdates.event.TripEventNotFoundException;
import com.example.livetripupdates.event.TripEventValidationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class RestExceptionHandler {

    @ExceptionHandler(TripEventValidationException.class)
    ResponseEntity<ApiError> badEvent(TripEventValidationException exception) {
        return error(HttpStatus.BAD_REQUEST, "INVALID_EVENT", exception.getMessage());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<ApiError> validationError(MethodArgumentNotValidException exception) {
        return error(HttpStatus.BAD_REQUEST, "INVALID_REQUEST", "Request validation failed.");
    }

    @ExceptionHandler(TripEventForbiddenException.class)
    ResponseEntity<ApiError> forbidden(TripEventForbiddenException exception) {
        return error(HttpStatus.FORBIDDEN, "FORBIDDEN", exception.getMessage());
    }

    @ExceptionHandler(TripEventNotFoundException.class)
    ResponseEntity<ApiError> notFound(TripEventNotFoundException exception) {
        return error(HttpStatus.NOT_FOUND, "NOT_FOUND", exception.getMessage());
    }

    @ExceptionHandler(TripEventConflictException.class)
    ResponseEntity<ApiError> conflict(TripEventConflictException exception) {
        return error(HttpStatus.CONFLICT, "EVENT_ID_CONFLICT", exception.getMessage());
    }

    @ExceptionHandler(IllegalArgumentException.class)
    ResponseEntity<ApiError> badRequest(IllegalArgumentException exception) {
        return error(HttpStatus.BAD_REQUEST, "BAD_REQUEST", exception.getMessage());
    }

    private ResponseEntity<ApiError> error(HttpStatus status, String code, String message) {
        return ResponseEntity.status(status).body(ApiError.of(code, message));
    }
}
