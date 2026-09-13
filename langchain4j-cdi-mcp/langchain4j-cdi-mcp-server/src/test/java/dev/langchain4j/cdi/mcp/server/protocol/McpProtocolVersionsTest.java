package dev.langchain4j.cdi.mcp.server.protocol;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class McpProtocolVersionsTest {

    @Test
    void supportedListsModernThenLegacy() {
        assertThat(McpProtocolVersions.SUPPORTED).containsExactly("2026-07-28", "2025-03-26");
    }

    @Test
    void modernEraIsDateBased() {
        assertThat(McpProtocolVersions.isModernEra("2026-07-28")).isTrue();
        assertThat(McpProtocolVersions.isModernEra("2027-01-01")).isTrue();
        assertThat(McpProtocolVersions.isModernEra("2025-11-25")).isFalse();
        assertThat(McpProtocolVersions.isModernEra(null)).isFalse();
    }

    @Test
    void onlyImplementedModernVersionsAreSupported() {
        assertThat(McpProtocolVersions.isSupportedModern("2026-07-28")).isTrue();
        assertThat(McpProtocolVersions.isSupportedModern("2027-01-01")).isFalse();
        assertThat(McpProtocolVersions.isSupportedModern("2025-03-26")).isFalse();
    }

    @Test
    @SuppressWarnings("deprecation")
    void legacyConstantIsKept() {
        assertThat(McpProtocol.VERSION).isEqualTo("2025-03-26");
    }
}
