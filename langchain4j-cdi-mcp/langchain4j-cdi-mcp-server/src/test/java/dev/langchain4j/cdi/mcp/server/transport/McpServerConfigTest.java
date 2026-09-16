package dev.langchain4j.cdi.mcp.server.transport;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.Test;

class McpServerConfigTest {

    @Test
    void defaults() {
        McpServerConfig config = new McpServerConfig();

        assertThat(config.getAllowedOrigins()).isEmpty();
        assertThat(config.getMrtrMode()).isEqualTo(McpMrtrMode.REPLAY);
        assertThat(config.getRequestStateSecret()).isNull();
        assertThat(config.getRequestStateTtl()).isEqualTo(Duration.ofMinutes(10));
        assertThat(config.getContinuationTimeout()).isEqualTo(Duration.ofMinutes(5));
    }

    @Test
    void builderSetsNewProperties() {
        McpServerConfig config = McpServerConfig.builder()
                .serverName("srv")
                .allowedOrigins(List.of("https://app"))
                .mrtrMode(McpMrtrMode.CONTINUATION)
                .requestStateSecret("0123456789abcdef0123456789abcdef")
                .requestStateTtl(Duration.ofMinutes(2))
                .continuationTimeout(Duration.ofSeconds(30))
                .build();

        assertThat(config.getServerName()).isEqualTo("srv");
        assertThat(config.getAllowedOrigins()).containsExactly("https://app");
        assertThat(config.getMrtrMode()).isEqualTo(McpMrtrMode.CONTINUATION);
        assertThat(config.getRequestStateSecret()).hasSize(32);
        assertThat(config.getRequestStateTtl()).isEqualTo(Duration.ofMinutes(2));
        assertThat(config.getContinuationTimeout()).isEqualTo(Duration.ofSeconds(30));
    }
}
