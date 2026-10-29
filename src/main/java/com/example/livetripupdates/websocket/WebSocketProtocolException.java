package com.example.livetripupdates.websocket;

public class WebSocketProtocolException extends RuntimeException {

    private final String code;

    public WebSocketProtocolException(String code, String message) {
        super(message);
        this.code = code;
    }

    public String code() {
        return code;
    }
}
