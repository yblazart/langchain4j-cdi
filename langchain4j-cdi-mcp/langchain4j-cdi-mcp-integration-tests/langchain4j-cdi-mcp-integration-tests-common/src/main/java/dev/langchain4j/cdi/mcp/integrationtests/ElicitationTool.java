package dev.langchain4j.cdi.mcp.integrationtests;

import dev.langchain4j.cdi.mcp.server.api.Elicitation;
import dev.langchain4j.cdi.mcp.server.api.ElicitationResponse;
import jakarta.enterprise.context.ApplicationScoped;
import java.util.Map;
import org.mcpjava.server.tools.Tool;

/** Tool that asks the client for input, used to test MRTR. */
@ApplicationScoped
public class ElicitationTool {

    public ElicitationTool() {}

    @Tool(description = "Ask the user for their name and greet them")
    public String askName(Elicitation elicitation) {
        ElicitationResponse response = elicitation
                .requestBuilder()
                .setMessage("What is your name?")
                .addSchemaProperty("name", () -> Map.of("type", "string"))
                .build()
                .sendAndAwait();
        if (response == null || response.action() != ElicitationResponse.Action.ACCEPT) {
            return "Hello, stranger!";
        }
        return "Hello, " + response.content().getString("name") + "!";
    }
}
