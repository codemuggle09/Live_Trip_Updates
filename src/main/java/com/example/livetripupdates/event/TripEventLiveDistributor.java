package com.example.livetripupdates.event;

import com.example.livetripupdates.observability.TripMetrics;
import com.example.livetripupdates.websocket.ClientConnection;
import com.example.livetripupdates.websocket.ConnectionManager;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.TextMessage;

import java.util.List;
import java.util.Set;

@Component
public class TripEventLiveDistributor {

    private static final Logger log = LoggerFactory.getLogger(TripEventLiveDistributor.class);

    private final ConnectionManager connectionManager;
    private final ObjectMapper objectMapper;
    private final TripMetrics metrics;

    public TripEventLiveDistributor(ConnectionManager connectionManager, ObjectMapper objectMapper, TripMetrics metrics) {
        this.connectionManager = connectionManager;
        this.objectMapper = objectMapper;
        this.metrics = metrics;
    }

    public int distribute(TripEventEnvelope envelope) {
        TextMessage message = new TextMessage(writeEnvelope(envelope));
        Set<ClientConnection> subscribers = connectionManager.subscribersForTopics(List.of(
                "trip:" + envelope.tripId(),
                "cab:" + envelope.cabId()
        ));
        int delivered = 0;
        for (ClientConnection subscriber : subscribers) {
            try {
                subscriber.send(message, envelope.priority());
                delivered++;
            } catch (Exception exception) {
                log.warn("failed to deliver trip event to websocket subscriber");
            }
        }
        metrics.eventsDelivered(envelope.priority(), delivered);
        return delivered;
    }

    public int distribute(String envelopeJson) {
        try {
            return distribute(objectMapper.readValue(envelopeJson, TripEventEnvelope.class));
        } catch (JsonProcessingException exception) {
            log.warn("ignored malformed trip event notification");
            return 0;
        }
    }

    private String writeEnvelope(TripEventEnvelope envelope) {
        try {
            return objectMapper.writeValueAsString(envelope);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Event envelope could not be serialized.", exception);
        }
    }
}
