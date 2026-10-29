package com.example.livetripupdates.websocket;

import com.example.livetripupdates.security.Topic;
import com.example.livetripupdates.security.TripAuthorizationService;
import org.springframework.stereotype.Component;

@Component
public class SubscriptionManager {

    private final ConnectionManager connectionManager;
    private final TripAuthorizationService authorizationService;

    public SubscriptionManager(ConnectionManager connectionManager, TripAuthorizationService authorizationService) {
        this.connectionManager = connectionManager;
        this.authorizationService = authorizationService;
    }

    public void subscribe(String connectionId, String topic) {
        ClientConnection connection = connectionManager.find(connectionId)
                .orElseThrow(() -> new WebSocketProtocolException("UNKNOWN_CONNECTION", "Connection is not registered."));
        if (Topic.parse(topic).isEmpty()) {
            throw new WebSocketProtocolException("INVALID_TOPIC", "Topic is invalid.");
        }
        if (!authorizationService.canSubscribeTopic(connection.principal(), topic)) {
            throw new WebSocketProtocolException("FORBIDDEN", "Subscription is not permitted.");
        }
        connectionManager.subscribe(connectionId, topic);
    }

    public void unsubscribe(String connectionId, String topic) {
        if (Topic.parse(topic).isEmpty()) {
            throw new WebSocketProtocolException("INVALID_TOPIC", "Topic is invalid.");
        }
        connectionManager.unsubscribe(connectionId, topic);
    }
}
