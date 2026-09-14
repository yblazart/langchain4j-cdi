package dev.langchain4j.cdi.mcp.server.protocol;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.json.Json;
import jakarta.json.JsonObject;
import jakarta.json.JsonString;
import jakarta.json.JsonValue;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
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
    void invalidJsonStructuredContentStringIsKeptAsJsonString() {
        JsonObject json = McpJsonSerializer.toolResponseToJson(ToolResponse.ofStructured("{bad"));

        assertThat(json.get("structuredContent").getValueType()).isEqualTo(JsonValue.ValueType.STRING);
        assertThat(json.getString("structuredContent")).isEqualTo("{bad");
    }

    @Test
    void toJsonValueNeverParsesStrings() {
        JsonValue value = McpJsonSerializer.toJsonValue("{x");

        assertThat(value.getValueType()).isEqualTo(JsonValue.ValueType.STRING);
        assertThat(((JsonString) value).getString()).isEqualTo("{x");
        assertThat(McpJsonSerializer.toJsonValue("{\"a\":1}").getValueType()).isEqualTo(JsonValue.ValueType.STRING);
    }

    @Test
    void toJsonValueIsSafeForConcurrentUse() throws Exception {
        List<Callable<JsonObject>> tasks = new ArrayList<>();
        for (int i = 0; i < 64; i++) {
            int n = i;
            tasks.add(() -> McpJsonSerializer.toJsonObject(new McpImplementation("srv" + n, null)));
        }
        ExecutorService pool = Executors.newFixedThreadPool(8);
        try {
            List<Future<JsonObject>> results = pool.invokeAll(tasks);
            for (int i = 0; i < results.size(); i++) {
                assertThat(results.get(i).get().getString("name")).isEqualTo("srv" + i);
            }
        } finally {
            pool.shutdownNow();
        }
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
