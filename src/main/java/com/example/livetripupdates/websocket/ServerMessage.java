package com.example.livetripupdates.websocket;

public record ServerMessage(
        String type,
        String requestId,
        String topic,
        String code,
        String message,
        Long currentSequence
) {

    public static ServerMessage subscribed(String requestId, String topic, long currentSequence) {
        return new ServerMessage("SUBSCRIBED", requestId, topic, null, null, currentSequence);
    }

    public static ServerMessage unsubscribed(String requestId, String topic) {
        return new ServerMessage("UNSUBSCRIBED", requestId, topic, null, null, null);
    }

    public static ServerMessage error(String requestId, String code, String message) {
        return new ServerMessage("ERROR", requestId, null, code, message, null);
    }
}
