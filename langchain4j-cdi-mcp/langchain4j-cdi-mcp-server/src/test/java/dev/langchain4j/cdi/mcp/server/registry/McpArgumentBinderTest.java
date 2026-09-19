package dev.langchain4j.cdi.mcp.server.registry;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.langchain4j.cdi.mcp.server.error.McpInvalidArgumentException;
import dev.langchain4j.cdi.mcp.server.fixtures.ArgumentBindingTool.Priority;
import jakarta.json.Json;
import jakarta.json.JsonValue;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;
import org.mcpjava.server.tools.ToolArg;

/** langchain4j-cdi#298: how one JSON argument becomes a Java argument, and how a wrong one is rejected. */
class McpArgumentBinderTest {

    @SuppressWarnings("unused")
    static class Signatures {
        public void typed(
                @ToolArg(name = "count") int count,
                @ToolArg(name = "boxed") Integer boxed,
                @ToolArg(name = "id") long id,
                @ToolArg(name = "ratio") double ratio,
                @ToolArg(name = "factor") float factor,
                @ToolArg(name = "small") short small,
                @ToolArg(name = "tiny") byte tiny,
                @ToolArg(name = "initial") char initial,
                @ToolArg(name = "flag") boolean flag,
                @ToolArg(name = "text") String text,
                @ToolArg(name = "priority") Priority priority) {}

        public void defaults(
                @ToolArg(name = "limit", defaultValue = "20") Integer limit,
                @ToolArg(name = "pageSize", defaultValue = "10") int pageSize,
                @ToolArg(name = "includeDone", defaultValue = "true") boolean includeDone,
                @ToolArg(name = "priority", defaultValue = "HIGH") Priority priority,
                @ToolArg(name = "label", defaultValue = "none") String label) {}
    }

    private static Parameter param(String method, String name) {
        for (Method m : Signatures.class.getDeclaredMethods()) {
            if (m.getName().equals(method)) {
                for (Parameter p : m.getParameters()) {
                    if (p.getAnnotation(ToolArg.class).name().equals(name)) {
                        return p;
                    }
                }
            }
        }
        throw new IllegalStateException(method + "." + name);
    }

    private static Object bindTyped(String name, JsonValue value) {
        return McpArgumentBinder.bind(7, param("typed", name), name, value, false);
    }

    private static Object bindText(String name, JsonValue value) {
        return McpArgumentBinder.bind(7, param("typed", name), name, value, true);
    }

    private static McpInvalidArgumentException rejected(String name, JsonValue value) {
        try {
            bindTyped(name, value);
        } catch (McpInvalidArgumentException e) {
            return e;
        }
        throw new AssertionError("'" + name + "' should have been rejected: " + value);
    }

    // --- Part 1: defaults ---

    @Test
    void anAbsentArgumentWithoutDefaultIsNullOrZero() {
        assertThat(bindTyped("count", null)).isEqualTo(0);
        assertThat(bindTyped("boxed", null)).isNull();
        assertThat(bindTyped("small", null)).isEqualTo((short) 0);
        assertThat(bindTyped("initial", null)).isEqualTo('\0');
        assertThat(bindTyped("flag", null)).isEqualTo(false);
        assertThat(bindTyped("priority", JsonValue.NULL)).isNull();
    }

    @Test
    void anAbsentOrNullArgumentTakesItsDefaultValue() {
        assertThat(McpArgumentBinder.bind(7, param("defaults", "limit"), "limit", null, false))
                .isEqualTo(20);
        assertThat(McpArgumentBinder.bind(7, param("defaults", "pageSize"), "pageSize", JsonValue.NULL, false))
                .isEqualTo(10);
        assertThat(McpArgumentBinder.bind(7, param("defaults", "includeDone"), "includeDone", null, false))
                .isEqualTo(true);
        assertThat(McpArgumentBinder.bind(7, param("defaults", "priority"), "priority", null, false))
                .isEqualTo(Priority.HIGH);
        assertThat(McpArgumentBinder.bind(7, param("defaults", "label"), "label", null, true))
                .isEqualTo("none");
    }

    @Test
    void aSuppliedArgumentWinsOverItsDefault() {
        assertThat(McpArgumentBinder.bind(7, param("defaults", "limit"), "limit", Json.createValue(5), false))
                .isEqualTo(5);
    }

    // --- Part 2: enums ---

    @Test
    void anEnumBindsFromTheAdvertisedConstantName() {
        assertThat(bindTyped("priority", Json.createValue("HIGH"))).isEqualTo(Priority.HIGH);
    }

    @Test
    void anEnumValueOutsideTheAdvertisedOnesIsInvalid() {
        assertThat(rejected("priority", Json.createValue("high")))
                .hasMessage("Invalid argument 'priority': expected one of [LOW, MEDIUM, HIGH], got string \"high\"");
        assertThat(rejected("priority", Json.createValue(2)))
                .hasMessage("Invalid argument 'priority': expected one of [LOW, MEDIUM, HIGH], got number 2");
    }

    // --- Part 3: strict JSON types ---

