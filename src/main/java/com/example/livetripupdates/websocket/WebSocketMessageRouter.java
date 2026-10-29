package com.example.livetripupdates.websocket;

import com.example.livetripupdates.event.TripEventForbiddenException;
import com.example.livetripupdates.event.TripEventHistoryPage;
import com.example.livetripupdates.event.TripEventNotFoundException;
import com.example.livetripupdates.event.TripEventReplayService;
import com.example.livetripupdates.event.TripEventValidationException;
import com.example.livetripupdates.security.Topic;
import com.example.livetripupdates.security.TopicType;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.TextMessage;

import java.util.ArrayList;
import java.util.List;

@Component
public class WebSocketMessageRouter {

    private final ObjectMapper objectMapper;
    private final SubscriptionManager subscriptionManager;
    private final ConnectionManager connectionManager;
    private final TripEventReplayService replayService;

    public WebSocketMessageRouter(
            ObjectMapper objectMapper,
            SubscriptionManager subscriptionManager,
            ConnectionManager connectionManager,
            TripEventReplayService replayService
    ) {
        this.objectMapper = objectMapper;
        this.subscriptionManager = subscriptionManager;
        this.connectionManager = connectionManager;
        this.replayService = replayService;
    }

    public TextMessage route(String connectionId, String payload) {
        return routeAll(connectionId, payload).getFirst();
    }

    public List<TextMessage> routeAll(String connectionId, String payload) {
        ClientCommand command = readCommand(payload);
        return switch (normalizedAction(command.action())) {
            case "SUBSCRIBE" -> List.of(subscribe(connectionId, command));
            case "UNSUBSCRIBE" -> List.of(unsubscribe(connectionId, command));
            case "RESUME" -> resume(connectionId, command);
            case "ACK" -> List.of(error(command.requestId(), "NOT_IMPLEMENTED", "Acknowledgements are not implemented yet."));
            default -> List.of(error(command.requestId(), "UNKNOWN_ACTION", "Action is not supported."));
        };
    }

    private TextMessage subscribe(String connectionId, ClientCommand command) {
        requireTopic(command);
        subscriptionManager.subscribe(connectionId, command.topic());
        return message(ServerMessage.subscribed(command.requestId(), command.topic(), 0));
    }

    private TextMessage unsubscribe(String connectionId, ClientCommand command) {
        requireTopic(command);
        subscriptionManager.unsubscribe(connectionId, command.topic());
        return message(ServerMessage.unsubscribed(command.requestId(), command.topic()));
    }

    private List<TextMessage> resume(String connectionId, ClientCommand command) {
        requireTopic(command);
        if (command.lastReceivedSequence() == null || command.lastReceivedSequence() < 0) {
            return List.of(error(command.requestId(), "INVALID_REPLAY", "lastReceivedSequence is required."));
        }
        requireReplayTopic(command);
        return connectionManager.find(connectionId)
                .map(connection -> resumeKnownConnection(connectionId, command, connection))
                .orElseGet(() -> List.of(error(command.requestId(), "UNKNOWN_CONNECTION", "Connection is not registered.")));
    }

    private List<TextMessage> resumeKnownConnection(
            String connectionId,
            ClientCommand command,
            ClientConnection connection
    ) {
        boolean subscribed = false;
        try {
            subscriptionManager.subscribe(connectionId, command.topic());
            subscribed = true;
            TripEventHistoryPage page = replayService.replayTopic(
                    connection.principal(),
                    command.topic(),
                    command.lastReceivedSequence(),
                    100
            );
            List<TextMessage> messages = new ArrayList<>();
            messages.add(message(ServerMessage.subscribed(command.requestId(), command.topic(), page.currentSequence())));
            page.events().stream()
                    .map(this::message)
                    .forEach(messages::add);
            return messages;
        } catch (TripEventForbiddenException exception) {
            if (subscribed) {
                subscriptionManager.unsubscribe(connectionId, command.topic());
            }
            return List.of(error(command.requestId(), "FORBIDDEN", exception.getMessage()));
        } catch (TripEventNotFoundException exception) {
            if (subscribed) {
                subscriptionManager.unsubscribe(connectionId, command.topic());
            }
            return List.of(error(command.requestId(), "NOT_FOUND", exception.getMessage()));
        } catch (TripEventValidationException exception) {
            if (subscribed) {
                subscriptionManager.unsubscribe(connectionId, command.topic());
            }
            return List.of(error(command.requestId(), "INVALID_REPLAY", exception.getMessage()));
        }
    }

    private ClientCommand readCommand(String payload) {
        try {
            return objectMapper.readValue(payload, ClientCommand.class);
        } catch (JsonProcessingException exception) {
            throw new WebSocketProtocolException("MALFORMED_JSON", "Message must be valid JSON.");
        }
    }

    private String normalizedAction(String action) {
        return action == null ? "" : action.trim().toUpperCase();
    }

    private void requireTopic(ClientCommand command) {
        if (command.topic() == null || command.topic().isBlank()) {
            throw new WebSocketProtocolException("INVALID_TOPIC", "Topic is required.");
        }
    }

    private void requireReplayTopic(ClientCommand command) {
        Topic topic = Topic.parse(command.topic())
                .orElseThrow(() -> new WebSocketProtocolException("INVALID_TOPIC", "Topic is invalid."));
        if (topic.type() != TopicType.TRIP) {
            throw new WebSocketProtocolException("INVALID_REPLAY", "Replay currently supports trip topics.");
        }
    }

    private TextMessage error(String requestId, String code, String detail) {
        return message(ServerMessage.error(requestId, code, detail));
    }

    private TextMessage message(ServerMessage serverMessage) {
        try {
            return new TextMessage(objectMapper.writeValueAsString(serverMessage));
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Failed to serialize WebSocket message", exception);
        }
    }

    private TextMessage message(Object response) {
        try {
            return new TextMessage(objectMapper.writeValueAsString(response));
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Failed to serialize WebSocket message", exception);
        }
    }
}
