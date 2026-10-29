package com.example.livetripupdates.websocket;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record ClientCommand(
        String action,
        String requestId,
        String topic,
        Long lastReceivedSequence,
        Long lastProcessedSequence
) {
}
