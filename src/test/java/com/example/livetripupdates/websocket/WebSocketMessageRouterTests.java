package com.example.livetripupdates.websocket;

import com.example.livetripupdates.event.EventPriority;
import com.example.livetripupdates.event.TripEventEnvelope;
import com.example.livetripupdates.event.TripEventHistoryPage;
import com.example.livetripupdates.event.TripEventReplayService;
import com.example.livetripupdates.event.TripEventType;
import com.example.livetripupdates.domain.UserRole;
import com.example.livetripupdates.security.CurrentPrincipal;
import com.example.livetripupdates.security.TripAuthorizationService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.TextMessage;

import java.time.Instant;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class WebSocketMessageRouterTests {

    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();
    private final TripAuthorizationService authorizationService = mock(TripAuthorizationService.class);
    private final TripEventReplayService replayService = mock(TripEventReplayService.class);
    private final ConnectionManager connectionManager = new ConnectionManager();
    private final SubscriptionManager subscriptionManager =
            new SubscriptionManager(connectionManager, authorizationService);
    private final WebSocketMessageRouter router =
            new WebSocketMessageRouter(objectMapper, subscriptionManager, connectionManager, replayService);

    @Test
    void subscribeAddsAuthorizedConnectionToTopic() throws Exception {
        registerConnection("conn-1");
        CurrentPrincipal principal = principal();
        when(authorizationService.canSubscribeTopic(principal, "trip:trip-1")).thenReturn(true);

        String response = router.route("conn-1", """
                {"action":"SUBSCRIBE","requestId":"req-1","topic":"trip:trip-1"}
                """).getPayload();

        JsonNode json = objectMapper.readTree(response);
        assertThat(json.get("type").asText()).isEqualTo("SUBSCRIBED");
        assertThat(json.get("requestId").asText()).isEqualTo("req-1");
        assertThat(connectionManager.subscribers("trip:trip-1")).containsExactly("conn-1");
    }

    @Test
    void unsubscribeRemovesConnectionFromTopic() throws Exception {
        registerConnection("conn-1");
        CurrentPrincipal principal = principal();
        when(authorizationService.canSubscribeTopic(principal, "trip:trip-1")).thenReturn(true);
        router.route("conn-1", """
                {"action":"SUBSCRIBE","requestId":"req-1","topic":"trip:trip-1"}
                """);

        String response = router.route("conn-1", """
                {"action":"UNSUBSCRIBE","requestId":"req-2","topic":"trip:trip-1"}
                """).getPayload();

        JsonNode json = objectMapper.readTree(response);
        assertThat(json.get("type").asText()).isEqualTo("UNSUBSCRIBED");
        assertThat(connectionManager.subscribers("trip:trip-1")).isEmpty();
    }

    @Test
    void invalidTopicIsRejected() {
        registerConnection("conn-1");

        assertThatThrownBy(() -> router.route("conn-1", """
                {"action":"SUBSCRIBE","requestId":"req-1","topic":"trip:../trip-1"}
                """))
                .isInstanceOf(WebSocketProtocolException.class)
                .hasMessage("Topic is invalid.");
    }

    @Test
    void unregisterCleansSubscriptionIndexes() {
        registerConnection("conn-1");
        CurrentPrincipal principal = principal();
        when(authorizationService.canSubscribeTopic(principal, "trip:trip-1")).thenReturn(true);
        router.route("conn-1", """
                {"action":"SUBSCRIBE","requestId":"req-1","topic":"trip:trip-1"}
                """);

        connectionManager.unregister("conn-1");

        assertThat(connectionManager.subscribers("trip:trip-1")).isEmpty();
        assertThat(connectionManager.activeConnectionCount()).isZero();
    }

    @Test
    void resumeSubscribesAndReturnsReplayEvents() throws Exception {
        registerConnection("conn-1");
        CurrentPrincipal principal = principal();
        when(authorizationService.canSubscribeTopic(principal, "trip:trip-1")).thenReturn(true);
        when(replayService.replayTopic(principal, "trip:trip-1", 3, 100))
                .thenReturn(new TripEventHistoryPage(List.of(envelope(4)), 4, 4));

        List<TextMessage> responses = router.routeAll("conn-1", """
                {"action":"RESUME","requestId":"req-3","topic":"trip:trip-1","lastReceivedSequence":3}
                """);

        JsonNode subscribed = objectMapper.readTree(responses.get(0).getPayload());
        JsonNode event = objectMapper.readTree(responses.get(1).getPayload());
        assertThat(subscribed.get("type").asText()).isEqualTo("SUBSCRIBED");
        assertThat(subscribed.get("currentSequence").asLong()).isEqualTo(4);
        assertThat(event.get("eventId").asText()).isEqualTo("event-4");
        assertThat(connectionManager.subscribers("trip:trip-1")).containsExactly("conn-1");
    }

    private void registerConnection(String connectionId) {
        WebSocketSession session = mock(WebSocketSession.class);
        when(session.getId()).thenReturn(connectionId);
        connectionManager.register(session, principal());
    }

    private CurrentPrincipal principal() {
        return new CurrentPrincipal("employee-1", "tenant-1", Set.of(UserRole.EMPLOYEE));
    }

    private TripEventEnvelope envelope(long sequenceNumber) {
        ObjectNode payload = objectMapper.createObjectNode()
                .put("latitude", 28.6139)
                .put("longitude", 77.2090);
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
}
