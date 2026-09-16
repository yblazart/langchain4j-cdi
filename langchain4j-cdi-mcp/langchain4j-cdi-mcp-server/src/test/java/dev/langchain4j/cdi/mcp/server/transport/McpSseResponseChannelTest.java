package dev.langchain4j.cdi.mcp.server.transport;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.json.Json;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;

class McpSseResponseChannelTest {

    @Test
    void writesSseMessageEvent() {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        McpSseResponseChannel channel = new McpSseResponseChannel(out, new AtomicBoolean());

        channel.send(Json.createObjectBuilder()
                .add("jsonrpc", "2.0")
                .add("method", "x")
                .build());
        channel.send(Map.of("a", 1));

        assertThat(out.toString(StandardCharsets.UTF_8))
                .isEqualTo("event: message\ndata: {\"jsonrpc\":\"2.0\",\"method\":\"x\"}\n\n"
                        + "event: message\ndata: {\"a\":1}\n\n");
    }

    @Test
    void writeFailureMarksRequestCancelled() {
        OutputStream broken = new OutputStream() {
            @Override
            public void write(int b) throws IOException {
                throw new IOException("closed");
            }
        };
        AtomicBoolean cancelled = new AtomicBoolean();
        McpSseResponseChannel channel = new McpSseResponseChannel(broken, cancelled);

        channel.send(Map.of("a", 1));

        assertThat(cancelled).isTrue();
        assertThat(channel.isOpen()).isFalse();
    }

    @Test
    void commentUsesSseCommentSyntax() {
        ByteArrayOutputStream out = new ByteArrayOutputStream();

        new McpSseResponseChannel(out, new AtomicBoolean()).sendComment("keepalive");

        assertThat(out.toString(StandardCharsets.UTF_8)).isEqualTo(": keepalive\n\n");
    }
}
