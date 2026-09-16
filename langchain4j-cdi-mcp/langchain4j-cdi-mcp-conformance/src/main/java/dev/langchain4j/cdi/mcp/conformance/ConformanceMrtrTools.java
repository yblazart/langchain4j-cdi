package dev.langchain4j.cdi.mcp.conformance;

import dev.langchain4j.cdi.mcp.server.api.Elicitation;
import dev.langchain4j.cdi.mcp.server.api.ElicitationResponse;
import dev.langchain4j.cdi.mcp.server.api.McpInteractionResults;
import dev.langchain4j.cdi.mcp.server.api.McpInteractions;
import dev.langchain4j.cdi.mcp.server.api.Roots;
import dev.langchain4j.cdi.mcp.server.api.Sampling;
import dev.langchain4j.cdi.mcp.server.api.SamplingResponse;
import dev.langchain4j.cdi.mcp.server.protocol.McpRoot;
import jakarta.enterprise.context.ApplicationScoped;
import java.util.List;
import java.util.Map;
import org.mcpjava.server.Role;
import org.mcpjava.server.content.TextContent;
import org.mcpjava.server.prompts.Prompt;
import org.mcpjava.server.prompts.PromptResponse;
import org.mcpjava.server.tools.Tool;
import org.mcpjava.server.tools.ToolResponse;

/**
 * Multi round-trip request (MRTR / {@code input_required}, SEP-2322) fixtures.
 *
 * <p>Most methods use the ordinary blocking client-interaction API: with an MCP 2026-07-28 client the server cannot
 * send its own requests, so the framework turns the first pending interaction into an {@code input_required} result and
 * the client retries the call with the answer. {@link #multipleInputs} instead uses {@link McpInteractions} to declare
 * a batch of interactions and await all of their answers together, so a single {@code input_required} result can list
 * several pending requests at once (SEP-2322 batch interactions).
 */
@ApplicationScoped
public class ConformanceMrtrTools {

    /** Creates a new instance. */
    public ConformanceMrtrTools() {}

    /**
     * Single elicitation round trip.
     *
     * @param elicitation the elicitation API
     * @return the tool response
     */
    @Tool(
            name = "test_input_required_result_elicitation",
            description = "Ephemeral InputRequiredResult flow with a single elicitation input request")
    public ToolResponse elicitation(Elicitation elicitation) {
        ElicitationResponse response = askName(elicitation, "What is your name?");
        return ToolResponse.ofText("Hello, " + name(response) + "!");
    }

    /**
     * Single sampling round trip.
     *
     * @param sampling the sampling API
     * @return the tool response
     */
    @Tool(
            name = "test_input_required_result_sampling",
            description = "Ephemeral InputRequiredResult flow with a single sampling input request")
    public ToolResponse sampling(Sampling sampling) {
        SamplingResponse response = sampling.requestBuilder()
                .addMessage(ConformanceTools.userMessage("What is the capital of France?"))
                .setMaxTokens(100)
                .build()
                .sendAndAwait();
        return ToolResponse.ofText("Sampling answer: " + ConformanceTools.describe(response));
    }

    /**
     * Single {@code roots/list} round trip.
     *
     * @param roots the roots API
     * @return the tool response
     */
    @Tool(
            name = "test_input_required_result_list_roots",
            description = "Ephemeral InputRequiredResult flow with a single roots/list input request")
    public ToolResponse listRoots(Roots roots) {
        List<McpRoot> list = roots.listAndAwait();
        return ToolResponse.ofText("Client roots: " + list);
    }

    /**
     * Elicitation round trip whose completion proves the signed {@code requestState} was validated.
     *
     * @param elicitation the elicitation API
     * @return the tool response
     */
    @Tool(
            name = "test_input_required_result_request_state",
            description = "InputRequiredResult flow that round-trips an opaque requestState")
    public ToolResponse requestState(Elicitation elicitation) {
        ElicitationResponse response = elicitation
                .requestBuilder()
                .setMessage("Please confirm")
                .addSchemaProperty("ok", () -> Map.of("type", "boolean"))
                .build()
                .sendAndAwait();
        return ToolResponse.ofText("state-ok: " + ConformanceTools.describe(response));
    }

