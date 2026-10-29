package com.example.livetripupdates.websocket;

import com.example.livetripupdates.event.EventPriority;
import com.example.livetripupdates.security.CurrentPrincipal;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.CloseStatus;

import java.io.IOException;
import java.time.Instant;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicBoolean;

public class ClientConnection {

    private final String id;
    private final WebSocketSession session;
    private final CurrentPrincipal principal;
    private final Set<String> subscriptions = new HashSet<>();
    private final Instant connectedAt;
    private final ArrayBlockingQueue<QueuedWebSocketMessage> normalQueue;
    private final ArrayBlockingQueue<QueuedWebSocketMessage> criticalQueue;
    private final Executor outboundExecutor;
    private final AtomicBoolean draining = new AtomicBoolean(false);
    private final AtomicBoolean closed = new AtomicBoolean(false);

    public ClientConnection(
            String id,
            WebSocketSession session,
            CurrentPrincipal principal,
            int outboundQueueCapacity,
            Executor outboundExecutor
    ) {
        this.id = id;
        this.session = session;
        this.principal = principal;
        this.connectedAt = Instant.now();
        this.normalQueue = new ArrayBlockingQueue<>(outboundQueueCapacity);
        this.criticalQueue = new ArrayBlockingQueue<>(Math.max(1, outboundQueueCapacity / 4));
        this.outboundExecutor = outboundExecutor;
    }

    public String id() {
        return id;
    }

    public WebSocketSession session() {
        return session;
    }

    public CurrentPrincipal principal() {
        return principal;
    }

    public Instant connectedAt() {
        return connectedAt;
    }

    public synchronized boolean addSubscription(String topic) {
        return subscriptions.add(topic);
    }

    public synchronized boolean removeSubscription(String topic) {
        return subscriptions.remove(topic);
    }

    public synchronized Set<String> subscriptions() {
        return Collections.unmodifiableSet(new HashSet<>(subscriptions));
    }

    public void send(TextMessage message) throws IOException {
        send(message, EventPriority.NORMAL);
    }

    public void send(TextMessage message, EventPriority priority) throws IOException {
        if (closed.get() || !session.isOpen()) {
            return;
        }
        ArrayBlockingQueue<QueuedWebSocketMessage> targetQueue =
                priority == EventPriority.CRITICAL ? criticalQueue : normalQueue;
        if (!targetQueue.offer(new QueuedWebSocketMessage(message, priority))) {
            close(CloseStatus.SERVICE_OVERLOAD.withReason("Outbound queue is full."));
            return;
        }
        scheduleDrain();
    }

    public void close(CloseStatus status) throws IOException {
        if (closed.compareAndSet(false, true) && session.isOpen()) {
            session.close(status);
        }
    }

    public void stop() {
        closed.set(true);
        normalQueue.clear();
        criticalQueue.clear();
    }

    int queuedMessages() {
        return normalQueue.size() + criticalQueue.size();
    }

    private void scheduleDrain() {
        if (!draining.compareAndSet(false, true)) {
            return;
        }
        outboundExecutor.execute(this::drain);
    }

    private void drain() {
        try {
            QueuedWebSocketMessage queuedMessage;
            while (!closed.get() && session.isOpen() && (queuedMessage = pollNext()) != null) {
                synchronized (session) {
                    if (session.isOpen()) {
                        session.sendMessage(queuedMessage.message());
                    }
                }
            }
        } catch (IOException exception) {
            try {
                close(CloseStatus.SERVER_ERROR.withReason("WebSocket send failed."));
            } catch (IOException ignored) {
                closed.set(true);
            }
        } finally {
            draining.set(false);
            if (queuedMessages() > 0 && !closed.get()) {
                scheduleDrain();
            }
        }
    }

    private QueuedWebSocketMessage pollNext() {
        QueuedWebSocketMessage critical = criticalQueue.poll();
        if (critical != null) {
            return critical;
        }
        return normalQueue.poll();
    }
}
