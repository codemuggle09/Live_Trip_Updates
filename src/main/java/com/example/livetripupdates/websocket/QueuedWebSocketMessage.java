package com.example.livetripupdates.websocket;

import com.example.livetripupdates.event.EventPriority;
import org.springframework.web.socket.TextMessage;

record QueuedWebSocketMessage(TextMessage message, EventPriority priority) {
}
