package dev.langchain4j.cdi.mcp.server.error;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class McpErrorCodeTest {

    @Test
    void shouldHaveStandardJsonRpcCodes() {
        assertThat(McpErrorCode.PARSE_ERROR.getCode()).isEqualTo(-32700);
        assertThat(McpErrorCode.INVALID_REQUEST.getCode()).isEqualTo(-32600);
        assertThat(McpErrorCode.METHOD_NOT_FOUND.getCode()).isEqualTo(-32601);
        assertThat(McpErrorCode.INVALID_PARAMS.getCode()).isEqualTo(-32602);
        assertThat(McpErrorCode.INTERNAL_ERROR.getCode()).isEqualTo(-32603);
    }

    @Test
    void shouldHaveMcpSpecificCodes() {
        assertThat(McpErrorCode.SESSION_NOT_FOUND.getCode()).isEqualTo(-32001);
        assertThat(McpErrorCode.TOOL_NOT_FOUND.getCode()).isEqualTo(-32002);
    }

    @Test
    void modernProtocolErrorCodes() {
        assertThat(McpErrorCode.HEADER_MISMATCH.getCode()).isEqualTo(-32020);
        assertThat(McpErrorCode.MISSING_REQUIRED_CLIENT_CAPABILITY.getCode()).isEqualTo(-32021);
        assertThat(McpErrorCode.UNSUPPORTED_PROTOCOL_VERSION.getCode()).isEqualTo(-32022);
    }
}
