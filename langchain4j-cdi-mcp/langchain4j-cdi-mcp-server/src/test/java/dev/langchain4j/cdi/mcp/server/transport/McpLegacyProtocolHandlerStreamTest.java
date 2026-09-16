package dev.langchain4j.cdi.mcp.server.transport;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;

import dev.langchain4j.cdi.mcp.server.protocol.JsonRpcNotification;
import java.time.Duration;
import org.junit.jupiter.api.Test;

class McpLegacyProtocolHandlerStreamTest {

    @Test
    void openStreamReturnsAndDeliversSessionNotificationsUntilTheBroadcasterShutsDown() {
        McpLegacyProtocolHandler handler = new McpLegacyProtocolHandler();
        McpNotificationBroadcaster broadcaster = new McpNotificationBroadcaster();
        handler.broadcaster = broadcaster;
        FakeSseEventSink sink = new FakeSseEventSink();
        McpSseEventSinkChannel channel = new McpSseEventSinkChannel(sink, new FakeSse(), null);

        // the request thread must not be held for the lifetime of the stream
        assertTimeoutPreemptively(Duration.ofSeconds(5), () -> handler.openStream("s1", channel));
        broadcaster.sendToSession("s1", JsonRpcNotification.toolsListChanged());

        assertThat(sink.rendered())
                .startsWith(": stream opened\n\n")
                .contains("event: message\ndata: ")
                .contains("notifications/tools/list_changed");
        assertThat(sink.isClosed()).isFalse();

        broadcaster.shutdown();

        assertThat(broadcaster.connectedStreamCount()).isZero();
        assertThat(sink.isClosed()).isTrue();
    }

    @Test
    void openStreamOnAnAlreadyClosedClientIsNotRegistered() {
        McpLegacyProtocolHandler handler = new McpLegacyProtocolHandler();
        McpNotificationBroadcaster broadcaster = new McpNotificationBroadcaster();
        handler.broadcaster = broadcaster;
        FakeSseEventSink sink = new FakeSseEventSink();
        sink.failSends(new java.io.IOException("client gone"));

        handler.openStream("s1", new McpSseEventSinkChannel(sink, new FakeSse(), null));

        assertThat(broadcaster.connectedStreamCount()).isZero();
        assertThat(sink.isClosed()).isTrue();
    }
}
