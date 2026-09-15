package dev.langchain4j.cdi.mcp.server.registry;

import static org.assertj.core.api.Assertions.assertThat;

import dev.langchain4j.cdi.mcp.server.fixtures.HeaderArgTool;
import java.lang.reflect.Method;
import org.junit.jupiter.api.Test;

/** The descriptor keeps one schema per era: SEP-2243 postdates the 2025-03-26 legacy era. */
class McpToolDescriptorHeaderArgTest {

    private static McpToolDescriptor validDescriptor() throws Exception {
        Method valid = HeaderArgTool.class.getMethod("valid", String.class, int.class, boolean.class, String.class);
        return McpToolDescriptor.fromMethod(HeaderArgTool.class, valid);
    }

    @Test
    void modernWireFormatCarriesTheDesignation() throws Exception {
        assertThat(validDescriptor().toWireFormat(true).inputSchema().toString())
                .contains("\"x-mcp-header\"");
    }

    @Test
    void legacyWireFormatDoesNot() throws Exception {
        assertThat(validDescriptor().toWireFormat(false).inputSchema().toString())
                .doesNotContain("x-mcp-header");
    }

    @Test
    void theNoArgWireFormatStaysLegacy() throws Exception {
        assertThat(validDescriptor().toWireFormat().inputSchema().toString()).doesNotContain("x-mcp-header");
    }
}
