package com.example.livetripupdates.websocket;

import com.example.livetripupdates.event.EventPriority;
import com.example.livetripupdates.domain.UserRole;
import com.example.livetripupdates.security.CurrentPrincipal;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import java.util.Set;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ClientConnectionTests {

    @Test
    void sendsQueuedMessagesThroughDispatcher() throws Exception {
        WebSocketSession session = session();
        ClientConnection connection = new ClientConnection(
                "conn-1",
                session,
                principal(),
                2,
                Runnable::run
        );

        connection.send(new TextMessage("hello"));

        verify(session).sendMessage(org.mockito.ArgumentMatchers.argThat(message ->
                "hello".equals(message.getPayload())
        ));
        assertThat(connection.queuedMessages()).isZero();
    }

    @Test
    void closesConnectionWhenOutboundQueueIsFull() throws Exception {
        WebSocketSession session = session();
        ClientConnection connection = new ClientConnection(
                "conn-1",
                session,
                principal(),
                1,
                ignored -> {
                }
        );

        connection.send(new TextMessage("first"));
        connection.send(new TextMessage("second"));

        ArgumentCaptor<CloseStatus> captor = ArgumentCaptor.forClass(CloseStatus.class);
        verify(session).close(captor.capture());
        assertThat(captor.getValue().getCode()).isEqualTo(CloseStatus.SERVICE_OVERLOAD.getCode());
        assertThat(connection.queuedMessages()).isEqualTo(1);
    }

    @Test
    void sendsCriticalMessagesBeforeNormalMessages() throws Exception {
        WebSocketSession session = session();
        CapturingExecutor executor = new CapturingExecutor();
        ClientConnection connection = new ClientConnection(
                "conn-1",
                session,
                principal(),
                4,
                executor
        );

        connection.send(new TextMessage("normal"), EventPriority.NORMAL);
        connection.send(new TextMessage("critical"), EventPriority.CRITICAL);
        executor.runAll();

        ArgumentCaptor<TextMessage> captor = ArgumentCaptor.forClass(TextMessage.class);
        verify(session, org.mockito.Mockito.times(2)).sendMessage(captor.capture());
        assertThat(captor.getAllValues()).extracting(TextMessage::getPayload)
                .containsExactly("critical", "normal");
    }

    private WebSocketSession session() {
        WebSocketSession session = mock(WebSocketSession.class);
        when(session.isOpen()).thenReturn(true);
        return session;
    }

    private CurrentPrincipal principal() {
        return new CurrentPrincipal("employee-1", "tenant-1", Set.of(UserRole.EMPLOYEE));
    }

    private static class CapturingExecutor implements Executor {

        private final List<Runnable> tasks = new ArrayList<>();

        @Override
        public void execute(Runnable command) {
            tasks.add(command);
        }

        void runAll() {
            List.copyOf(tasks).forEach(Runnable::run);
            tasks.clear();
        }
    }
}
