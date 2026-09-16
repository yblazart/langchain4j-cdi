package dev.langchain4j.cdi.mcp.server.transport;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import dev.langchain4j.cdi.mcp.server.fixtures.AnnotatedTool;
import dev.langchain4j.cdi.mcp.server.registry.McpBeanInvoker;
import dev.langchain4j.cdi.mcp.server.registry.McpPromptRegistry;
import dev.langchain4j.cdi.mcp.server.registry.McpResourceRegistry;
import dev.langchain4j.cdi.mcp.server.registry.McpToolDescriptor;
import dev.langchain4j.cdi.mcp.server.registry.McpToolInvoker;
import dev.langchain4j.cdi.mcp.server.registry.McpToolRegistry;
import jakarta.json.JsonObject;
import java.lang.reflect.Method;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * {@code @Tool.title()} / {@code @Tool.annotations()} on the wire. Both are defined by the 2026-07-28 schema; only
 * {@code annotations} is defined by 2025-03-26, and the legacy listing must stay byte-identical regardless, per the
 * upstream PR's promise.
 */
class McpToolAnnotationsWireTest {

    private McpToolRegistry toolRegistry;
    private McpFeatureService service;

    @BeforeEach
    void setup() {
        toolRegistry = mock(McpToolRegistry.class);
        service = new McpFeatureService(
                toolRegistry,
                mock(McpResourceRegistry.class),
                mock(McpPromptRegistry.class),
                mock(McpToolInvoker.class),
                mock(McpBeanInvoker.class),
                new McpCancellationManager(),
                new McpServerConfigResolver(new McpServerConfig("srv", "1.0")));
    }

    private static Method method(String name) throws Exception {
        return AnnotatedTool.class.getMethod(name, String.class);
    }

    private void register(String methodName) throws Exception {
        when(toolRegistry.listTools())
                .thenReturn(List.of(McpToolDescriptor.fromMethod(AnnotatedTool.class, method(methodName))));
    }

    @Test
    void modernListingCarriesTitleAndFullAnnotations() throws Exception {
        register("fullyAnnotated");

        JsonObject tool = service.listTools(null, true).getJsonArray("tools").getJsonObject(0);

        assertThat(tool.getJsonObject("annotations").toString())
                .isEqualTo(
                        "{\"destructiveHint\":false,\"idempotentHint\":true,\"openWorldHint\":false,\"readOnlyHint\":true,\"title\":\"Annotated Title\"}");
    }

    @Test
    void modernListingCarriesOnlyTheDifferingAnnotationMember() throws Exception {
        register("partiallyAnnotated");

        JsonObject tool = service.listTools(null, true).getJsonArray("tools").getJsonObject(0);

        assertThat(tool.getJsonObject("annotations").toString()).isEqualTo("{\"readOnlyHint\":true}");
    }

    @Test
    void modernListingOmitsAnnotationsWhenEveryMemberIsDefault() throws Exception {
        register("plain");

        JsonObject tool = service.listTools(null, true).getJsonArray("tools").getJsonObject(0);

        assertThat(tool).doesNotContainKey("annotations");
        assertThat(tool).doesNotContainKey("title");
    }

    @Test
    void modernListingCarriesTopLevelTitleWhenSet() throws Exception {
        register("titledOnly");

        JsonObject tool = service.listTools(null, true).getJsonArray("tools").getJsonObject(0);

        assertThat(tool.getString("title")).isEqualTo("Human Title");
        assertThat(tool).doesNotContainKey("annotations");
    }

    @Test
    void legacyListingIsByteIdenticalAndCarriesNeitherTitleNorAnnotations() throws Exception {
        register("fullyAnnotated");

        assertThat(service.listTools(null).toString())
                .isEqualTo(
                        "{\"tools\":[{\"description\":\"A tool with every annotation member set away from its default\",\"inputSchema\":{\"type\":\"object\",\"properties\":{\"input\":{\"type\":\"string\",\"description\":\"input\"}},\"required\":[\"input\"]},\"name\":\"annotated_tool\"}]}");
    }
}
