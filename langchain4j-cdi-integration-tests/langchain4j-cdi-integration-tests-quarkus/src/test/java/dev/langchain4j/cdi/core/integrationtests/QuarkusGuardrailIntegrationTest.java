package dev.langchain4j.cdi.core.integrationtests;

import static org.assertj.core.api.Assertions.assertThat;

import io.quarkus.test.junit.QuarkusTest;
import jakarta.ws.rs.client.Client;
import jakarta.ws.rs.client.ClientBuilder;
import jakarta.ws.rs.client.Entity;
import jakarta.ws.rs.client.WebTarget;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.junit.jupiter.api.Test;

@QuarkusTest
public class QuarkusGuardrailIntegrationTest {

    @ConfigProperty(name = "quarkus.http.test-port")
    int port;

    @Test
    public void testGuardrailChatWithValidMessage() {
        String chatEndpoint = "http://localhost:" + port + "/guarded-chat";
        try (Client client = ClientBuilder.newClient()) {
            WebTarget target = client.target(chatEndpoint);

            String question = "What is the meaning of life?";
            Response response = target.request(MediaType.APPLICATION_JSON)
                    .post(Entity.entity(question, MediaType.APPLICATION_JSON));

            assertThat(response.getStatus()).isEqualTo(200);
            String result = response.readEntity(String.class);
            // Quarkus ChatModelMock returns "ok" (no embeddingStore configured, unlike Jakarta EE mock)
            assertThat(result).isNotNull().isEqualTo("ok");
        }
    }

    @Test
    public void testGuardrailChatWithEmptyMessage() {
        String chatEndpoint = "http://localhost:" + port + "/guarded-chat";
        try (Client client = ClientBuilder.newClient()) {
            WebTarget target = client.target(chatEndpoint);

            Response response =
                    target.request(MediaType.APPLICATION_JSON).post(Entity.entity("", MediaType.APPLICATION_JSON));

            assertThat(response.getStatus()).isEqualTo(400);
        }
    }
}