    /**
     * Elicitation, sampling and {@code roots/list} in one batch, awaited together (SEP-2322 batch interactions): proves
     * an {@code input_required} result can list several requests of different methods at once, and that a retry
     * answering all of them completes the call.
     *
     * @param interactions the batch API
     * @param elicitation the elicitation API
     * @param sampling the sampling API
     * @return the tool response
     */
    @Tool(
            name = "test_input_required_result_multiple_inputs",
            description = "InputRequiredResult flow requiring elicitation, sampling and roots/list")
    public ToolResponse multipleInputs(McpInteractions interactions, Elicitation elicitation, Sampling sampling) {
        McpInteractionResults answers = interactions
                .batch()
                .elicit(
                        "user_name",
                        elicitation
                                .requestBuilder()
                                .setMessage("What is your name?")
                                .addSchemaProperty("name", () -> Map.of("type", "string"))
                                .build())
                .sample(
                        "greeting",
                        sampling.requestBuilder()
                                .addMessage(ConformanceTools.userMessage("Generate a greeting"))
                                .setMaxTokens(50)
                                .build())
                .roots("client_roots")
                .awaitAll();

        String who = name(answers.elicitation("user_name"));
        SamplingResponse greeting = answers.sampling("greeting");
        List<McpRoot> list = answers.roots("client_roots");
        return ToolResponse.ofText(
                "name=" + who + ", greeting=" + ConformanceTools.describe(greeting) + ", roots=" + list);
    }

    /**
     * Two successive elicitation rounds.
     *
     * @param elicitation the elicitation API
     * @return the tool response
     */
    @Tool(
            name = "test_input_required_result_multi_round",
            description = "Multi-round InputRequiredResult flow with evolving requestState")
    public ToolResponse multiRound(Elicitation elicitation) {
        ElicitationResponse step1 = askName(elicitation, "Step 1: What is your name?");
        ElicitationResponse step2 = elicitation
                .requestBuilder()
                .setMessage("Step 2: What is your favorite color?")
                .addSchemaProperty("color", () -> Map.of("type", "string"))
                .build()
                .sendAndAwait();
        String color = step2 == null ? "unknown" : step2.content().getString("color");
        return ToolResponse.ofText("Hello, " + name(step1) + "! Your favorite color is " + color + ".");
    }

    /**
     * Elicitation round trip used to exercise tampering with the signed {@code requestState}.
     *
     * @param elicitation the elicitation API
     * @return the tool response
     */
    @Tool(
            name = "test_input_required_result_tampered_state",
            description = "InputRequiredResult flow whose requestState is integrity protected")
    public ToolResponse tamperedState(Elicitation elicitation) {
        ElicitationResponse response = elicitation
                .requestBuilder()
                .setMessage("Please confirm")
                .addSchemaProperty("ok", () -> Map.of("type", "boolean"))
                .build()
                .sendAndAwait();
        return ToolResponse.ofText("state accepted: " + ConformanceTools.describe(response));
    }

    /**
     * Requests only the interactions the client declared support for.
     *
     * @param elicitation the elicitation API
     * @param sampling the sampling API
     * @return the tool response
     */
    @Tool(
            name = "test_input_required_result_capabilities",
            description = "InputRequiredResult flow that only requests declared client capabilities")
    public ToolResponse capabilities(Elicitation elicitation, Sampling sampling) {
        StringBuilder result = new StringBuilder("capabilities:");
        if (elicitation.isSupported()) {
            result.append(" elicitation=").append(name(askName(elicitation, "What is your name?")));
        }
        if (sampling.isSupported()) {
            SamplingResponse response = sampling.requestBuilder()
                    .addMessage(ConformanceTools.userMessage("Generate a greeting"))
                    .setMaxTokens(50)
                    .build()
                    .sendAndAwait();
            result.append(" sampling=").append(ConformanceTools.describe(response));
        }
        return ToolResponse.ofText(result.toString());
    }

    /**
     * A prompt that needs client input before it can be rendered, proving {@code input_required} is not tool-specific.
     *
     * @param elicitation the elicitation API
     * @return the rendered prompt
     */
    @Prompt(
            name = "test_input_required_result_prompt",
            description = "Prompt that requires elicitation input before it can be rendered")
    public PromptResponse inputRequiredPrompt(Elicitation elicitation) {
        ElicitationResponse response = elicitation
                .requestBuilder()
                .setMessage("What context should the prompt use?")
                .addSchemaProperty("context", () -> Map.of("type", "string"))
                .build()
                .sendAndAwait();
        String context = response == null ? "none" : response.content().getString("context");
        return PromptResponse.builder()
                .addMessage(Role.USER, TextContent.of("Prompt rendered with context: " + context))
                .build();
    }

    /**
     * Asks for the caller's name under the key {@code user_name}: {@code input-required-result-basic-elicitation}
     * (SEP-2322) requires that exact key verbatim.
     */
    private static ElicitationResponse askName(Elicitation elicitation, String message) {
        return elicitation
                .requestBuilder()
                .setMessage(message)
                .addSchemaProperty("name", () -> Map.of("type", "string"))
                .setKey("user_name")
                .build()
                .sendAndAwait();
    }

    private static String name(ElicitationResponse response) {
        return response == null ? "stranger" : response.content().getString("name");
    }
}