    @Test
    void numbersBindWhenTheyFitTheParameterType() {
        assertThat(bindTyped("count", Json.createValue(5))).isEqualTo(5);
        assertThat(bindTyped("count", Json.createValue(new BigDecimal("5.0")))).isEqualTo(5);
        assertThat(bindTyped("boxed", Json.createValue(-3))).isEqualTo(-3);
        assertThat(bindTyped("id", Json.createValue(3_000_000_000L))).isEqualTo(3_000_000_000L);
        assertThat(bindTyped("ratio", Json.createValue(2.5))).isEqualTo(2.5);
        assertThat(bindTyped("factor", Json.createValue(2.5))).isEqualTo(2.5f);
        assertThat(bindTyped("small", Json.createValue(12))).isEqualTo((short) 12);
        assertThat(bindTyped("tiny", Json.createValue(7))).isEqualTo((byte) 7);
        assertThat(bindTyped("initial", Json.createValue("x"))).isEqualTo('x');
        assertThat(bindTyped("flag", JsonValue.TRUE)).isEqualTo(true);
        assertThat(bindTyped("flag", JsonValue.FALSE)).isEqualTo(false);
    }

    @Test
    void aNumberSentAsAStringIsInvalid() {
        McpInvalidArgumentException e = rejected("boxed", Json.createValue("5"));

        assertThat(e).hasMessage("Invalid argument 'boxed': expected integer, got string \"5\"");
        assertThat(e.getArgumentName()).isEqualTo("boxed");
        assertThat(e.getErrorCode().getCode()).isEqualTo(-32602);
        assertThat(e.getRequestId()).isEqualTo(7);
        assertThat(rejected("id", Json.createValue("7")))
                .hasMessage("Invalid argument 'id': expected integer, got string \"7\"");
        assertThat(rejected("ratio", Json.createValue("2.5")))
                .hasMessage("Invalid argument 'ratio': expected number, got string \"2.5\"");
    }

    @Test
    void aFractionalOrOutOfRangeIntegerIsInvalidNotTruncated() {
        assertThat(rejected("count", Json.createValue(5.5)))
                .hasMessage("Invalid argument 'count': expected integer, got number 5.5");
        assertThat(rejected("count", Json.createValue(3_000_000_000L)))
                .hasMessage("Invalid argument 'count': expected integer, got number 3000000000");
        assertThat(rejected("tiny", Json.createValue(300)))
                .hasMessage("Invalid argument 'tiny': expected integer, got number 300");
    }

    @Test
    void aBooleanSentAsAStringOrANumberIsInvalid() {
        assertThat(rejected("flag", Json.createValue("true")))
                .hasMessage("Invalid argument 'flag': expected boolean, got string \"true\"");
        assertThat(rejected("flag", Json.createValue(1)))
                .hasMessage("Invalid argument 'flag': expected boolean, got number 1");
    }

    @Test
    void aCharNeedsASingleCharacterString() {
        assertThat(rejected("initial", Json.createValue("xy")))
                .hasMessage("Invalid argument 'initial': expected a single-character string, got string \"xy\"");
    }

    @Test
    void aStringParameterStaysLenient() {
        assertThat(bindTyped("text", Json.createValue("abc"))).isEqualTo("abc");
        assertThat(bindTyped("text", Json.createValue(5))).isEqualTo("5");
        assertThat(bindTyped("text", JsonValue.TRUE)).isEqualTo("true");
    }

    @Test
    void theMessageNeverEchoesMoreThanFortyCharactersNorNamesAJavaType() {
        McpInvalidArgumentException e = rejected("count", Json.createValue("x".repeat(500)));

        assertThat(e.getMessage())
                .isEqualTo("Invalid argument 'count': expected integer, got string \"" + "x".repeat(40) + "...\"")
                .doesNotContain("java.")
                .doesNotContain("jakarta.")
                .doesNotContain("Exception");
        assertThat(rejected("count", Json.createObjectBuilder().add("a", 1).build()))
                .hasMessage("Invalid argument 'count': expected integer, got object");
        assertThat(rejected("count", Json.createArrayBuilder().add(1).build()))
                .hasMessage("Invalid argument 'count': expected integer, got array");
    }

    // --- prompt arguments and resource-template variables are strings on the wire ---

    @Test
    void aTextualArgumentIsParsedIntoTheParameterType() {
        assertThat(bindText("count", Json.createValue("6"))).isEqualTo(6);
        assertThat(bindText("flag", Json.createValue("true"))).isEqualTo(true);
        assertThat(bindText("priority", Json.createValue("LOW"))).isEqualTo(Priority.LOW);
        // a client that sends the native JSON type is still accepted
        assertThat(bindText("count", Json.createValue(6))).isEqualTo(6);
    }

    @Test
    void aTextualArgumentThatDoesNotParseIsInvalid() {
        assertThatThrownBy(() -> bindText("count", Json.createValue("six")))
                .isInstanceOf(McpInvalidArgumentException.class)
                .hasMessage("Invalid argument 'count': expected integer, got string \"six\"");
    }
}
