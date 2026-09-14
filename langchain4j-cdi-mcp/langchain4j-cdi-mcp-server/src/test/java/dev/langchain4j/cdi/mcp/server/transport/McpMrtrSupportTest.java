package dev.langchain4j.cdi.mcp.server.transport;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import jakarta.json.Json;
import jakarta.json.JsonObject;
import jakarta.json.JsonValue;
import java.time.Duration;
import org.junit.jupiter.api.Test;

class McpMrtrSupportTest {

    @Test
    void randomSecretProducesWorkingCodec() {
        McpMrtrSupport support = new McpMrtrSupport(new McpServerConfigResolver(new McpServerConfig()));

        McpRequestStateCodec codec = support.codec();
        String token = codec.encode(new McpRequestStateCodec.State(
                "tools/call", "t", "d", codec.expiresAt(Duration.ofMinutes(1)), JsonValue.EMPTY_JSON_OBJECT, null));

        assertThat(codec.decode(1, token, "tools/call", "t", "d")).isNotNull();
        assertThat(support.codec()).isSameAs(codec);
        assertThat(support.mode()).isEqualTo(McpMrtrMode.REPLAY);
    }

    @Test
    void configuredSecretMustBeLongEnough() {
        McpServerConfig config =
                McpServerConfig.builder().requestStateSecret("short").build();

        assertThatThrownBy(() -> new McpMrtrSupport(new McpServerConfigResolver(config)).codec())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("requestStateSecret");
    }

    @Test
    void nameAndDigestDependOnMethod() {
        JsonObject toolParams = Json.createObjectBuilder()
                .add("name", "greet")
                .add("arguments", Json.createObjectBuilder().add("name", "Ada"))
                .build();
        JsonObject resourceParams =
                Json.createObjectBuilder().add("uri", "config://app").build();

        assertThat(McpMrtrSupport.mrtrName("tools/call", toolParams)).isEqualTo("greet");
        assertThat(McpMrtrSupport.mrtrName("resources/read", resourceParams)).isEqualTo("config://app");
        assertThat(McpMrtrSupport.argumentsDigest("tools/call", toolParams))
                .isEqualTo(McpJsonCanonicalizer.digest(toolParams.get("arguments")));
        assertThat(McpMrtrSupport.argumentsDigest("resources/read", resourceParams))
                .isEqualTo(McpJsonCanonicalizer.digest(resourceParams.get("uri")));
    }
}
