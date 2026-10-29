package com.example.livetripupdates.config;

import com.example.livetripupdates.websocket.TripWebSocketHandler;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

@Configuration
@EnableWebSocket
public class WebSocketConfig implements WebSocketConfigurer {

    private final TripWebSocketHandler tripWebSocketHandler;
    private final String[] allowedOriginPatterns;

    public WebSocketConfig(
            TripWebSocketHandler tripWebSocketHandler,
            @Value("${app.websocket.allowed-origin-patterns:http://localhost:*}") String allowedOriginPatterns
    ) {
        this.tripWebSocketHandler = tripWebSocketHandler;
        this.allowedOriginPatterns = allowedOriginPatterns.split(",");
    }

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(tripWebSocketHandler, "/ws")
                .setAllowedOriginPatterns(allowedOriginPatterns);
    }
}
