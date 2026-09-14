package dev.langchain4j.cdi.mcp.server.transport;

import static org.assertj.core.api.Assertions.assertThat;

import dev.langchain4j.cdi.mcp.server.protocol.JsonRpcRequest;
import org.junit.jupiter.api.Test;

class McpJsonRpcParserTest {

    @Test
    void parsesRequestWithNumericIdAndProgressToken() {
        JsonRpcRequest request = McpJsonRpcParser.parseRequest(
                "{\"jsonrpc\":\"2.0\",\"id\":7,\"method\":\"tools/call\",\"params\":{\"_meta\":{\"progressToken\":\"p1\"}}}");

        assertThat(request.getId()).isEqualTo(7L);
        assertThat(request.getMethod()).isEqualTo("tools/call");
        assertThat(request.getProgressToken()).isEqualTo("p1");
    }

    @Test
    void parsesNotificationWithoutIdOrParams() {
        JsonRpcRequest request =
                McpJsonRpcParser.parseRequest("{\"jsonrpc\":\"2.0\",\"method\":\"notifications/initialized\"}");

        assertThat(request.getId()).isNull();
        assertThat(request.getParams()).isNull();
        assertThat(request.getProgressToken()).isNull();
    }

    @Test
    void detectsClientResponses() {
        assertThat(McpJsonRpcParser.isJsonRpcResponse("{\"jsonrpc\":\"2.0\",\"id\":\"server-1\",\"result\":{}}"))
                .isTrue();
        assertThat(McpJsonRpcParser.isJsonRpcResponse("{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"ping\"}"))
                .isFalse();
        assertThat(McpJsonRpcParser.isJsonRpcResponse("not json")).isFalse();
    }
}
