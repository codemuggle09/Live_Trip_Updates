package com.example.livetripupdates.websocket;

import com.example.livetripupdates.security.CurrentPrincipal;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.annotation.Qualifier;

import java.util.Collections;
import java.util.Collection;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;

@Component
public class ConnectionManager {

    private final Map<String, ClientConnection> connections = new ConcurrentHashMap<>();
    private final Map<String, Set<String>> topicSubscribers = new ConcurrentHashMap<>();
    private final int outboundQueueCapacity;
    private final Executor outboundExecutor;

    @Autowired
    public ConnectionManager(
            @Value("${app.websocket.outbound-queue-capacity:256}") int outboundQueueCapacity,
            @Qualifier("websocketOutboundExecutor") Executor websocketOutboundExecutor
    ) {
        this.outboundQueueCapacity = outboundQueueCapacity;
        this.outboundExecutor = websocketOutboundExecutor;
    }

    public ConnectionManager() {
        this(256, Runnable::run);
    }

    public ClientConnection register(WebSocketSession session, CurrentPrincipal principal) {
        ClientConnection connection = new ClientConnection(
                session.getId(),
                session,
                principal,
                outboundQueueCapacity,
                outboundExecutor
        );
        connections.put(connection.id(), connection);
        return connection;
    }

    public Optional<ClientConnection> find(String connectionId) {
        return Optional.ofNullable(connections.get(connectionId));
    }

    public synchronized void subscribe(String connectionId, String topic) {
        ClientConnection connection = requireConnection(connectionId);
        connection.addSubscription(topic);
        topicSubscribers.computeIfAbsent(topic, ignored -> new HashSet<>()).add(connectionId);
    }

    public synchronized void unsubscribe(String connectionId, String topic) {
        ClientConnection connection = connections.get(connectionId);
        if (connection == null) {
            return;
        }
        connection.removeSubscription(topic);
        Set<String> subscribers = topicSubscribers.get(topic);
        if (subscribers != null) {
            subscribers.remove(connectionId);
            if (subscribers.isEmpty()) {
                topicSubscribers.remove(topic);
            }
        }
    }

    public synchronized void unregister(String connectionId) {
        ClientConnection connection = connections.remove(connectionId);
        if (connection == null) {
            return;
        }
        connection.stop();
        for (String topic : connection.subscriptions()) {
            Set<String> subscribers = topicSubscribers.get(topic);
            if (subscribers != null) {
                subscribers.remove(connectionId);
                if (subscribers.isEmpty()) {
                    topicSubscribers.remove(topic);
                }
            }
        }
    }

    public synchronized Set<String> subscribers(String topic) {
        Set<String> subscribers = topicSubscribers.get(topic);
        if (subscribers == null) {
            return Set.of();
        }
        return Collections.unmodifiableSet(new HashSet<>(subscribers));
    }

    public synchronized Set<ClientConnection> subscribersForTopics(Collection<String> topics) {
        Set<ClientConnection> subscribers = new HashSet<>();
        for (String topic : topics) {
            Set<String> connectionIds = topicSubscribers.get(topic);
            if (connectionIds == null) {
                continue;
            }
            for (String connectionId : connectionIds) {
                ClientConnection connection = connections.get(connectionId);
                if (connection != null) {
                    subscribers.add(connection);
                }
            }
        }
        return Collections.unmodifiableSet(subscribers);
    }

    public int activeConnectionCount() {
        return connections.size();
    }

    private ClientConnection requireConnection(String connectionId) {
        ClientConnection connection = connections.get(connectionId);
        if (connection == null) {
            throw new IllegalArgumentException("Unknown connection");
        }
        return connection;
    }
}
