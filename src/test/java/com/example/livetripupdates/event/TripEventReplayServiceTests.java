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

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class TripEventReplayServiceTests {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final TripRepository tripRepository = mock(TripRepository.class);
    private final TripAuthorizationService authorizationService = mock(TripAuthorizationService.class);
    private final FakeReplayStore eventStore = new FakeReplayStore();
    private final TripMetrics metrics = mock(TripMetrics.class);
    private final TripEventReplayService replayService = new TripEventReplayService(
            tripRepository,
            authorizationService,
            eventStore,
            2,
            metrics
    );

    private final CurrentPrincipal employee = new CurrentPrincipal("employee-1", "tenant-1", Set.of(UserRole.EMPLOYEE));

    @BeforeEach
    void setUp() {
        Tenant tenant = new Tenant("tenant-1", "Tenant One");
        User employeeUser = new User("employee-1", tenant, "employee-one", UserRole.EMPLOYEE);
        Cab cab = new Cab("cab-1", tenant, null);
        Trip trip = new Trip("trip-1", tenant, cab, employeeUser, "office-1", TripStatus.ACTIVE);
        when(tripRepository.findByIdAndTenantId("trip-1", "tenant-1")).thenReturn(Optional.of(trip));
        when(authorizationService.canReplayTrip(employee, "trip-1")).thenReturn(true);
        eventStore.add(event(1));
        eventStore.add(event(2));
        eventStore.add(event(3));
    }

    @Test
    void replayReturnsEventsAfterSequenceWithConfiguredLimit() {
        TripEventHistoryPage page = replayService.replayTrip(employee, "trip-1", 1, 10);

        assertThat(page.events()).extracting(TripEventEnvelope::sequenceNumber)
                .containsExactly(2L, 3L);
        assertThat(page.nextAfterSequence()).isEqualTo(3);
        assertThat(page.currentSequence()).isEqualTo(3);
        org.mockito.Mockito.verify(metrics).replayRequested();
        org.mockito.Mockito.verify(metrics).eventsReplayed(2);
    }

    @Test
    void replayRejectsNegativeSequence() {
        assertThatThrownBy(() -> replayService.replayTrip(employee, "trip-1", -1, 10))
                .isInstanceOf(TripEventValidationException.class)
                .hasMessage("afterSequence must be non-negative.");
    }

    @Test
    void replayRechecksAuthorization() {
        CurrentPrincipal other = new CurrentPrincipal("employee-2", "tenant-1", Set.of(UserRole.EMPLOYEE));
        when(tripRepository.findByIdAndTenantId("trip-1", "tenant-1")).thenReturn(Optional.of(mock(Trip.class)));
        when(authorizationService.canReplayTrip(other, "trip-1")).thenReturn(false);

        assertThatThrownBy(() -> replayService.replayTrip(other, "trip-1", 0, 10))
                .isInstanceOf(TripEventForbiddenException.class)
                .hasMessage("Replay is not permitted.");
    }

    @Test
    void topicReplayOnlySupportsTripTopics() {
        assertThatThrownBy(() -> replayService.replayTopic(employee, "cab:cab-1", 0, 10))
                .isInstanceOf(TripEventValidationException.class)
                .hasMessage("Replay currently supports trip topics.");
    }

    private TripEventEnvelope event(long sequenceNumber) {
        ObjectNode payload = objectMapper.createObjectNode()
                .put("latitude", 28.0 + sequenceNumber)
                .put("longitude", 77.0);
        return new TripEventEnvelope(
                1,
                "event-" + sequenceNumber,
                "tenant-1",
                "trip-1",
                "cab-1",
                sequenceNumber,
                TripEventType.LOCATION_UPDATE,
                Instant.parse("2026-10-29T10:30:00Z"),
                Instant.parse("2026-10-29T10:30:01Z"),
                EventPriority.NORMAL,
                payload
        );
    }

    private static class FakeReplayStore implements TripEventStore {

        private final List<TripEventEnvelope> events = new ArrayList<>();

        void add(TripEventEnvelope envelope) {
            events.add(envelope);
        }

        @Override
        public AcceptedTripEvent append(EventAppendCommand command) {
            throw new UnsupportedOperationException("append is not used in replay tests");
        }

        @Override
        public List<TripEventEnvelope> readAfter(String tripId, long afterSequence, int limit) {
            return events.stream()
                    .filter(envelope -> envelope.tripId().equals(tripId))
                    .filter(envelope -> envelope.sequenceNumber() > afterSequence)
                    .sorted(Comparator.comparingLong(TripEventEnvelope::sequenceNumber))
                    .limit(limit)
                    .toList();
        }

        @Override
        public long currentSequence(String tripId) {
            return events.stream()
                    .filter(envelope -> envelope.tripId().equals(tripId))
                    .mapToLong(TripEventEnvelope::sequenceNumber)
                    .max()
                    .orElse(0);
        }
    }
}
