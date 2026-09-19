package dev.langchain4j.cdi.mcp.server.schema;

import static org.assertj.core.api.Assertions.assertThat;

import dev.langchain4j.cdi.mcp.server.fixtures.ArgumentBindingTool;
import dev.langchain4j.cdi.mcp.server.fixtures.ArgumentBindingTool.Priority;
import jakarta.json.Json;
import jakarta.json.JsonObject;
import jakarta.json.JsonString;
import jakarta.json.JsonValue;
import java.lang.reflect.Method;
import org.junit.jupiter.api.Test;

/**
 * langchain4j-cdi#298, schema half: an argument with a {@code defaultValue} is not required and advertises its default,
 * and enum values are advertised by {@link Enum#name()}.
 */
class JsonSchemaGeneratorDefaultsTest {

    private static JsonObject listTasksSchema(boolean modern) throws Exception {
        Method method = ArgumentBindingTool.class.getMethod(
                "listTasks", Integer.class, int.class, boolean.class, Priority.class);
        return JsonSchemaGenerator.fromMethod(method, modern);
    }

    @Test
    void argumentsWithADefaultAreNotRequiredInEitherEra() throws Exception {
        assertThat(listTasksSchema(false).getJsonArray("required")).isEmpty();
        assertThat(listTasksSchema(true).getJsonArray("required")).isEmpty();
    }

    @Test
    void theDefaultIsAdvertisedWithItsJsonType() throws Exception {
        JsonObject properties = listTasksSchema(false).getJsonObject("properties");

        assertThat(properties.getJsonObject("limit").get("default")).isEqualTo(Json.createValue(20L));
        assertThat(properties.getJsonObject("pageSize").get("default")).isEqualTo(Json.createValue(10L));
        assertThat(properties.getJsonObject("includeDone").get("default")).isEqualTo(JsonValue.TRUE);
        assertThat(properties.getJsonObject("priority").get("default")).isEqualTo(Json.createValue("MEDIUM"));
    }

    @Test
    void enumValuesAreTheConstantNamesNotToString() throws Exception {
        Method method = ArgumentBindingTool.class.getMethod("byPriority", Priority.class);
        JsonObject schema = JsonSchemaGenerator.fromMethod(method);

        JsonObject priority = schema.getJsonObject("properties").getJsonObject("priority");
        assertThat(priority.getString("type")).isEqualTo("string");
        assertThat(priority.getJsonArray("enum").getValuesAs(JsonString::getString))
                .containsExactly("LOW", "MEDIUM", "HIGH");
        assertThat(priority).doesNotContainKey("default");
        assertThat(schema.getJsonArray("required").getValuesAs(JsonString::getString))
                .containsExactly("priority");
    }
}
