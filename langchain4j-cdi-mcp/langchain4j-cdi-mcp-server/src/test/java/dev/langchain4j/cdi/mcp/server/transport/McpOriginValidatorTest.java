package dev.langchain4j.cdi.mcp.server.transport;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

class McpOriginValidatorTest {

    @Test
    void absentOriginIsAllowed() {
        assertThat(McpOriginValidator.isAllowed(null, "example.com", List.of())).isTrue();
    }

    @Test
    void loopbackOriginOnLoopbackHostIsAllowedByDefault() {
        assertThat(McpOriginValidator.isAllowed("http://localhost:3000", "localhost:8080", List.of()))
                .isTrue();
        assertThat(McpOriginValidator.isAllowed("http://127.0.0.1", "localhost", List.of()))
                .isTrue();
        assertThat(McpOriginValidator.isAllowed("http://[::1]:5173", "[::1]:8080", List.of()))
                .isTrue();
        assertThat(McpOriginValidator.isAllowed("http://localhost:3000", "127.0.0.1:8080", List.of()))
                .isTrue();
    }

    @Test
    void loopbackComparisonIsCaseInsensitive() {
        assertThat(McpOriginValidator.isAllowed("http://LOCALHOST:3000", "LocalHost:8080", List.of()))
                .isTrue();
    }

    @Test
    void sameHostOriginIsRejectedByDefault() {
        assertThat(McpOriginValidator.isAllowed("https://mcp.example.com", "mcp.example.com", List.of()))
                .isFalse();
        assertThat(McpOriginValidator.isAllowed("http://mcp.example.com:8080", "mcp.example.com:8080", List.of()))
                .isFalse();
    }

    @Test
    void dnsRebindingIsRejectedByDefault() {
        assertThat(McpOriginValidator.isAllowed("http://evil.com:8080", "evil.com:8080", List.of()))
                .isFalse();
    }

    @Test
    void loopbackOriginWithNonLoopbackHostIsRejectedByDefault() {
        assertThat(McpOriginValidator.isAllowed("http://localhost:3000", "api:8080", List.of()))
                .isFalse();
        assertThat(McpOriginValidator.isAllowed("http://127.0.0.1", "mcp.example.com", List.of()))
                .isFalse();
        assertThat(McpOriginValidator.isAllowed("http://[::1]:5173", null, List.of()))
                .isFalse();
    }

    @Test
    void foreignOriginIsRejectedByDefault() {
        assertThat(McpOriginValidator.isAllowed("https://evil.example", "mcp.example.com", List.of()))
                .isFalse();
        assertThat(McpOriginValidator.isAllowed("null", "mcp.example.com", List.of()))
                .isFalse();
    }

    @Test
    void allowListIsExclusive() {
        List<String> allowed = List.of("https://app.example.com");

        assertThat(McpOriginValidator.isAllowed("https://app.example.com", "api", allowed))
                .isTrue();
        assertThat(McpOriginValidator.isAllowed("http://localhost", "api", allowed))
                .isFalse();
        assertThat(McpOriginValidator.isAllowed("https://any.example", "api", List.of("*")))
                .isTrue();
    }
}
