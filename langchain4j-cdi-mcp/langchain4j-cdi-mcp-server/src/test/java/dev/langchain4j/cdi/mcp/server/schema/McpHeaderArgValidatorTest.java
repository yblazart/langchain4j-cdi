package dev.langchain4j.cdi.mcp.server.schema;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.langchain4j.cdi.mcp.server.error.McpHeaderArgDefinitionException;
import dev.langchain4j.cdi.mcp.server.fixtures.HeaderArgTool;
import dev.langchain4j.cdi.mcp.server.registry.McpToolDescriptor;
import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * SEP-2243 puts four constraints on {@code x-mcp-header}. A client MUST exclude a tool that violates any of them from
 * {@code tools/list}, so the tool would vanish silently: these must fail at registration, loudly.
 */
class McpHeaderArgValidatorTest {

    private static Method method(String name, Class<?>... params) throws Exception {
        return HeaderArgTool.class.getMethod(name, params);
    }

    @Test
    void shouldAcceptValidDesignations() throws Exception {
        Method valid = method("valid", String.class, int.class, boolean.class, String.class);

        assertThatCode(() -> McpToolDescriptor.fromMethod(HeaderArgTool.class, valid))
                .doesNotThrowAnyException();
    }

    @Test
    void shouldRejectEmptyValue() throws Exception {
        Method empty = method("empty", String.class);

        assertThatThrownBy(() -> McpToolDescriptor.fromMethod(HeaderArgTool.class, empty))
                .isInstanceOf(McpHeaderArgDefinitionException.class)
                .hasMessageContaining("empty_designation")
                .hasMessageContaining("tenant")
                .hasMessageContaining("MUST NOT be empty");
    }

    @Test
    void shouldRejectValueContainingSpace() throws Exception {
        Method space = method("space", String.class);

        assertThatThrownBy(() -> McpToolDescriptor.fromMethod(HeaderArgTool.class, space))
                .isInstanceOf(McpHeaderArgDefinitionException.class)
                .hasMessageContaining("space_designation")
                .hasMessageContaining("tenant")
                .hasMessageContaining("only ASCII characters, excluding space and ':'");
    }

    @Test
    void shouldRejectValueContainingColon() throws Exception {
        Method colon = method("colon", String.class);

        assertThatThrownBy(() -> McpToolDescriptor.fromMethod(HeaderArgTool.class, colon))
                .isInstanceOf(McpHeaderArgDefinitionException.class)
                .hasMessageContaining("colon_designation")
                .hasMessageContaining("only ASCII characters, excluding space and ':'");
    }

    @Test
    void shouldRejectNonAsciiValue() throws Exception {
        Method nonAscii = method("nonAscii", String.class);

        assertThatThrownBy(() -> McpToolDescriptor.fromMethod(HeaderArgTool.class, nonAscii))
                .isInstanceOf(McpHeaderArgDefinitionException.class)
                .hasMessageContaining("non_ascii_designation")
                .hasMessageContaining("only ASCII characters, excluding space and ':'");
    }

    @Test
    void shouldRejectCaseInsensitiveDuplicateWithinOneTool() throws Exception {
        Method duplicate = method("duplicate", String.class, String.class);

        assertThatThrownBy(() -> McpToolDescriptor.fromMethod(HeaderArgTool.class, duplicate))
                .isInstanceOf(McpHeaderArgDefinitionException.class)
                .hasMessageContaining("duplicate_designation")
                .hasMessageContaining("otherTenant")
                .hasMessageContaining("case-insensitively unique")
                .hasMessageContaining("tenant");
    }

    @Test
    void shouldRejectDoubleParameterBecauseNumberIsNotPermitted() throws Exception {
        Method doubleArg = method("doubleArg", double.class);

        assertThatThrownBy(() -> McpToolDescriptor.fromMethod(HeaderArgTool.class, doubleArg))
                .isInstanceOf(McpHeaderArgDefinitionException.class)
                .hasMessageContaining("double_designation")
                .hasMessageContaining("amount")
                .hasMessageContaining("integer, string, boolean")
                .hasMessageContaining("number");
    }

    @Test
    void shouldRejectFloatParameterBecauseNumberIsNotPermitted() throws Exception {
        Method floatArg = method("floatArg", float.class);

        assertThatThrownBy(() -> McpToolDescriptor.fromMethod(HeaderArgTool.class, floatArg))
                .isInstanceOf(McpHeaderArgDefinitionException.class)
                .hasMessageContaining("float_designation")
                .hasMessageContaining("integer, string, boolean");
    }

    @Test
    void shouldRejectBigDecimalParameter() throws Exception {
        Method bigDecimalArg = method("bigDecimalArg", BigDecimal.class);

        assertThatThrownBy(() -> McpToolDescriptor.fromMethod(HeaderArgTool.class, bigDecimalArg))
                .isInstanceOf(McpHeaderArgDefinitionException.class)
                .hasMessageContaining("big_decimal_designation")
                .hasMessageContaining("integer, string, boolean");
    }

    @Test
    void shouldRejectDesignationOnAFrameworkTypeParameter() throws Exception {
        Method frameworkArg = method("frameworkArg", dev.langchain4j.cdi.mcp.server.api.McpLog.class);

        assertThatThrownBy(() -> McpToolDescriptor.fromMethod(HeaderArgTool.class, frameworkArg))
                .isInstanceOf(McpHeaderArgDefinitionException.class)
                .hasMessageContaining("framework_type_designation")
                .hasMessageContaining("not part of the tool's input schema");
    }

    @Test
    void shouldRejectNonPrimitiveParameter() throws Exception {
        Method objectArg = method("objectArg", List.class);

        assertThatThrownBy(() -> McpToolDescriptor.fromMethod(HeaderArgTool.class, objectArg))
                .isInstanceOf(McpHeaderArgDefinitionException.class)
                .hasMessageContaining("object_designation")
                .hasMessageContaining("payload")
                .hasMessageContaining("integer, string, boolean");
    }
}
