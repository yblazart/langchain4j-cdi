package dev.langchain4j.cdi.mcp.server.fixtures;

import java.util.List;
import org.mcpjava.server.FeatureType;
import org.mcpjava.server.Icon;
import org.mcpjava.server.IconProvider;
import org.mcpjava.server.Icons;
import org.mcpjava.server.prompts.Prompt;
import org.mcpjava.server.resources.Resource;
import org.mcpjava.server.resources.ResourceTemplate;
import org.mcpjava.server.tools.Tool;

/** Fixtures exercising the upstream {@code @Icons} / {@link IconProvider} contract. */
public final class IconFixtures {

    private IconFixtures() {}

    /** Echoes the {@link FeatureType} and the feature name it was called with into the icon {@code src}. */
    public static class EchoIconProvider implements IconProvider {

        @Override
        public List<Icon> getIcons(FeatureType featureType, String name) {
            return List.of(Icon.builder("https://icons.example/" + featureType + "/" + name + ".png")
                    .setMimeType("image/png")
                    .addSize(48, 48)
                    .setTheme(Icon.Theme.DARK)
                    .build());
        }
    }

    /** Returns a scalable, theme-less icon: the minimal legal shape ({@code src} only plus {@code sizes: ["any"]}). */
    public static class MinimalIconProvider implements IconProvider {

        @Override
        public List<Icon> getIcons(FeatureType featureType, String name) {
            return List.of(Icon.builder("https://icons.example/minimal.svg").build());
        }
    }

    /** Returns an empty list: the application has no icon for this feature. */
    public static class EmptyIconProvider implements IconProvider {

        @Override
        public List<Icon> getIcons(FeatureType featureType, String name) {
            return List.of();
        }
    }

    /** Returns {@code null}: also "no icons". */
    public static class NullIconProvider implements IconProvider {

        @Override
        public List<Icon> getIcons(FeatureType featureType, String name) {
            return null;
        }
    }

    /** Cannot be instantiated: it has no no-argument constructor. */
    public static class UninstantiableIconProvider implements IconProvider {

        @SuppressWarnings("unused")
        private final String required;

        public UninstantiableIconProvider(String required) {
            this.required = required;
        }

        @Override
        public List<Icon> getIcons(FeatureType featureType, String name) {
            return List.of();
        }
    }

    /** Cannot be instantiated: it is abstract. */
    public abstract static class AbstractIconProvider implements IconProvider {}

    /** A class-level {@code @Icons} covers every feature the bean declares. */
    @Icons(iconProvider = EchoIconProvider.class)
    public static class IconedFeatures {

        @Tool(name = "iconed_tool", description = "a tool with icons")
        public String tool(String input) {
            return input;
        }

        @Resource(uri = "test://iconed", name = "iconed_resource", description = "a resource with icons")
        public String resource() {
            return "content";
        }

        @ResourceTemplate(
                uriTemplate = "test://iconed/{id}",
                name = "iconed_template",
                description = "a template with icons")
        public String template(String id) {
            return id;
        }

        @Prompt(name = "iconed_prompt", description = "a prompt with icons")
        public String prompt() {
            return "hello";
        }
    }

    /** A method-level {@code @Icons} overrides the class-level one. */
    @Icons(iconProvider = MinimalIconProvider.class)
    public static class MethodOverridesClass {

        @Tool(name = "overridden_tool", description = "method-level icons win")
        @Icons(iconProvider = EchoIconProvider.class)
        public String tool() {
            return "";
        }

        @Tool(name = "inherited_tool", description = "class-level icons apply")
        public String other() {
            return "";
        }
    }

    /** Providers that say "no icons". */
    public static class NoIconFeatures {

        @Tool(name = "empty_icons_tool", description = "provider returns an empty list")
        @Icons(iconProvider = EmptyIconProvider.class)
        public String empty() {
            return "";
        }

        @Tool(name = "null_icons_tool", description = "provider returns null")
        @Icons(iconProvider = NullIconProvider.class)
        public String nul() {
            return "";
        }

        @Tool(name = "no_annotation_tool", description = "no @Icons at all")
        public String none() {
            return "";
        }
    }

    /** A provider that cannot be constructed must break the deployment. */
    public static class BrokenIconFeatures {

        @Tool(name = "broken_tool", description = "provider has no no-arg constructor")
        @Icons(iconProvider = UninstantiableIconProvider.class)
        public String broken() {
            return "";
        }

        @Tool(name = "abstract_tool", description = "provider is abstract")
        @Icons(iconProvider = AbstractIconProvider.class)
        public String abstractProvider() {
            return "";
        }

        @Prompt(name = "broken_prompt", description = "provider has no no-arg constructor")
        @Icons(iconProvider = UninstantiableIconProvider.class)
        public String brokenPrompt() {
            return "";
        }
    }
}
