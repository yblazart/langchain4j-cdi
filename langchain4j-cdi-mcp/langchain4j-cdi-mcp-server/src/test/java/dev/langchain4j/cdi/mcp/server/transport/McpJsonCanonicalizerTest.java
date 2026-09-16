package dev.langchain4j.cdi.mcp.server.transport;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.json.Json;
import jakarta.json.JsonValue;
import org.junit.jupiter.api.Test;

class McpJsonCanonicalizerTest {

    @Test
    void keyOrderDoesNotChangeCanonicalForm() {
        JsonValue a = Json.createObjectBuilder()
                .add("city", "Paris")
                .add("opts", Json.createObjectBuilder().add("z", 1).add("a", true))
                .build();
        JsonValue b = Json.createObjectBuilder()
                .add("opts", Json.createObjectBuilder().add("a", true).add("z", 1))
                .add("city", "Paris")
                .build();

        assertThat(McpJsonCanonicalizer.canonicalize(a))
                .isEqualTo("{\"city\":\"Paris\",\"opts\":{\"a\":true,\"z\":1}}");
        assertThat(McpJsonCanonicalizer.digest(a))
                .isEqualTo(McpJsonCanonicalizer.digest(b))
                .hasSize(64);
    }

    @Test
    void differentValuesHaveDifferentDigests() {
        assertThat(McpJsonCanonicalizer.digest(
                        Json.createObjectBuilder().add("city", "Paris").build()))
                .isNotEqualTo(McpJsonCanonicalizer.digest(
                        Json.createObjectBuilder().add("city", "Lyon").build()));
    }

    @Test
    void nullIsCanonicalized() {
        assertThat(McpJsonCanonicalizer.canonicalize(null)).isEqualTo("null");
        assertThat(McpJsonCanonicalizer.canonicalize(
                        Json.createArrayBuilder().add(2).add("x").build()))
                .isEqualTo("[2,\"x\"]");
    }
}
