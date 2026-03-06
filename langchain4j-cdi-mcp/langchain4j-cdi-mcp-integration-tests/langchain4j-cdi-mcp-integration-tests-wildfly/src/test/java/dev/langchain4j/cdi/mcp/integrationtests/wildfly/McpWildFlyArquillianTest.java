package dev.langchain4j.cdi.mcp.integrationtests.wildfly;

import static org.assertj.core.api.Assertions.assertThat;

import dev.langchain4j.cdi.mcp.integrationtests.McpTestRequests;
import dev.langchain4j.cdi.mcp.integrationtests.WeatherTool;
import jakarta.ws.rs.client.Client;
import jakarta.ws.rs.client.ClientBuilder;
import jakarta.ws.rs.client.Entity;
import jakarta.ws.rs.client.WebTarget;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.nio.file.FileVisitOption;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
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
                .resolve("dev.langchain4j.cdi.mcp:langchain4j-cdi-mcp-portable-ext", "org.assertj:assertj-core")
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
                        McpTestRequests.class,
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
    public void shouldCompleteFullMcpHandshake() {
        String mcpEndpoint = baseURL + "mcp";
        try (Client client = ClientBuilder.newClient()) {
            WebTarget target = client.target(mcpEndpoint);

            // 1. initialize
            Response initResponse = target.request(MediaType.APPLICATION_JSON)
                    .post(Entity.entity(McpTestRequests.initializeRequest(), MediaType.APPLICATION_JSON));
            assertThat(initResponse.getStatus()).isEqualTo(200);
            String initResult = initResponse.readEntity(String.class);
            assertThat(initResult).contains("2025-03-26");

            String sessionId = initResponse.getHeaderString("Mcp-Session-Id");
            assertThat(sessionId).isNotNull().isNotBlank();

            // 2. tools/list
            Response listResponse = target.request(MediaType.APPLICATION_JSON)
                    .header("Mcp-Session-Id", sessionId)
                    .post(Entity.entity(McpTestRequests.toolsListRequest(), MediaType.APPLICATION_JSON));
            assertThat(listResponse.getStatus()).isEqualTo(200);
            String listResult = listResponse.readEntity(String.class);
            assertThat(listResult).contains("getWeather");

            // 3. tools/call
            Response callResponse = target.request(MediaType.APPLICATION_JSON)
                    .header("Mcp-Session-Id", sessionId)
                    .post(Entity.entity(
                            McpTestRequests.toolsCallRequest(
                                    "getWeather", "{\"city\":\"London\",\"unit\":\"celsius\"}"),
                            MediaType.APPLICATION_JSON));
            assertThat(callResponse.getStatus()).isEqualTo(200);
            String callResult = callResponse.readEntity(String.class);
            assertThat(callResult).contains("London");
        }
    }
}
