package com.example.livetripupdates.event;

import com.example.livetripupdates.domain.Cab;
import com.example.livetripupdates.domain.Tenant;
import com.example.livetripupdates.domain.Trip;
import com.example.livetripupdates.domain.TripStatus;
import com.example.livetripupdates.domain.User;
import com.example.livetripupdates.domain.UserRole;
import com.example.livetripupdates.observability.TripMetrics;
import com.example.livetripupdates.persistence.TripRepository;
import com.example.livetripupdates.security.CurrentPrincipal;
import com.example.livetripupdates.security.TripAuthorizationService;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TripEventIngestionServiceTests {

    private static final Instant NOW = Instant.parse("2026-10-29T10:30:00Z");

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final TripRepository tripRepository = mock(TripRepository.class);
    private final TripAuthorizationService authorizationService = mock(TripAuthorizationService.class);
    private final FakeTripEventStore eventStore = new FakeTripEventStore();
    private final TripEventPublisher eventPublisher = mock(TripEventPublisher.class);
    private final TripMetrics metrics = mock(TripMetrics.class);
    private final TripEventIngestionService ingestionService = new TripEventIngestionService(
            tripRepository,
            authorizationService,
            eventStore,
            eventPublisher,
            objectMapper,
            Clock.fixed(NOW, ZoneOffset.UTC),
            metrics
    );

    private final CurrentPrincipal driver = new CurrentPrincipal("driver-1", "tenant-1", Set.of(UserRole.DRIVER));
    private Trip trip;

    @BeforeEach
    void setUp() {
        Tenant tenant = new Tenant("tenant-1", "Tenant One");
        User driverUser = new User("driver-1", tenant, "driver-one", UserRole.DRIVER);
        Cab cab = new Cab("cab-1", tenant, driverUser);
        trip = new Trip("trip-1", tenant, cab, null, "office-1", TripStatus.ACTIVE);

        when(tripRepository.findByIdAndTenantId("trip-1", "tenant-1")).thenReturn(Optional.of(trip));
        when(authorizationService.canPublishTrip(driver, "trip-1")).thenReturn(true);
    }

    @Test
    void publishLocationStoresSequenceAndPublishesNotification() {
        AcceptedTripEvent accepted = ingestionService.publishTripEvent(
                driver,
                "trip-1",
                locationRequest("event-1", 28.6139, 77.2090)
        );

        TripEventEnvelope envelope = accepted.envelope();
        assertThat(accepted.duplicate()).isFalse();
        assertThat(envelope.tenantId()).isEqualTo("tenant-1");
        assertThat(envelope.tripId()).isEqualTo("trip-1");
        assertThat(envelope.cabId()).isEqualTo("cab-1");
        assertThat(envelope.sequenceNumber()).isEqualTo(1);
        assertThat(envelope.priority()).isEqualTo(EventPriority.NORMAL);
        assertThat(envelope.acceptedAt()).isEqualTo(NOW);
        verify(metrics).eventAccepted(TripEventType.LOCATION_UPDATE, EventPriority.NORMAL, false);
        verify(eventPublisher).publish(envelope);
    }

    @Test
    void duplicateEventIdWithSameContentReturnsExistingSequenceWithoutRepublishing() {
        PublishTripEventRequest request = locationRequest("event-1", 28.6139, 77.2090);

        AcceptedTripEvent first = ingestionService.publishTripEvent(driver, "trip-1", request);
        AcceptedTripEvent second = ingestionService.publishTripEvent(driver, "trip-1", request);

        assertThat(second.duplicate()).isTrue();
        assertThat(second.envelope().sequenceNumber()).isEqualTo(first.envelope().sequenceNumber());
        assertThat(second.envelope().acceptedAt()).isEqualTo(first.envelope().acceptedAt());
        verify(metrics).eventAccepted(TripEventType.LOCATION_UPDATE, EventPriority.NORMAL, true);
        verify(eventPublisher).publish(first.envelope());
    }

    @Test
    void duplicateEventIdWithDifferentContentIsRejected() {
        ingestionService.publishTripEvent(driver, "trip-1", locationRequest("event-1", 28.6139, 77.2090));

        assertThatThrownBy(() -> ingestionService.publishTripEvent(
                driver,
                "trip-1",
                locationRequest("event-1", 12.9716, 77.5946)
        ))
                .isInstanceOf(TripEventConflictException.class)
                .hasMessage("eventId already exists with different content.");
    }

    @Test
    void invalidCoordinatesAreRejectedBeforeStorage() {
        assertThatThrownBy(() -> ingestionService.publishTripEvent(
                driver,
                "trip-1",
                locationRequest("event-1", 91.0, 77.2090)
        ))
                .isInstanceOf(TripEventValidationException.class)
                .hasMessage("latitude is out of range.");

        verify(metrics).eventRejected("invalid");
        assertThat(eventStore.appendCount()).isZero();
    }

    @Test
    void unauthorizedPublisherIsRejected() {
        CurrentPrincipal employee = new CurrentPrincipal("employee-1", "tenant-1", Set.of(UserRole.EMPLOYEE));
        when(authorizationService.canPublishTrip(employee, "trip-1")).thenReturn(false);

        assertThatThrownBy(() -> ingestionService.publishTripEvent(
                employee,
                "trip-1",
                locationRequest("event-1", 28.6139, 77.2090)
        ))
                .isInstanceOf(TripEventForbiddenException.class)
                .hasMessage("Publishing is not permitted.");

        verify(eventPublisher, never()).publish(org.mockito.ArgumentMatchers.any());
        verify(metrics).eventRejected("forbidden");
    }

    @Test
    void sosEndpointForcesCriticalSosEventType() {
        ObjectNode payload = objectMapper.createObjectNode().put("message", "Need help");

        AcceptedTripEvent accepted = ingestionService.publishSos(
                driver,
                "trip-1",
                new PublishSosRequest("sos-1", NOW, payload)
        );

        assertThat(accepted.envelope().eventType()).isEqualTo(TripEventType.SOS_ALERT);
        assertThat(accepted.envelope().priority()).isEqualTo(EventPriority.CRITICAL);
    }

    private PublishTripEventRequest locationRequest(String eventId, double latitude, double longitude) {
        ObjectNode payload = objectMapper.createObjectNode()
                .put("latitude", latitude)
                .put("longitude", longitude)
                .put("accuracyMeters", 8.0);
        return new PublishTripEventRequest(eventId, TripEventType.LOCATION_UPDATE, NOW, payload);
    }

    private static class FakeTripEventStore implements TripEventStore {

        private final Map<String, AcceptedTripEvent> acceptedByEventId = new HashMap<>();
        private final Map<String, String> hashByEventId = new HashMap<>();
        private long nextSequence = 1;
        private int appendCount;

        @Override
        public AcceptedTripEvent append(EventAppendCommand command) {
            appendCount++;
            String previousHash = hashByEventId.get(command.eventId());
            if (previousHash != null && !previousHash.equals(command.requestHash())) {
                throw new TripEventConflictException("eventId already exists with different content.");
            }
            AcceptedTripEvent previous = acceptedByEventId.get(command.eventId());
            if (previous != null) {
                return new AcceptedTripEvent(previous.envelope(), true);
            }
            TripEventEnvelope envelope = new TripEventEnvelope(
                    1,
                    command.eventId(),
                    command.tenantId(),
                    command.tripId(),
                    command.cabId(),
                    nextSequence++,
                    command.eventType(),
                    command.occurredAt(),
                    command.acceptedAt(),
                    command.priority(),
                    command.payload()
            );
            AcceptedTripEvent accepted = new AcceptedTripEvent(envelope, false);
            hashByEventId.put(command.eventId(), command.requestHash());
            acceptedByEventId.put(command.eventId(), accepted);
            return accepted;
        }

        int appendCount() {
            return appendCount;
        }

        @Override
        public List<TripEventEnvelope> readAfter(String tripId, long afterSequence, int limit) {
            return acceptedByEventId.values().stream()
                    .map(AcceptedTripEvent::envelope)
                    .filter(envelope -> envelope.tripId().equals(tripId))
                    .filter(envelope -> envelope.sequenceNumber() > afterSequence)
                    .limit(limit)
                    .toList();
        }

        @Override
        public long currentSequence(String tripId) {
            return nextSequence - 1;
        }
    }
}
