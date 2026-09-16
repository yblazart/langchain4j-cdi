package dev.langchain4j.cdi.mcp.server.registry;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import org.junit.jupiter.api.Test;
import org.mcpjava.server.resources.ResourceTemplate;

class McpResourceTemplateDescriptorTest {

    @SuppressWarnings("unused")
    static class TestBean {
        @ResourceTemplate(
                uriTemplate = "user://{userId}/profile",
                name = "User Profile",
                description = "Get user profile",
                mimeType = "application/json")
        public String getUserProfile(String userId) {
            return "{}";
        }

        @ResourceTemplate(uriTemplate = "file:///{path}")
        public String getFile(String path) {
            return "";
        }
    }

    @Test
    void shouldCreateFromMethodWithAllAttributes() throws Exception {
        var method = TestBean.class.getMethod("getUserProfile", String.class);
        McpResourceTemplateDescriptor descriptor = McpResourceTemplateDescriptor.fromMethod(TestBean.class, method);

        assertThat(descriptor.getUriTemplate()).isEqualTo("user://{userId}/profile");
        assertThat(descriptor.getName()).isEqualTo("User Profile");
        assertThat(descriptor.getDescription()).isEqualTo("Get user profile");
        assertThat(descriptor.getMimeType()).isEqualTo("application/json");
    }

    @Test
    void shouldDefaultNameToMethodName() throws Exception {
        var method = TestBean.class.getMethod("getFile", String.class);
        McpResourceTemplateDescriptor descriptor = McpResourceTemplateDescriptor.fromMethod(TestBean.class, method);

        assertThat(descriptor.getUriTemplate()).isEqualTo("file:///{path}");
        assertThat(descriptor.getName()).isEqualTo("getFile");
        assertThat(descriptor.getMimeType()).isEqualTo("text/plain");
    }

    private static McpResourceTemplateDescriptor template(String uriTemplate) {
        return new McpResourceTemplateDescriptor(uriTemplate, "n", "d", "text/plain", TestBean.class, null);
    }

    @Test
    void shouldMatchAnInstanceUriAndExtractItsVariables() {
        McpResourceTemplateDescriptor descriptor = template("test://template/{id}/data");

        assertThat(descriptor.match("test://template/123/data")).hasValue(Map.of("id", "123"));
    }

    @Test
    void shouldExtractSeveralVariables() {
        McpResourceTemplateDescriptor descriptor = template("db://{schema}/{table}/rows");

        assertThat(descriptor.match("db://public/orders/rows")).hasValue(Map.of("schema", "public", "table", "orders"));
    }

    @Test
    void shouldNotMatchAUriOfADifferentShape() {
        McpResourceTemplateDescriptor descriptor = template("test://template/{id}/data");

        assertThat(descriptor.match("test://template/123")).isEmpty();
        assertThat(descriptor.match("test://template/123/other")).isEmpty();
        assertThat(descriptor.match("test://template/1/2/data")).isEmpty();
    }

    @Test
    void shouldNotLetRegexMetacharactersOfTheTemplateLeakIntoThePattern() {
        McpResourceTemplateDescriptor descriptor = template("test://a.b/{id}");

        assertThat(descriptor.match("test://axb/1")).isEmpty();
        assertThat(descriptor.match("test://a.b/1")).hasValue(Map.of("id", "1"));
    }

    @Test
    void aTrailingVariableCapturesTheRestOfThePath() {
        McpResourceTemplateDescriptor descriptor = template("file:///{path}");

        assertThat(descriptor.match("file:///a/b/c.txt")).hasValue(Map.of("path", "a/b/c.txt"));
    }

    @Test
    void shouldPercentDecodeExtractedVariables() {
        McpResourceTemplateDescriptor descriptor = template("test://template/{id}/data");

        assertThat(descriptor.match("test://template/a%20b/data")).hasValue(Map.of("id", "a b"));
        assertThat(descriptor.match("test://template/a+b/data")).hasValue(Map.of("id", "a+b"));
    }

    @Test
    void shouldFallBackToTheRawValueOnAnIncompleteEscape() {
        McpResourceTemplateDescriptor descriptor = template("test://template/{id}/data");

        assertThat(descriptor.match("test://template/100%/data")).hasValue(Map.of("id", "100%"));
    }

    @Test
    void shouldFallBackToTheRawValueOnIllegalHexCharacters() {
        McpResourceTemplateDescriptor descriptor = template("test://template/{id}/data");

        assertThat(descriptor.match("test://template/a%zzb/data")).hasValue(Map.of("id", "a%zzb"));
    }

    @Test
    void aTemplateWithoutVariablesOnlyMatchesItself() {
        McpResourceTemplateDescriptor descriptor = template("test://plain");

        assertThat(descriptor.getVariableNames()).isEmpty();
        assertThat(descriptor.match("test://plain")).hasValue(Map.of());
        assertThat(descriptor.match("test://other")).isEmpty();
    }

    @Test
    void shouldExposeItsVariableNamesInOrder() {
        assertThat(template("db://{schema}/{table}/rows").getVariableNames()).containsExactly("schema", "table");
    }
}
