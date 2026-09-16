package dev.langchain4j.cdi.mcp.server.registry;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.langchain4j.cdi.mcp.server.error.McpIconProviderException;
import dev.langchain4j.cdi.mcp.server.fixtures.IconFixtures;
import dev.langchain4j.cdi.mcp.server.protocol.McpIconModel;
import java.lang.reflect.Method;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Registration-time resolution of the upstream {@code org.mcpjava.server.Icons} contract. */
class McpIconsTest {

    private static Method method(Class<?> owner, String name, Class<?>... parameterTypes) throws Exception {
        return owner.getMethod(name, parameterTypes);
    }

    // ---- discovery, one behaviour per feature type ----

    @Test
    void toolCarriesIconsFromClassLevelAnnotation() throws Exception {
        McpToolDescriptor descriptor = McpToolDescriptor.fromMethod(
                IconFixtures.IconedFeatures.class, method(IconFixtures.IconedFeatures.class, "tool", String.class));

        assertThat(descriptor.getIcons())
                .containsExactly(new McpIconModel(
                        "https://icons.example/TOOL/iconed_tool.png", "image/png", List.of("48x48"), "dark"));
    }

    @Test
    void promptCarriesIcons() throws Exception {
        McpPromptDescriptor descriptor = McpPromptDescriptor.fromMethod(
                IconFixtures.IconedFeatures.class, method(IconFixtures.IconedFeatures.class, "prompt"));

        assertThat(descriptor.getIcons())
                .extracting(McpIconModel::src)
                .containsExactly("https://icons.example/PROMPT/iconed_prompt.png");
    }

    @Test
    void resourceCarriesIconsKeyedByNameNotUri() throws Exception {
        McpResourceDescriptor descriptor = McpResourceDescriptor.fromMethod(
                IconFixtures.IconedFeatures.class, method(IconFixtures.IconedFeatures.class, "resource"));

        assertThat(descriptor.getIcons())
                .extracting(McpIconModel::src)
                .containsExactly("https://icons.example/RESOURCE/iconed_resource.png");
    }

    @Test
    void resourceTemplateCarriesIcons() throws Exception {
        McpResourceTemplateDescriptor descriptor = McpResourceTemplateDescriptor.fromMethod(
                IconFixtures.IconedFeatures.class, method(IconFixtures.IconedFeatures.class, "template", String.class));

        assertThat(descriptor.getIcons())
                .extracting(McpIconModel::src)
                .containsExactly("https://icons.example/RESOURCE_TEMPLATE/iconed_template.png");
    }

    @Test
    void methodLevelAnnotationOverridesClassLevelOne() throws Exception {
        McpToolDescriptor overridden = McpToolDescriptor.fromMethod(
                IconFixtures.MethodOverridesClass.class, method(IconFixtures.MethodOverridesClass.class, "tool"));
        McpToolDescriptor inherited = McpToolDescriptor.fromMethod(
                IconFixtures.MethodOverridesClass.class, method(IconFixtures.MethodOverridesClass.class, "other"));

        assertThat(overridden.getIcons())
                .extracting(McpIconModel::src)
                .containsExactly("https://icons.example/TOOL/overridden_tool.png");
        assertThat(inherited.getIcons())
                .extracting(McpIconModel::src)
                .containsExactly("https://icons.example/minimal.svg");
    }

    @Test
    void optionalIconFieldsAreOmittedWhenAbsent() throws Exception {
        McpToolDescriptor descriptor = McpToolDescriptor.fromMethod(
                IconFixtures.MethodOverridesClass.class, method(IconFixtures.MethodOverridesClass.class, "other"));

        assertThat(descriptor.getIcons())
                .containsExactly(new McpIconModel("https://icons.example/minimal.svg", null, null, null));
    }

    // ---- "no icons" means no key at all ----

    @Test
    void emptyIconListMeansNoIcons() throws Exception {
        McpToolDescriptor descriptor = McpToolDescriptor.fromMethod(
                IconFixtures.NoIconFeatures.class, method(IconFixtures.NoIconFeatures.class, "empty"));

        assertThat(descriptor.getIcons()).isNull();
        assertThat(descriptor.toWireFormat(true).icons()).isNull();
    }

    @Test
    void nullIconListMeansNoIcons() throws Exception {
        McpToolDescriptor descriptor = McpToolDescriptor.fromMethod(
                IconFixtures.NoIconFeatures.class, method(IconFixtures.NoIconFeatures.class, "nul"));

        assertThat(descriptor.getIcons()).isNull();
    }

    @Test
    void missingAnnotationMeansNoIcons() throws Exception {
        McpToolDescriptor descriptor = McpToolDescriptor.fromMethod(
                IconFixtures.NoIconFeatures.class, method(IconFixtures.NoIconFeatures.class, "none"));

        assertThat(descriptor.getIcons()).isNull();
    }

    // ---- registration-time failure ----

    @Test
    void providerWithoutNoArgConstructorFailsRegistration() throws Exception {
        Method broken = method(IconFixtures.BrokenIconFeatures.class, "broken");

        assertThatThrownBy(() -> McpToolDescriptor.fromMethod(IconFixtures.BrokenIconFeatures.class, broken))
                .isInstanceOf(McpIconProviderException.class)
                .hasMessageContaining("broken_tool")
                .hasMessageContaining(IconFixtures.UninstantiableIconProvider.class.getName());
    }

    @Test
    void abstractProviderFailsRegistration() throws Exception {
        Method abstractProvider = method(IconFixtures.BrokenIconFeatures.class, "abstractProvider");

        assertThatThrownBy(() -> McpToolDescriptor.fromMethod(IconFixtures.BrokenIconFeatures.class, abstractProvider))
                .isInstanceOf(McpIconProviderException.class)
                .hasMessageContaining("abstract_tool")
                .hasMessageContaining(IconFixtures.AbstractIconProvider.class.getName());
    }

    @Test
    void registrationFailureNamesThePromptToo() throws Exception {
        Method brokenPrompt = method(IconFixtures.BrokenIconFeatures.class, "brokenPrompt");

        assertThatThrownBy(() -> McpPromptDescriptor.fromMethod(IconFixtures.BrokenIconFeatures.class, brokenPrompt))
                .isInstanceOf(McpIconProviderException.class)
                .hasMessageContaining("PROMPT")
                .hasMessageContaining("broken_prompt");
    }
}
