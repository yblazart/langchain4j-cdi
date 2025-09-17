package dev.langchain4j.cdi.core.integrationtests;

import static org.assertj.core.api.Assertions.assertThat;

import dev.langchain4j.cdi.integrationtests.ChatAiService;
import dev.langchain4j.cdi.integrationtests.ChatRestService;
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

@ExtendWith(ArquillianExtension.class)
public class ChatRestServiceArquillianTest {

    @SuppressWarnings("unused")
    @Deployment
    public static WebArchive createDeployment() throws IOException {
        // Include the application classes and the portable extension as a library
        File langchain4jCdiPortableExtFile = findBuildFiles(
                        new File("../../../langchain4j-cdi-portable-ext/target").toPath(),
                        "langchain4j-cdi-portable-ext-")
                .get()
                .toFile();
        File langchain4jCdiCoreFile = findBuildFiles(
                        new File("../../../langchain4j-cdi-core/target").toPath(), "langchain4j-cdi-core-")
                .get()
                .toFile();

        File[] deps = Maven.resolver()
                .loadPomFromFile("pom.xml") // lit ton pom
                .importRuntimeDependencies() // récupère ce qu'il faut pour tourner
                .resolve("dev.langchain4j.cdi:langchain4j-cdi-portable-ext", "org.assertj:assertj-core")
                .withTransitivity()
                .asFile();

        File[] fixedDeps = Stream.concat(
                        Stream.of(langchain4jCdiPortableExtFile, langchain4jCdiCoreFile),
                        Stream.of(deps).filter(f -> !f.getName().startsWith("langchain4j-cdi-")))
                .toArray(File[]::new);

        return ShrinkWrap.create(WebArchive.class, "chat-test.war")
                .addClasses(
                        ChatAiService.class,
                        ChatRestService.class,
                        JaxRsApplication.class,
                        DummyLLConfig.class,
                        ChatModelMock.class,
                        EmbeddingStoreString.class,
                        EmbeddingStoreTextSegment.class)
                .addAsLibraries(fixedDeps)
                .addAsWebInfResource(EmptyAsset.INSTANCE, "beans.xml")
                .addAsManifestResource(EmptyAsset.INSTANCE, "beans.xml")
                .addAsResource("llm-config.properties")
                .addAsResource("META-INF/services/jakarta.enterprise.inject.spi.Extension")
                .addAsResource("META-INF/services/dev.langchain4j.cdi.core.config.spi.LLMConfig");
    }

    private static Optional<Path> findBuildFiles(Path folder, String prefix) throws IOException {
        return Files.find(
                        folder,
                        1,
                        new BiPredicate<Path, BasicFileAttributes>() {
                            @Override
                            public boolean test(Path t, BasicFileAttributes u) {
                                String finleName = t.getFileName().toString();
                                return finleName.startsWith(prefix) && finleName.endsWith(".jar");
                            }
                        },
                        FileVisitOption.FOLLOW_LINKS)
                .findFirst();
    }

    @SuppressWarnings("unused")
    @ArquillianResource
    private URL baseURL;

    @Test
    public void testChatRestService() {
        String chatEndpoint = baseURL + "chat";
        Client client = ClientBuilder.newClient();
        WebTarget target = client.target(chatEndpoint);

        String question = "What is the meaning of life?";
        Response response =
                target.request(MediaType.APPLICATION_JSON).post(Entity.entity(question, MediaType.APPLICATION_JSON));

        assertThat(response.getStatus()).isEqualTo(200);
        String result = response.readEntity(String.class);
        assertThat(result).isNotNull().isEqualTo("ok EmbeddingStoreTextSegment{}");

        client.close();
    }
}
