package dev.langchain4j.cdi.mcp.server.spi;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.module.ModuleDescriptor;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

/**
 * The module descriptor and {@code META-INF/services} must register the same service providers.
 *
 * <p>The JDK reads {@code META-INF/services} only for a jar on the class path, and only the {@code provides} clauses of
 * {@code module-info} for a named module. When the two drift, the provider is found in one launch and silently missing
 * in the other: with no {@code provides} for {@code McpServerSPI}, the MCP server worked from the class path and failed
 * every call that goes through {@code McpServerSPILoader} ("No McpServerSPI implementation found") as soon as it ran on
 * the module path. The integration suites all run from the class path, so nothing else in the build could see it.
 */
class ModuleDescriptorServicesTest {

    @Test
    void everyServiceFileHasTheSameProvidesClause() throws IOException, URISyntaxException {
        Path classes = Path.of(CdiMcpServerSPI.class
                .getProtectionDomain()
                .getCodeSource()
                .getLocation()
                .toURI());
        ModuleDescriptor descriptor;
        try (InputStream in = Files.newInputStream(classes.resolve("module-info.class"))) {
            descriptor = ModuleDescriptor.read(in);
        }
        Map<String, List<String>> provides = new TreeMap<>();
        descriptor.provides().forEach(p -> provides.put(p.service(), List.copyOf(p.providers())));

        Map<String, List<String>> serviceFiles = new TreeMap<>();
        Path services = classes.resolve("META-INF/services");
        if (Files.isDirectory(services)) {
            try (var files = Files.list(services)) {
                for (Path file : files.collect(Collectors.toList())) {
                    serviceFiles.put(file.getFileName().toString(), providersIn(file));
                }
            }
        }

        assertTrue(
                serviceFiles.containsKey("org.mcpjava.server.spi.McpServerSPI"),
                "the class-path registration of the MCP server SPI is expected in META-INF/services");
        assertEquals(
                serviceFiles,
                provides,
                "module-info's 'provides' clauses must list exactly the providers of META-INF/services");
    }

    private static List<String> providersIn(Path serviceFile) throws IOException {
        try (BufferedReader reader =
                new BufferedReader(new InputStreamReader(Files.newInputStream(serviceFile), StandardCharsets.UTF_8))) {
            return reader.lines()
                    .map(line -> line.replaceFirst("#.*", "").strip())
                    .filter(line -> !line.isEmpty())
                    .collect(Collectors.toList());
        }
    }
}
