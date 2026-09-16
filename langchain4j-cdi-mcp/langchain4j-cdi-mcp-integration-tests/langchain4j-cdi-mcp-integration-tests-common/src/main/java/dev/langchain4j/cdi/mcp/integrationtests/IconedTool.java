package dev.langchain4j.cdi.mcp.integrationtests;

import jakarta.enterprise.context.ApplicationScoped;
import java.util.List;
import java.util.Optional;
import org.mcpjava.server.FeatureType;
import org.mcpjava.server.Icon;
import org.mcpjava.server.IconProvider;
import org.mcpjava.server.Icons;
import org.mcpjava.server.tools.Tool;

/** MCP tool whose icons come from a CDI-managed {@link IconProvider}. */
@ApplicationScoped
public class IconedTool {

    /** Name of the tool this fixture registers. */
    public static final String ICONED = "iconedTool";

    /** Source URI of the icon the provider returns. */
    public static final String ICON_SRC = "https://example.org/iconed-tool.png";

    /** Creates a new instance. */
    public IconedTool() {}

    /**
     * Returns a fixed answer; the point of the fixture is the {@code @Icons} annotation, not the result.
     *
     * @return a fixed string
     */
    @Tool(name = ICONED, description = "A tool that advertises icons")
    @Icons(iconProvider = CdiIconProvider.class)
    public String iconedTool() {
        return "iconed";
    }

    /**
     * A CDI bean implementing the upstream {@link IconProvider} SPI.
     *
     * <p>It builds its {@link Icon} directly rather than through {@code Icon.builder(…)}: that factory goes through
     * {@code org.mcpjava.server.spi.McpServerSPILoader}, which loads the SPI with
     * {@code McpServerSPI.class.getClassLoader()} and therefore cannot see the {@code META-INF/services} entry of
     * {@code langchain4j-cdi-mcp-server} under Quarkus (the API jar sits in the parent class loader, the server jar in
     * the child application archive). That is an upstream loader limitation affecting every {@code org.mcpjava} static
     * factory, not something specific to icons, so this fixture sidesteps it and keeps the suite portable.
     */
    @ApplicationScoped
    public static class CdiIconProvider implements IconProvider {

        /** Creates a new instance. */
        public CdiIconProvider() {}

        @Override
        public List<Icon> getIcons(FeatureType featureType, String name) {
            return List.of(new FixedIcon());
        }
    }

    /** A minimal {@link Icon}: a light-theme 48x48 PNG. */
    public static class FixedIcon implements Icon {

        /** Creates a new instance. */
        public FixedIcon() {}

        @Override
        public String src() {
            return ICON_SRC;
        }

        @Override
        public Optional<String> mimeType() {
            return Optional.of("image/png");
        }

        @Override
        public List<String> sizes() {
            return List.of("48x48");
        }

        @Override
        public Optional<Theme> theme() {
            return Optional.of(Theme.LIGHT);
        }
    }
}
