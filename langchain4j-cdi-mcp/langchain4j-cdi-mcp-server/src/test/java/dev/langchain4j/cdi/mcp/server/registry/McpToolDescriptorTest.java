package dev.langchain4j.cdi.mcp.server.registry;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.langchain4j.cdi.mcp.server.error.McpArgumentDefinitionException;
import dev.langchain4j.cdi.mcp.server.fixtures.WeatherTool;
import dev.langchain4j.cdi.mcp.server.protocol.McpToolModel;
import java.lang.reflect.Method;
import org.junit.jupiter.api.Test;
import org.mcpjava.server.tools.Tool;
import org.mcpjava.server.tools.ToolArg;

class McpToolDescriptorTest {

    @SuppressWarnings("unused")
    static class BadDefaultTool {
        @Tool(name = "bad_default", description = "A default that is not an integer")
        public String run(@ToolArg(name = "limit", defaultValue = "abc") Integer limit) {
            return "limit=" + limit;
        }
    }

    @Test
    void aDefaultValueThatDoesNotConvertFailsRegistration() throws Exception {
        Method method = BadDefaultTool.class.getMethod("run", Integer.class);

        assertThatThrownBy(() -> McpToolDescriptor.fromMethod(BadDefaultTool.class, method))
                .isInstanceOf(McpArgumentDefinitionException.class)
                .hasMessage(
                        "Tool 'bad_default', parameter 'limit': defaultValue \"abc\" cannot be converted to Integer");
    }

    @Test
    void shouldCreateFromMethod() throws Exception {
        Method method = WeatherTool.class.getMethod("getWeather", String.class, String.class);
        McpToolDescriptor descriptor = McpToolDescriptor.fromMethod(WeatherTool.class, method);

        assertThat(descriptor.getName()).isEqualTo("getWeather");
        assertThat(descriptor.getDescription()).isEqualTo("Get the current weather for a given city");
        assertThat(descriptor.getBeanType()).isEqualTo(WeatherTool.class);
        assertThat(descriptor.getMethod()).isEqualTo(method);
        assertThat(descriptor.getInputSchema()).isNotNull();
        assertThat(descriptor.getInputSchema().getString("type")).isEqualTo("object");
    }

    @Test
    void shouldConvertToWireFormat() throws Exception {
        Method method = WeatherTool.class.getMethod("getWeather", String.class, String.class);
        McpToolDescriptor descriptor = McpToolDescriptor.fromMethod(WeatherTool.class, method);

        McpToolModel wire = descriptor.toWireFormat();

        assertThat(wire.name()).isEqualTo("getWeather");
        assertThat(wire.description()).isEqualTo("Get the current weather for a given city");
        assertThat(wire.inputSchema()).isNotNull();
    }
}
