package dev.langchain4j.cdi.mcp.server.protocol;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.json.Json;
import jakarta.json.JsonObject;
import jakarta.json.JsonValue;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.mcpjava.server.tools.ToolResponse;

class McpJsonSerializerTest {

    @Test
    void structuredContentMapIsSerializedAsJsonObject() {
        JsonObject json = McpJsonSerializer.toolResponseToJson(ToolResponse.ofStructured(Map.of("temperature", 21)));

        assertThat(json.get("structuredContent").getValueType()).isEqualTo(JsonValue.ValueType.OBJECT);
        assertThat(json.getJsonObject("structuredContent").getInt("temperature"))
                .isEqualTo(21);
    }

    @Test
    void structuredContentJsonStringIsParsed() {
        JsonObject json = McpJsonSerializer.toolResponseToJson(ToolResponse.ofStructured("{\"city\":\"Paris\"}"));

        assertThat(json.getJsonObject("structuredContent").getString("city")).isEqualTo("Paris");
    }

    @Test
    void toJsonObjectConvertsRecordsAndSkipsNulls() {
        JsonObject json = McpJsonSerializer.toJsonObject(new McpImplementation("srv", null));

        assertThat(json.getString("name")).isEqualTo("srv");
        assertThat(json.containsKey("version")).isFalse();
    }

    @Test
    void toJsonValueKeepsExistingJsonValues() {
        JsonObject input = Json.createObjectBuilder().add("a", 1).build();

        assertThat(McpJsonSerializer.toJsonValue(input)).isSameAs(input);
    }
}
