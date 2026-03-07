package dev.langchain4j.cdi.mcp.integrationtests.wildfly;

import static org.assertj.core.api.Assertions.assertThat;

import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.cdi.mcp.integrationtests.ConfigResource;
import dev.langchain4j.cdi.mcp.integrationtests.GreetingTool;
import dev.langchain4j.cdi.mcp.integrationtests.SummarizePrompt;
import dev.langchain4j.cdi.mcp.integrationtests.WeatherTool;
import dev.langchain4j.mcp.client.DefaultMcpClient;
import dev.langchain4j.mcp.client.McpClient;
import dev.langchain4j.mcp.client.transport.http.StreamableHttpMcpTransport;
import dev.langchain4j.service.tool.ToolExecutionResult;
import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.nio.file.FileVisitOption;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.List;
import java.util.Optional;
import java.util.function.BiPredicate;
import java.util.stream.Stream;
import org.jboss.arquillian.container.test.api.Deployment;
import org.jboss.arquillian.junit5.ArquillianExtension;
import org.jboss.arquillian.test.api.ArquillianResource;
import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.asset.EmptyAsset;
import org.jboss.shrinkwrap.api.spec.WebArchive;
import org.jboss.shrinkwrap.resolver.api.maven.Maven;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

@SuppressWarnings({"OptionalGetWithoutIsPresent", "resource"})
@ExtendWith(ArquillianExtension.class)
public class McpWildFlyArquillianTest {

    @SuppressWarnings("unused")
    @Deployment
    public static WebArchive createDeployment() throws IOException {
        File mcpServerFile = findBuildFiles(
                        new File("../../langchain4j-cdi-mcp-server/target").toPath(), "langchain4j-cdi-mcp-server-")
                .get()
                .toFile();
        File mcpPortableExtFile = findBuildFiles(
                        new File("../../langchain4j-cdi-mcp-portable-ext/target").toPath(),
                        "langchain4j-cdi-mcp-portable-ext-")
                .get()
                .toFile();

        File[] deps = Maven.resolver()
                .loadPomFromFile("pom.xml")
                .importRuntimeDependencies()
                .resolve(
                        "dev.langchain4j.cdi.mcp:langchain4j-cdi-mcp-portable-ext",
                        "dev.langchain4j:langchain4j-mcp",
                        "org.assertj:assertj-core")
                .withTransitivity()
                .asFile();

        File[] fixedDeps = Stream.concat(
                        Stream.of(mcpServerFile, mcpPortableExtFile),
                        Stream.of(deps).filter(f -> !f.getName().startsWith("langchain4j-cdi-mcp-")))
                .toArray(File[]::new);

        return ShrinkWrap.create(WebArchive.class, "mcp-test.war")
                .addClasses(
                        McpWildFlyArquillianTest.class,
                        WeatherTool.class,
                        GreetingTool.class,
                        ConfigResource.class,
                        SummarizePrompt.class,
                        JaxRsApplication.class)
                .addAsLibraries(fixedDeps)
                .addAsWebInfResource(EmptyAsset.INSTANCE, "beans.xml")
                .addAsManifestResource(EmptyAsset.INSTANCE, "beans.xml")
                .addAsResource("META-INF/services/jakarta.enterprise.inject.spi.Extension");
    }

    private static Optional<Path> findBuildFiles(Path folder, String prefix) throws IOException {
        return Files.find(
                        folder,
                        1,
                        (BiPredicate<Path, BasicFileAttributes>) (t, u) -> {
                            String fileName = t.getFileName().toString();
                            return fileName.startsWith(prefix) && fileName.endsWith(".jar");
                        },
                        FileVisitOption.FOLLOW_LINKS)
                .findFirst();
    }

    @SuppressWarnings("unused")
    @ArquillianResource
    private URL baseURL;

    @Test
    public void shouldListToolsViaMcpClient() throws Exception {
        try (McpClient client = buildClient()) {
            List<ToolSpecification> tools = client.listTools();
            assertThat(tools).hasSizeGreaterThanOrEqualTo(2);
            List<String> toolNames = tools.stream().map(ToolSpecification::name).toList();
            assertThat(toolNames).contains("getWeather", "greet");
        }
    }

    @Test
    public void shouldCallToolViaMcpClient() throws Exception {
        try (McpClient client = buildClient()) {
            ToolExecutionRequest request = ToolExecutionRequest.builder()
                    .name("getWeather")
                    .arguments("{\"city\":\"London\",\"unit\":\"celsius\"}")
                    .build();
            ToolExecutionResult result = client.executeTool(request);
            assertThat(result.resultText()).contains("London");
        }
    }

    private McpClient buildClient() {
        return DefaultMcpClient.builder()
                .transport(StreamableHttpMcpTransport.builder()
                        .url(baseURL + "mcp")
                        .build())
                .build();
    }
}
