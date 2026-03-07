package dev.langchain4j.cdi.mcp.server.protocol;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class JsonRpcNotificationTest {

    @Test
    void shouldCreateToolsListChangedNotification() {
        JsonRpcNotification notification = JsonRpcNotification.toolsListChanged();

        assertThat(notification.getJsonrpc()).isEqualTo("2.0");
        assertThat(notification.getMethod()).isEqualTo("notifications/tools/list_changed");
    }

    @Test
    void shouldCreateCustomNotification() {
        JsonRpcNotification notification = new JsonRpcNotification("notifications/resources/list_changed");

        assertThat(notification.getMethod()).isEqualTo("notifications/resources/list_changed");
    }
}
