package com.example.livetripupdates.event;

import com.example.livetripupdates.observability.TripMetrics;
import com.example.livetripupdates.domain.UserRole;
import com.example.livetripupdates.security.CurrentPrincipal;
import com.example.livetripupdates.websocket.ConnectionManager;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import java.time.Instant;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TripEventLiveDistributorTests {

    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();
    private final ConnectionManager connectionManager = new ConnectionManager();
    private final TripMetrics metrics = mock(TripMetrics.class);
    private final TripEventLiveDistributor distributor =
            new TripEventLiveDistributor(connectionManager, objectMapper, metrics);

    @Test
    void distributesToTripAndCabSubscribersWithoutDuplicateDelivery() throws Exception {
        WebSocketSession tripSubscriber = register("conn-1");
        WebSocketSession cabSubscriber = register("conn-2");
        WebSocketSession bothSubscriber = register("conn-3");
        connectionManager.subscribe("conn-1", "trip:trip-1");
        connectionManager.subscribe("conn-2", "cab:cab-1");
        connectionManager.subscribe("conn-3", "trip:trip-1");
        connectionManager.subscribe("conn-3", "cab:cab-1");

        int delivered = distributor.distribute(envelope());

        assertThat(delivered).isEqualTo(3);
        verify(tripSubscriber).sendMessage(org.mockito.ArgumentMatchers.any(TextMessage.class));
        verify(cabSubscriber).sendMessage(org.mockito.ArgumentMatchers.any(TextMessage.class));
        ArgumentCaptor<TextMessage> captor = ArgumentCaptor.forClass(TextMessage.class);
        verify(bothSubscriber, times(1)).sendMessage(captor.capture());
        assertThat(objectMapper.readTree(captor.getValue().getPayload()).get("sequenceNumber").asLong())
                .isEqualTo(11);
        verify(metrics).eventsDelivered(EventPriority.NORMAL, 3);
    }

    private WebSocketSession register(String connectionId) {
        WebSocketSession session = mock(WebSocketSession.class);
        when(session.getId()).thenReturn(connectionId);
        when(session.isOpen()).thenReturn(true);
        connectionManager.register(
                session,
                new CurrentPrincipal("employee-1", "tenant-1", Set.of(UserRole.EMPLOYEE))
        );
        return session;
    }

    private TripEventEnvelope envelope() {
        ObjectNode payload = objectMapper.createObjectNode()
                .put("latitude", 28.6139)
                .put("longitude", 77.2090);
        return new TripEventEnvelope(
                1,
                "event-1",
                "tenant-1",
                "trip-1",
                "cab-1",
                11,
                TripEventType.LOCATION_UPDATE,
                Instant.parse("2026-10-29T10:30:00Z"),
                Instant.parse("2026-10-29T10:30:01Z"),
                EventPriority.NORMAL,
                payload
        );
    }
}
