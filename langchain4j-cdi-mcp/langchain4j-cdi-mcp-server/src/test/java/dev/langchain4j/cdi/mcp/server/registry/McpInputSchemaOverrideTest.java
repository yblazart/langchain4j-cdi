package dev.langchain4j.cdi.mcp.server.registry;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.langchain4j.cdi.mcp.server.error.McpInputSchemaDefinitionException;
import dev.langchain4j.cdi.mcp.server.fixtures.SchemaOverrideTool;
import dev.langchain4j.cdi.mcp.server.fixtures.WeatherTool;
import java.lang.reflect.Method;
import org.junit.jupiter.api.Test;

/**
 * {@code @McpInputSchema}: a hand-written schema published verbatim, and the three consistency rules enforced between
 * it and the method it describes, at registration time.
 */
class McpInputSchemaOverrideTest {

    private static Method method(String name, Class<?>... paramTypes) throws Exception {
        return SchemaOverrideTool.class.getMethod(name, paramTypes);
    }

    @Test
    void suppliedSchemaIsPublishedVerbatimInBothEras() throws Exception {
        McpToolDescriptor descriptor =
                McpToolDescriptor.fromMethod(SchemaOverrideTool.class, method("validOverride", String.class));

        String expected =
                "{\"type\":\"object\",\"properties\":{\"name\":{\"type\":\"string\"}},\"required\":[\"name\"],"
                        + "\"additionalProperties\":false}";
        assertThat(descriptor.getInputSchema().toString()).isEqualTo(expected);
        assertThat(descriptor.getModernInputSchema().toString()).isEqualTo(expected);
    }

    @Test
    void rejectsAnOverrideWhoseRootIsNotAnObjectSchema() throws Exception {
        Method m = method("nonObjectOverride", String.class);

        assertThatThrownBy(() -> McpToolDescriptor.fromMethod(SchemaOverrideTool.class, m))
                .isInstanceOf(McpInputSchemaDefinitionException.class)
                .hasMessageContaining("non_object_override")
                .hasMessageContaining("type")
                .hasMessageContaining("object");
    }

    @Test
    void rejectsARequiredPropertyWithNoBindableParameter() throws Exception {
        Method m = method("unbindableRequiredOverride", String.class);

        assertThatThrownBy(() -> McpToolDescriptor.fromMethod(SchemaOverrideTool.class, m))
                .isInstanceOf(McpInputSchemaDefinitionException.class)
                .hasMessageContaining("unbindable_required_override")
                .hasMessageContaining("missingParam");
    }

    @Test
    void rejectsCombinationWithMcpHeader() throws Exception {
        Method m = method("overrideWithHeader", String.class);

        assertThatThrownBy(() -> McpToolDescriptor.fromMethod(SchemaOverrideTool.class, m))
                .isInstanceOf(McpInputSchemaDefinitionException.class)
                .hasMessageContaining("override_with_header")
                .hasMessageContaining("McpHeader");
    }

    @Test
    void toolsWithoutTheOverrideKeepTheGeneratedSchemaUnaffected() throws Exception {
        Method m = WeatherTool.class.getMethod("getWeather", String.class, String.class);

        McpToolDescriptor descriptor = McpToolDescriptor.fromMethod(WeatherTool.class, m);

        assertThat(descriptor.getInputSchema().toString())
                .isEqualTo(
                        "{\"type\":\"object\",\"properties\":{\"city\":{\"type\":\"string\",\"description\":\"The city "
                                + "name\"},\"unit\":{\"type\":\"string\",\"description\":\"Unit: celsius or "
                                + "fahrenheit\"}},\"required\":[\"city\",\"unit\"]}");
    }
}
