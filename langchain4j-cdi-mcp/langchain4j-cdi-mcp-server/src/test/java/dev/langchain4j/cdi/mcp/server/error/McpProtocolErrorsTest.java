package dev.langchain4j.cdi.mcp.server.error;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class McpProtocolErrorsTest {

    @Test
    void unsupportedProtocolVersionCarriesSupportedList() {
        McpException e = McpProtocolErrors.unsupportedProtocolVersion(1, "1900-01-01");

        assertThat(e.getErrorCode().getCode()).isEqualTo(-32022);
        assertThat(e.getHttpStatus()).isEqualTo(400);
        assertThat(e.getData())
                .isEqualTo(Map.of("supported", List.of("2026-07-28", "2025-03-26"), "requested", "1900-01-01"));
    }

    @Test
    void headerMismatchIs400() {
        McpException e = McpProtocolErrors.headerMismatch(2, "Mcp-Method");

        assertThat(e.getErrorCode().getCode()).isEqualTo(-32020);
        assertThat(e.getHttpStatus()).isEqualTo(400);
        assertThat(e.getMessage()).contains("Mcp-Method");
    }

    @Test
    void missingClientCapabilityListsRequiredCapability() {
        McpException e = McpProtocolErrors.missingClientCapability(3, "elicitation");

        assertThat(e.getErrorCode().getCode()).isEqualTo(-32021);
        assertThat(e.getData()).isEqualTo(Map.of("requiredCapabilities", Map.of("elicitation", Map.of())));
    }

    @Test
    void methodNotFoundIs404() {
        McpException e = McpProtocolErrors.methodNotFound(4, "ping");

        assertThat(e.getErrorCode().getCode()).isEqualTo(-32601);
        assertThat(e.getHttpStatus()).isEqualTo(404);
    }

    @Test
    void legacyConstructorDefaultsTo200WithoutData() {
        McpException e = new McpException(5, McpErrorCode.INTERNAL_ERROR, "boom");

        assertThat(e.getHttpStatus()).isEqualTo(200);
        assertThat(e.getData()).isNull();
    }
}
