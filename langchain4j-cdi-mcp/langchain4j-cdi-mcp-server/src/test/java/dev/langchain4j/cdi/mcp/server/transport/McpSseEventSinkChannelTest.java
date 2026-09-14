package dev.langchain4j.cdi.mcp.server.transport;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.json.Json;
import java.io.IOException;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;

class McpSseEventSinkChannelTest {

    final FakeSseEventSink sink = new FakeSseEventSink();
    final AtomicBoolean cancelled = new AtomicBoolean();
    final McpSseEventSinkChannel channel = new McpSseEventSinkChannel(sink, new FakeSse(), cancelled);

    @Test
    void sendsMessagesAsNamedMessageEventsWithSingleLineJson() {
        channel.send(Json.createObjectBuilder()
                .add("jsonrpc", "2.0")
                .add("method", "x")
                .build());
        channel.send(Map.of("a", 1));

        assertThat(sink.rendered())
                .isEqualTo("event: message\ndata: {\"jsonrpc\":\"2.0\",\"method\":\"x\"}\n\n"
                        + "event: message\ndata: {\"a\":1}\n\n");
        assertThat(channel.isOpen()).isTrue();
    }

    @Test
    void sendsCommentsAsSseComments() {
        channel.sendComment("keepalive");

        assertThat(sink.rendered()).isEqualTo(": keepalive\n\n");
    }

    @Test
    void failedSendClosesChannelAndCancelsRequest() {
        sink.failSends(new IOException("client gone"));

        channel.send(Map.of("a", 1));

        assertThat(channel.isOpen()).isFalse();
        assertThat(cancelled).isTrue();
    }

    @Test
    void sendThrowingClosesChannel() {
        sink.throwOnSend(new IllegalStateException("Already closed"));

        channel.sendComment("keepalive");

        assertThat(channel.isOpen()).isFalse();
        assertThat(cancelled).isTrue();
    }

    @Test
    void sinkClosedByRuntimeIsReportedAndMessagesAreDropped() {
        sink.close();

        channel.send(Map.of("a", 1));

        assertThat(channel.isOpen()).isFalse();
        assertThat(sink.events()).isEmpty();
    }

    @Test
    void closeClosesTheSink() {
        channel.close();
        channel.close();

        assertThat(sink.isClosed()).isTrue();
        assertThat(channel.isOpen()).isFalse();
        assertThat(cancelled).isFalse();
    }
}
