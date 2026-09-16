package dev.langchain4j.cdi.mcp.server.logging;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class McpRequestLogSinkTest {

    private final List<Object> sent = new ArrayList<>();

    @Test
    void withoutRequestedLevelNothingIsSent() {
        McpRequestLogSink sink = new McpRequestLogSink(sent::add, null);

        sink.log(McpLogLevel.emergency, "l", "m");

        assertThat(sent).isEmpty();
    }

    @Test
    void messagesBelowRequestedLevelAreDropped() {
        McpRequestLogSink sink = new McpRequestLogSink(sent::add, McpLogLevel.warning);

        sink.log(McpLogLevel.info, "l", "ignored");
        sink.log(McpLogLevel.error, "l", "kept");

        assertThat(sent)
                .containsExactly(Map.of(
                        "jsonrpc", "2.0",
                        "method", "notifications/message",
                        "params", Map.of("level", "error", "logger", "l", "data", "kept")));
        assertThat(sink.minimumLevel()).isEqualTo(McpLogLevel.warning);
    }
}
