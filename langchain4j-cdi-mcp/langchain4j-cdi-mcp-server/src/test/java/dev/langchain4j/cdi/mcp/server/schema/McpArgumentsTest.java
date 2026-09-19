package dev.langchain4j.cdi.mcp.server.schema;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.langchain4j.cdi.mcp.server.error.McpArgumentDefinitionException;
import dev.langchain4j.cdi.mcp.server.fixtures.ArgumentBindingTool;
import dev.langchain4j.cdi.mcp.server.fixtures.ArgumentBindingTool.Priority;
import jakarta.json.Json;
import jakarta.json.JsonValue;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mcpjava.server.prompts.PromptArg;
import org.mcpjava.server.tools.ToolArg;

class McpArgumentsTest {

    @SuppressWarnings("unused")
    static class Signatures {
        public void required(
                @ToolArg(name = "plain") String plain,
                @ToolArg(name = "optional", required = false) String optional,
                @ToolArg(name = "defaulted", defaultValue = "x") String defaulted,
                @ToolArg(name = "both", required = true, defaultValue = "x") String both,
                String bare,
                @PromptArg(name = "promptDefaulted", defaultValue = "y") String promptDefaulted) {}

        public void badInteger(@ToolArg(name = "limit", defaultValue = "abc") Integer limit) {}

        public void badEnum(@ToolArg(name = "priority", defaultValue = "URGENT") Priority priority) {}

        public void badBoolean(@ToolArg(name = "flag", defaultValue = "yes") boolean flag) {}

        public void unsupported(@ToolArg(name = "tags", defaultValue = "a") List<String> tags) {}
    }

    private static Method method(String name) {
        for (Method m : Signatures.class.getDeclaredMethods()) {
            if (m.getName().equals(name)) {
                return m;
            }
        }
        throw new IllegalStateException("no method " + name);
    }

    private static Parameter param(String method, int index) {
        return method(method).getParameters()[index];
    }

    @Test
    void anArgumentIsRequiredUnlessItOptsOutOrHasADefault() {
        assertThat(McpArguments.isRequired(param("required", 0))).isTrue();
        assertThat(McpArguments.isRequired(param("required", 1))).isFalse();
        assertThat(McpArguments.isRequired(param("required", 2))).isFalse();
        // "Setting a default value causes the parameter to not be required, regardless of the value of required()"
        assertThat(McpArguments.isRequired(param("required", 3))).isFalse();
        assertThat(McpArguments.isRequired(param("required", 4))).isTrue();
        assertThat(McpArguments.isRequired(param("required", 5))).isFalse();
    }

    @Test
    void defaultValueIsNullWhenTheAnnotationLeavesItEmpty() {
        assertThat(McpArguments.defaultValue(param("required", 0))).isNull();
        assertThat(McpArguments.defaultValue(param("required", 2))).isEqualTo("x");
        assertThat(McpArguments.defaultValue(param("required", 4))).isNull();
        assertThat(McpArguments.defaultValue(param("required", 5))).isEqualTo("y");
    }

    @Test
    void enumNamesUseNameNotToString() {
        assertThat(McpArguments.enumNames(Priority.class)).containsExactly("LOW", "MEDIUM", "HIGH");
    }

    @Test
    void parseConvertsEveryTypeWithATextualForm() {
        assertThat(McpArguments.parse(String.class, "abc")).isEqualTo("abc");
        assertThat(McpArguments.parse(int.class, "20")).isEqualTo(20);
        assertThat(McpArguments.parse(Integer.class, "-3")).isEqualTo(-3);
        assertThat(McpArguments.parse(long.class, "7")).isEqualTo(7L);
        assertThat(McpArguments.parse(short.class, "12")).isEqualTo((short) 12);
        assertThat(McpArguments.parse(Byte.class, "1")).isEqualTo((byte) 1);
        assertThat(McpArguments.parse(double.class, "2.5")).isEqualTo(2.5);
        assertThat(McpArguments.parse(Float.class, "2.5")).isEqualTo(2.5f);
        assertThat(McpArguments.parse(boolean.class, "TRUE")).isEqualTo(true);
        assertThat(McpArguments.parse(Boolean.class, "false")).isEqualTo(false);
        assertThat(McpArguments.parse(char.class, "x")).isEqualTo('x');
        assertThat(McpArguments.parse(Priority.class, "HIGH")).isEqualTo(Priority.HIGH);
    }

    @Test
    void parseRejectsTextThatDoesNotDenoteAValue() {
        assertThatThrownBy(() -> McpArguments.parse(int.class, "5.5")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> McpArguments.parse(boolean.class, "yes")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> McpArguments.parse(char.class, "xy")).isInstanceOf(IllegalArgumentException.class);
        // the enum matches on name(), so the lowercase toString() form is not accepted
        assertThatThrownBy(() -> McpArguments.parse(Priority.class, "high"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> McpArguments.parse(List.class, "a")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void toJsonWritesTheSchemaDefault() {
        assertThat(McpArguments.toJson(20)).isEqualTo(Json.createValue(20L));
        assertThat(McpArguments.toJson(2.5)).isEqualTo(Json.createValue(2.5));
        // a float default is written from its decimal text, not widened to 0.10000000149011612
        assertThat(McpArguments.toJson(0.1f)).isEqualTo(Json.createValue(new BigDecimal("0.1")));
        assertThat(McpArguments.toJson(true)).isEqualTo(JsonValue.TRUE);
        assertThat(McpArguments.toJson(Priority.HIGH)).isEqualTo(Json.createValue("HIGH"));
        assertThat(McpArguments.toJson('x')).isEqualTo(Json.createValue("x"));
        assertThat(McpArguments.toJson("abc")).isEqualTo(Json.createValue("abc"));
    }

    @Test
    void validDefaultsPassRegistration() throws Exception {
        Method listTasks = ArgumentBindingTool.class.getMethod(
                "listTasks", Integer.class, int.class, boolean.class, Priority.class);

        assertThatCode(() -> McpArguments.validateDefaults("Tool 'list_tasks'", listTasks))
                .doesNotThrowAnyException();
    }

    @Test
    void aDefaultThatDoesNotConvertFailsRegistration() {
        assertThatThrownBy(() -> McpArguments.validateDefaults("Tool 'bad'", method("badInteger")))
                .isInstanceOf(McpArgumentDefinitionException.class)
                .hasMessage("Tool 'bad', parameter 'limit': defaultValue \"abc\" cannot be converted to Integer");
        assertThatThrownBy(() -> McpArguments.validateDefaults("Tool 'bad'", method("badEnum")))
                .isInstanceOf(McpArgumentDefinitionException.class)
                .hasMessage("Tool 'bad', parameter 'priority': defaultValue \"URGENT\" cannot be converted to "
                        + "Priority [LOW, MEDIUM, HIGH]");
        assertThatThrownBy(() -> McpArguments.validateDefaults("Prompt 'bad'", method("badBoolean")))
                .isInstanceOf(McpArgumentDefinitionException.class)
                .hasMessageContaining("Prompt 'bad', parameter 'flag'");
    }

    @Test
    void aDefaultOnATypeWithoutTextualFormFailsRegistration() {
        assertThatThrownBy(() -> McpArguments.validateDefaults("Tool 'bad'", method("unsupported")))
                .isInstanceOf(McpArgumentDefinitionException.class)
                .hasMessageContaining("parameter 'tags'")
                .hasMessageContaining("is not supported on a parameter of type List");
    }
}
