package dev.langchain4j.cdi.mcp.server.registry;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;

import dev.langchain4j.cdi.mcp.server.error.McpInvalidArgumentException;
import dev.langchain4j.cdi.mcp.server.fixtures.ArgumentBindingTool.Priority;
import dev.langchain4j.cdi.mcp.server.schema.JsonSchemaGenerator;
import jakarta.json.Json;
import jakarta.json.JsonObject;
import jakarta.json.JsonString;
import jakarta.json.JsonValue;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.List;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.OptionalInt;
import java.util.OptionalLong;
import org.junit.jupiter.api.Test;
import org.mcpjava.server.prompts.Prompt;
import org.mcpjava.server.prompts.PromptArg;
import org.mcpjava.server.tools.Tool;
import org.mcpjava.server.tools.ToolArg;

/**
 * The other not-required rule of the mcp-java {@code @ToolArg}/{@code @PromptArg} javadoc: a parameter of type
 * {@code Optional}, {@code OptionalInt}, {@code OptionalLong} or {@code OptionalDouble} is not required, is described
 * by its value type, and receives an empty optional when the argument is absent.
 */
class McpOptionalArgumentTest {

    @SuppressWarnings("unused")
    static class OptionalTool {
        @Tool(name = "search", description = "Search")
        public String search(
                @ToolArg(name = "query") String query,
                @ToolArg(name = "owner") Optional<String> owner,
                @ToolArg(name = "priority") Optional<Priority> priority,
                @ToolArg(name = "limit") OptionalInt limit,
                @ToolArg(name = "since") OptionalLong since,
                @ToolArg(name = "score") OptionalDouble score,
                @ToolArg(name = "page", defaultValue = "1") Optional<Integer> page,
                @ToolArg(name = "tags") Optional<List<String>> tags) {
            return query;
        }

        @Prompt(name = "brief", description = "Brief")
        public String brief(@PromptArg(name = "topic") Optional<String> topic) {
            return topic.orElse("none");
        }
    }

    private static Method search() throws Exception {
        return OptionalTool.class.getMethod(
                "search",
                String.class,
                Optional.class,
                Optional.class,
                OptionalInt.class,
                OptionalLong.class,
                OptionalDouble.class,
                Optional.class,
                Optional.class);
    }

    private static Parameter param(String name) throws Exception {
        for (Parameter p : search().getParameters()) {
            if (p.getAnnotation(ToolArg.class).name().equals(name)) {
                return p;
            }
        }
        throw new IllegalStateException(name);
    }

    private static Object bind(String name, JsonValue value) throws Exception {
        return McpArgumentBinder.bind(1, param(name), name, value, false);
    }

    @Test
    void optionalParametersAreNotRequiredAndAreDescribedByTheirValueType() throws Exception {
        JsonObject schema = JsonSchemaGenerator.fromMethod(search());
        JsonObject properties = schema.getJsonObject("properties");

        assertThat(schema.getJsonArray("required").getValuesAs(JsonString::getString))
                .containsExactly("query");
        assertThat(properties.getJsonObject("owner").getString("type")).isEqualTo("string");
        assertThat(properties.getJsonObject("priority").getJsonArray("enum").getValuesAs(JsonString::getString))
                .containsExactly("LOW", "MEDIUM", "HIGH");
        assertThat(properties.getJsonObject("limit").getString("type")).isEqualTo("integer");
        assertThat(properties.getJsonObject("since").getString("type")).isEqualTo("integer");
        assertThat(properties.getJsonObject("score").getString("type")).isEqualTo("number");
        assertThat(properties.getJsonObject("page").get("default")).isEqualTo(Json.createValue(1L));
        assertThat(properties.getJsonObject("tags").getString("type")).isEqualTo("array");
    }

    @Test
    void anOptionalPromptArgumentIsNotRequired() throws Exception {
        Method brief = OptionalTool.class.getMethod("brief", Optional.class);

        assertThat(McpPromptDescriptor.fromMethod(OptionalTool.class, brief).getArguments())
                .extracting(McpPromptDescriptor.PromptArgument::name, McpPromptDescriptor.PromptArgument::required)
                .containsExactly(tuple("topic", false));
    }

    @Test
    void anAbsentOptionalArgumentIsEmptyNotNull() throws Exception {
        assertThat(bind("owner", null)).isEqualTo(Optional.empty());
        assertThat(bind("limit", JsonValue.NULL)).isEqualTo(OptionalInt.empty());
        assertThat(bind("since", null)).isEqualTo(OptionalLong.empty());
        assertThat(bind("score", null)).isEqualTo(OptionalDouble.empty());
        assertThat(bind("page", null)).isEqualTo(Optional.of(1));
    }

    @Test
    void aPresentOptionalArgumentIsConvertedAndWrapped() throws Exception {
        assertThat(bind("owner", Json.createValue("ada"))).isEqualTo(Optional.of("ada"));
        assertThat(bind("priority", Json.createValue("HIGH"))).isEqualTo(Optional.of(Priority.HIGH));
        assertThat(bind("limit", Json.createValue(5))).isEqualTo(OptionalInt.of(5));
        assertThat(bind("since", Json.createValue(7L))).isEqualTo(OptionalLong.of(7L));
        assertThat(bind("score", Json.createValue(0.5))).isEqualTo(OptionalDouble.of(0.5));
    }

    @Test
    void aWronglyTypedOptionalArgumentIsInvalid() {
        assertThatThrownBy(() -> bind("limit", Json.createValue("5")))
                .isInstanceOf(McpInvalidArgumentException.class)
                .hasMessage("Invalid argument 'limit': expected integer, got string \"5\"");
    }
}
