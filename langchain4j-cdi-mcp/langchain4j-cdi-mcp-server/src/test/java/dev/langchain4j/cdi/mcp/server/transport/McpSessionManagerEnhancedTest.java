package dev.langchain4j.cdi.mcp.server.transport;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.langchain4j.cdi.mcp.server.error.McpSessionException;
import java.time.Duration;
import org.junit.jupiter.api.Test;

class McpSessionManagerEnhancedTest {

    @Test
    void shouldExpireSessionAfterTimeout() throws InterruptedException {
        McpSessionManager manager = new McpSessionManager(Duration.ofMillis(50));
        try {
            String sessionId = manager.createSession(null);
            assertThat(manager.activeSessionCount()).isEqualTo(1);

            Thread.sleep(100);
            manager.cleanupExpiredSessions();

            assertThat(manager.activeSessionCount()).isEqualTo(0);
            assertThatThrownBy(() -> manager.requireSession("req", sessionId)).isInstanceOf(McpSessionException.class);
        } finally {
            manager.shutdown();
        }
    }

    @Test
    void shouldNotExpireActiveSession() throws InterruptedException {
        McpSessionManager manager = new McpSessionManager(Duration.ofMillis(200));
        try {
            String sessionId = manager.createSession(null);

            Thread.sleep(50);
            manager.requireSession("req", sessionId); // touch

            Thread.sleep(50);
            manager.cleanupExpiredSessions();

            assertThat(manager.activeSessionCount()).isEqualTo(1);
        } finally {
            manager.shutdown();
        }
    }

    @Test
    void shouldCleanupMultipleExpiredSessions() throws InterruptedException {
        McpSessionManager manager = new McpSessionManager(Duration.ofMillis(50));
        try {
            manager.createSession(null);
            manager.createSession(null);
            manager.createSession(null);

            Thread.sleep(100);
            manager.cleanupExpiredSessions();

            assertThat(manager.activeSessionCount()).isEqualTo(0);
        } finally {
            manager.shutdown();
        }
    }

    @Test
    void shouldUpdateLastAccessOnTouch() {
        McpSessionManager manager = new McpSessionManager(Duration.ofMinutes(30));
        try {
            String sessionId = manager.createSession(null);
            McpSession session = manager.requireSession("req", sessionId);

            assertThat(session.getLastAccessedAt()).isNotNull();
            assertThat(session.getLastAccessedAt()).isAfterOrEqualTo(session.getCreatedAt());
        } finally {
            manager.shutdown();
        }
    }
}
