package dev.langchain4j.cdi.mcp.server.schema;

import static org.assertj.core.api.Assertions.assertThat;

import dev.langchain4j.cdi.mcp.server.fixtures.HeaderArgTool;
import dev.langchain4j.cdi.mcp.server.fixtures.WeatherTool;
import jakarta.json.JsonObject;
import java.lang.reflect.Method;
import org.junit.jupiter.api.Test;

/** SEP-2243: the {@code x-mcp-header} keyword on designated argument properties. */
class JsonSchemaGeneratorHeaderArgTest {

    private static Method validMethod() throws Exception {
        return HeaderArgTool.class.getMethod("valid", String.class, int.class, boolean.class, String.class);
    }

    @Test
    void shouldEmitXMcpHeaderForDesignatedArgumentsInModernEra() throws Exception {
        JsonObject properties =
                JsonSchemaGenerator.fromMethod(validMethod(), true).getJsonObject("properties");

        assertThat(properties.getJsonObject("tenant").getString("x-mcp-header")).isEqualTo("X-Tenant-Id");
        assertThat(properties.getJsonObject("attempt").getString("x-mcp-header"))
                .isEqualTo("X-Attempt");
        assertThat(properties.getJsonObject("dryRun").getString("x-mcp-header")).isEqualTo("X-Dry-Run");
    }

    @Test
    void shouldNotEmitXMcpHeaderForUndesignatedArguments() throws Exception {
        JsonObject properties =
                JsonSchemaGenerator.fromMethod(validMethod(), true).getJsonObject("properties");

        assertThat(properties.getJsonObject("plain").containsKey("x-mcp-header"))
                .isFalse();
    }

    @Test
    void shouldKeepTypeAndDescriptionAlongsideTheDesignation() throws Exception {
        JsonObject tenant = JsonSchemaGenerator.fromMethod(validMethod(), true).getJsonObject("properties");

        assertThat(tenant.getJsonObject("tenant").getString("type")).isEqualTo("string");
        assertThat(tenant.getJsonObject("tenant").getString("description")).isEqualTo("The tenant");
        assertThat(tenant.getJsonObject("attempt").getString("type")).isEqualTo("integer");
        assertThat(tenant.getJsonObject("dryRun").getString("type")).isEqualTo("boolean");
    }

    @Test
    void shouldOmitXMcpHeaderInLegacyEra() throws Exception {
        JsonObject legacy = JsonSchemaGenerator.fromMethod(validMethod(), false);

        assertThat(legacy.toString()).doesNotContain("x-mcp-header");
    }

    @Test
    void shouldKeepTheLegacyEraByteIdenticalToTheSingleArgumentOverload() throws Exception {
        Method method = validMethod();

        assertThat(JsonSchemaGenerator.fromMethod(method, false).toString())
                .isEqualTo(JsonSchemaGenerator.fromMethod(method).toString());
    }

    @Test
    void shouldLeaveUndesignatedToolsByteIdenticalInBothEras() throws Exception {
        Method method = WeatherTool.class.getMethod("getWeather", String.class, String.class);

        String legacy = JsonSchemaGenerator.fromMethod(method, false).toString();
        String modern = JsonSchemaGenerator.fromMethod(method, true).toString();

        assertThat(legacy).isEqualTo(modern);
        assertThat(legacy)
                .isEqualTo(
                        "{\"type\":\"object\",\"properties\":{\"city\":{\"type\":\"string\",\"description\":\"The city name\"},"
                                + "\"unit\":{\"type\":\"string\",\"description\":\"Unit: celsius or fahrenheit\"}},"
                                + "\"required\":[\"city\",\"unit\"]}");
    }
}
