package com.example.livetripupdates.event;

import com.example.livetripupdates.domain.Trip;
import com.example.livetripupdates.observability.TripMetrics;
import com.example.livetripupdates.persistence.TripRepository;
import com.example.livetripupdates.security.CurrentPrincipal;
import com.example.livetripupdates.security.TripAuthorizationService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Instant;
import java.util.HexFormat;

@Service
public class TripEventIngestionService {

    private static final int SCHEMA_VERSION = 1;

    private final TripRepository tripRepository;
    private final TripAuthorizationService authorizationService;
    private final TripEventStore eventStore;
    private final TripEventPublisher eventPublisher;
    private final ObjectMapper objectMapper;
    private final Clock clock;
    private final TripMetrics metrics;

    public TripEventIngestionService(
            TripRepository tripRepository,
            TripAuthorizationService authorizationService,
            TripEventStore eventStore,
            TripEventPublisher eventPublisher,
            ObjectMapper objectMapper,
            Clock clock,
            TripMetrics metrics
    ) {
        this.tripRepository = tripRepository;
        this.authorizationService = authorizationService;
        this.eventStore = eventStore;
        this.eventPublisher = eventPublisher;
        this.objectMapper = objectMapper;
        this.clock = clock;
        this.metrics = metrics;
    }

    @Transactional(readOnly = true)
    public AcceptedTripEvent publishTripEvent(
            CurrentPrincipal principal,
            String tripId,
            PublishTripEventRequest request
    ) {
        try {
            Trip trip = tripRepository.findByIdAndTenantId(tripId, principal.tenantId())
                    .orElseThrow(() -> new TripEventNotFoundException("Trip was not found."));
            if (!authorizationService.canPublishTrip(principal, tripId)) {
                throw new TripEventForbiddenException("Publishing is not permitted.");
            }
            validate(request.eventType(), request.payload());

            EventAppendCommand command = new EventAppendCommand(
                    requestHash(trip, request),
                    request.eventId(),
                    principal.tenantId(),
                    trip.getId(),
                    trip.getCab().getId(),
                    request.eventType(),
                    request.occurredAt(),
                    Instant.now(clock),
                    priorityFor(request.eventType()),
                    request.payload()
            );
            AcceptedTripEvent accepted = eventStore.append(command);
            metrics.eventAccepted(accepted.envelope().eventType(), accepted.envelope().priority(), accepted.duplicate());
            if (!accepted.duplicate()) {
                eventPublisher.publish(accepted.envelope());
            }
            return accepted;
        } catch (TripEventConflictException exception) {
            metrics.eventRejected("conflict");
            throw exception;
        } catch (TripEventForbiddenException exception) {
            metrics.eventRejected("forbidden");
            throw exception;
        } catch (TripEventNotFoundException exception) {
            metrics.eventRejected("not_found");
            throw exception;
        } catch (TripEventValidationException exception) {
            metrics.eventRejected("invalid");
            throw exception;
        }
    }

    @Transactional(readOnly = true)
    public AcceptedTripEvent publishSos(CurrentPrincipal principal, String tripId, PublishSosRequest request) {
        return publishTripEvent(
                principal,
                tripId,
                new PublishTripEventRequest(
                        request.eventId(),
                        TripEventType.SOS_ALERT,
                        request.occurredAt(),
                        request.payload()
                )
        );
    }

    private void validate(TripEventType eventType, JsonNode payload) {
        if (payload == null || !payload.isObject()) {
            throw new TripEventValidationException("Payload must be a JSON object.");
        }
        if (eventType == TripEventType.LOCATION_UPDATE) {
            requireCoordinate(payload, "latitude", -90.0, 90.0);
            requireCoordinate(payload, "longitude", -180.0, 180.0);
            requireNonNegativeNumberIfPresent(payload, "accuracyMeters");
            requireNonNegativeNumberIfPresent(payload, "speedMetersPerSecond");
        }
    }

    private void requireCoordinate(JsonNode payload, String field, double min, double max) {
        JsonNode value = payload.get(field);
        if (value == null || !value.isNumber() || value.asDouble() < min || value.asDouble() > max) {
            throw new TripEventValidationException(field + " is out of range.");
        }
    }

    private void requireNonNegativeNumberIfPresent(JsonNode payload, String field) {
        JsonNode value = payload.get(field);
        if (value != null && (!value.isNumber() || value.asDouble() < 0.0)) {
            throw new TripEventValidationException(field + " must be non-negative.");
        }
    }

    private EventPriority priorityFor(TripEventType eventType) {
        return switch (eventType) {
            case LOCATION_UPDATE -> EventPriority.NORMAL;
            case SOS_ALERT -> EventPriority.CRITICAL;
            case TRIP_STATUS_CHANGED, TRIP_COMPLETED, TRIP_CANCELLED -> EventPriority.HIGH;
        };
    }

    private String requestHash(Trip trip, PublishTripEventRequest request) {
        ObjectNode node = objectMapper.createObjectNode();
        node.put("schemaVersion", SCHEMA_VERSION);
        node.put("eventId", request.eventId());
        node.put("tenantId", trip.getTenant().getId());
        node.put("tripId", trip.getId());
        node.put("cabId", trip.getCab().getId());
        node.put("eventType", request.eventType().name());
        node.put("occurredAt", request.occurredAt().toString());
        node.set("payload", request.payload());
        try {
            byte[] canonical = objectMapper.writeValueAsBytes(node);
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(canonical));
        } catch (JsonProcessingException exception) {
            throw new TripEventValidationException("Payload could not be serialized.");
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable.", exception);
        }
    }
}
