package com.example.livetripupdates.websocket;

import com.example.livetripupdates.security.CurrentPrincipal;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.security.Principal;
import java.util.List;

@Component
public class TripWebSocketHandler extends TextWebSocketHandler {

    private static final Logger log = LoggerFactory.getLogger(TripWebSocketHandler.class);

    private final ConnectionManager connectionManager;
    private final WebSocketMessageRouter messageRouter;
    private final ObjectMapper objectMapper;

    public TripWebSocketHandler(
            ConnectionManager connectionManager,
            WebSocketMessageRouter messageRouter,
            ObjectMapper objectMapper
    ) {
        this.connectionManager = connectionManager;
        this.messageRouter = messageRouter;
        this.objectMapper = objectMapper;
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        Principal sessionPrincipal = session.getPrincipal();
        if (sessionPrincipal == null) {
            session.close(CloseStatus.POLICY_VIOLATION.withReason("Authentication is required."));
            return;
        }
        CurrentPrincipal principal = CurrentPrincipal.from(sessionPrincipal);
        if (!principal.isComplete()) {
            session.close(CloseStatus.POLICY_VIOLATION.withReason("Required principal claims are missing."));
            return;
        }
        connectionManager.register(session, principal);
        log.info("websocket connection registered");
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        try {
            List<TextMessage> responses = messageRouter.routeAll(session.getId(), message.getPayload());
            connectionManager.find(session.getId())
                    .ifPresent(connection -> responses.forEach(response -> sendQuietly(connection, response)));
        } catch (WebSocketProtocolException exception) {
            TextMessage error = new TextMessage(objectMapper.writeValueAsString(
                    ServerMessage.error(null, exception.code(), exception.getMessage())
            ));
            connectionManager.find(session.getId())
                    .ifPresent(connection -> sendQuietly(connection, error));
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        connectionManager.unregister(session.getId());
        log.info("websocket connection closed");
    }

    private void sendQuietly(ClientConnection connection, TextMessage response) {
        try {
            connection.send(response);
        } catch (Exception exception) {
            log.warn("failed to send websocket response");
        }
    }
}
