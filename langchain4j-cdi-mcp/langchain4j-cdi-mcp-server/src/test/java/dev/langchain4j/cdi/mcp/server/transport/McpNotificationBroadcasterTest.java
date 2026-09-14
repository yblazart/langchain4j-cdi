package dev.langchain4j.cdi.mcp.server.transport;

import static org.assertj.core.api.Assertions.assertThat;

import dev.langchain4j.cdi.mcp.server.protocol.JsonRpcNotification;
import java.io.ByteArrayOutputStream;
import org.junit.jupiter.api.Test;

class McpNotificationBroadcasterTest {

    @Test
    void shouldBroadcastToRegisteredChannelsWithSseFraming() {
        McpNotificationBroadcaster broadcaster = new McpNotificationBroadcaster();
        FakeSseEventSink sink = new FakeSseEventSink();

        broadcaster.registerStream("s1", new McpSseEventSinkChannel(sink, new FakeSse(), null));
        broadcaster.broadcast(JsonRpcNotification.toolsListChanged());

        assertThat(sink.rendered())
                .isEqualTo("event: message\ndata: {\"jsonrpc\":\"2.0\",\"method\":\"notifications/tools/list_changed\"}"
                        + "\n\n");
    }

    @Test
    void shouldRemoveAndCloseChannelsWhoseSendFails() {
        McpNotificationBroadcaster broadcaster = new McpNotificationBroadcaster();
        FakeSseEventSink sink = new FakeSseEventSink();
        McpSseEventSinkChannel channel = new McpSseEventSinkChannel(sink, new FakeSse(), null);
        broadcaster.registerStream("s1", channel);
        sink.failSends(new java.io.IOException("disconnected"));

        broadcaster.sendToSession("s1", JsonRpcNotification.toolsListChanged());

        assertThat(broadcaster.connectedStreamCount()).isZero();
        assertThat(channel.isOpen()).isFalse();
    }

    @Test
    void shouldOnlyUnregisterTheGivenChannel() {
        McpNotificationBroadcaster broadcaster = new McpNotificationBroadcaster();
        McpSseEventSinkChannel first = new McpSseEventSinkChannel(new FakeSseEventSink(), new FakeSse(), null);
        McpSseEventSinkChannel second = new McpSseEventSinkChannel(new FakeSseEventSink(), new FakeSse(), null);
        broadcaster.registerStream("s1", first);
        broadcaster.registerStream("s1", second);

        broadcaster.unregisterStream("s1", first);

        assertThat(broadcaster.connectedStreamCount()).isEqualTo(1);
    }

    @Test
    void shutdownClosesRegisteredChannels() {
        McpNotificationBroadcaster broadcaster = new McpNotificationBroadcaster();
        FakeSseEventSink sink = new FakeSseEventSink();
        broadcaster.registerStream("s1", new McpSseEventSinkChannel(sink, new FakeSse(), null));

        broadcaster.shutdown();

        assertThat(sink.isClosed()).isTrue();
        assertThat(broadcaster.connectedStreamCount()).isZero();
    }

    @Test
    void shouldBroadcastToRegisteredStreams() {
        McpNotificationBroadcaster broadcaster = new McpNotificationBroadcaster();
        ByteArrayOutputStream out1 = new ByteArrayOutputStream();
        ByteArrayOutputStream out2 = new ByteArrayOutputStream();

        broadcaster.registerStream("s1", out1);
        broadcaster.registerStream("s2", out2);

        broadcaster.broadcast(JsonRpcNotification.toolsListChanged());

        String output1 = out1.toString();
        String output2 = out2.toString();
        assertThat(output1).contains("event: message");
        assertThat(output1).contains("notifications/tools/list_changed");
        assertThat(output2).contains("event: message");
    }

    @Test
    void shouldUnregisterStream() {
        McpNotificationBroadcaster broadcaster = new McpNotificationBroadcaster();
        ByteArrayOutputStream out = new ByteArrayOutputStream();

        broadcaster.registerStream("s1", out);
        assertThat(broadcaster.connectedStreamCount()).isEqualTo(1);

        broadcaster.unregisterStream("s1");
        assertThat(broadcaster.connectedStreamCount()).isEqualTo(0);
    }

    @Test
    void shouldRemoveDisconnectedStreamsOnBroadcast() {
        McpNotificationBroadcaster broadcaster = new McpNotificationBroadcaster();

        // Create a stream that throws on write (simulates disconnection)
        java.io.OutputStream failingStream = new java.io.OutputStream() {
            @Override
            public void write(int b) throws java.io.IOException {
                throw new java.io.IOException("disconnected");
            }
        };

        broadcaster.registerStream("s1", failingStream);
        assertThat(broadcaster.connectedStreamCount()).isEqualTo(1);

        broadcaster.broadcast(JsonRpcNotification.toolsListChanged());

        assertThat(broadcaster.connectedStreamCount()).isEqualTo(0);
    }

    @Test
    void shouldHandleEmptyBroadcast() {
        McpNotificationBroadcaster broadcaster = new McpNotificationBroadcaster();
        // Should not throw
        broadcaster.broadcast(JsonRpcNotification.toolsListChanged());
        assertThat(broadcaster.connectedStreamCount()).isEqualTo(0);
    }

    @Test
    void broadcastAlsoReachesModernSubscriptions() throws Exception {
        McpNotificationBroadcaster broadcaster = new McpNotificationBroadcaster();
        McpSubscriptionRegistry registry = new McpSubscriptionRegistry();
        java.lang.reflect.Field field = McpNotificationBroadcaster.class.getDeclaredField("subscriptionRegistry");
        field.setAccessible(true);
        field.set(broadcaster, registry);
        java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
        registry.open(
                1L,
                McpNotificationFilter.from(jakarta.json.Json.createObjectBuilder()
                        .add("toolsListChanged", true)
                        .build()),
                new McpSseResponseChannel(out, new java.util.concurrent.atomic.AtomicBoolean()));

        assertThat(broadcaster.connectedStreamCount()).isEqualTo(1);
        broadcaster.broadcast(dev.langchain4j.cdi.mcp.server.protocol.JsonRpcNotification.toolsListChanged());

        assertThat(out.toString(java.nio.charset.StandardCharsets.UTF_8)).contains("notifications/tools/list_changed");
        registry.shutdown();
    }
}
