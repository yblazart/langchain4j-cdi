package dev.langchain4j.cdi.mcp.conformance;

import dev.langchain4j.cdi.mcp.server.api.Elicitation;
import dev.langchain4j.cdi.mcp.server.api.ElicitationResponse;
import dev.langchain4j.cdi.mcp.server.api.McpLog;
import dev.langchain4j.cdi.mcp.server.api.Sampling;
import dev.langchain4j.cdi.mcp.server.api.SamplingResponse;
import dev.langchain4j.cdi.mcp.server.protocol.McpSamplingMessage;
import jakarta.enterprise.context.ApplicationScoped;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import org.mcpjava.server.content.AudioContent;
import org.mcpjava.server.content.EmbeddedResource;
import org.mcpjava.server.content.ImageContent;
import org.mcpjava.server.content.TextContent;
import org.mcpjava.server.progress.Progress;
import org.mcpjava.server.progress.ProgressTracker;
import org.mcpjava.server.tools.Tool;
import org.mcpjava.server.tools.ToolArg;
import org.mcpjava.server.tools.ToolResponse;

/**
 * Content, error, progress, logging and client-interaction tools expected by the official MCP conformance suite
 * ({@code @modelcontextprotocol/conformance}).
 */
@ApplicationScoped
public class ConformanceTools {

    /** Creates a new instance. */
    public ConformanceTools() {}

    // ===== CONTENT TYPES =====

    /**
     * Returns the fixed simple text response.
     *
     * @return the tool response
     */
    @Tool(name = "test_simple_text", description = "Tests simple text content response")
    public ToolResponse testSimpleText() {
        return ToolResponse.ofText("This is a simple text response for testing.");
    }

    /**
     * Returns a 1x1 PNG as image content.
     *
     * @return the tool response
     */
    @Tool(name = "test_image_content", description = "Tests image content response")
    public ToolResponse testImageContent() {
        return ToolResponse.builder()
                .addContent(ImageContent.of(ConformanceFixtures.testImage(), "image/png"))
                .build();
    }

    /**
     * Returns a minimal WAV file as audio content.
     *
     * @return the tool response
     */
    @Tool(name = "test_audio_content", description = "Tests audio content response")
    public ToolResponse testAudioContent() {
        return ToolResponse.builder()
                .addContent(AudioContent.of(ConformanceFixtures.testAudio(), "audio/wav"))
                .build();
    }

    /**
     * Returns an embedded text resource.
     *
     * @return the tool response
     */
    @Tool(name = "test_embedded_resource", description = "Tests embedded resource content response")
    public ToolResponse testEmbeddedResource() {
        return ToolResponse.builder()
                .addContent(
                        EmbeddedResource.builder("This is an embedded resource content.", "test://embedded-resource")
                                .setMimeType("text/plain")
                                .build())
                .build();
    }

    /**
     * Returns text, image and embedded resource content in a single response.
     *
     * @return the tool response
     */
    @Tool(
            name = "test_multiple_content_types",
            description = "Tests response with multiple content types (text, image, resource)")
    public ToolResponse testMultipleContentTypes() {
        return ToolResponse.builder()
                .addContent(TextContent.of("Multiple content types test:"))
                .addContent(ImageContent.of(ConformanceFixtures.testImage(), "image/png"))
                .addContent(
                        EmbeddedResource.builder("{\"test\":\"data\",\"value\":123}", "test://mixed-content-resource")
                                .setMimeType("application/json")
                                .build())
                .build();
    }

    // ===== ERRORS =====

    /**
     * Always reports a tool error.
     *
     * @return an error tool response
     */
    @Tool(name = "test_error_handling", description = "Tests error response handling")
    public ToolResponse testErrorHandling() {
        return ToolResponse.ofError("This tool intentionally returns an error for testing");
    }

    // ===== LOGGING AND PROGRESS =====

    /**
     * Emits three log notifications while running.
     *
     * @param log the MCP log sink
     * @return the tool response
     */
    @Tool(name = "test_tool_with_logging", description = "Tests tool that emits log messages during execution")
    public ToolResponse testToolWithLogging(McpLog log) {
        log.info("Tool execution started");
        pause();
        log.info("Tool processing data");
        pause();
        log.info("Tool execution completed");
        return ToolResponse.ofText("Tool with logging executed successfully");
    }

    /**
     * Emits a single log notification; used to check that nothing is logged when no log level was requested.
     *
     * @param log the MCP log sink
     * @return the tool response
     */
    @Tool(name = "test_logging_tool", description = "Tests that logs are only emitted when a log level was requested")
    public ToolResponse testLoggingTool(McpLog log) {
        log.info("Diagnostic log message");
        return ToolResponse.ofText("Logging tool executed successfully");
    }

    /**
     * Reports progress 0/100, 50/100 and 100/100 when a progress token was supplied.
     *
     * @param progress the progress reporter
     * @return the tool response
     */
    @Tool(name = "test_tool_with_progress", description = "Tests tool that reports progress notifications")
    public ToolResponse testToolWithProgress(Progress progress) {
        if (progress.token().isEmpty()) {
            return ToolResponse.ofText("No progress token provided");
        }
        ProgressTracker tracker = progress.trackerBuilder()
                .setTotal(100)
                .setMessageBuilder(value -> "Completed step " + value + " of 100")
                .build();
        tracker.advanceAndForget(BigDecimal.ZERO);
        pause();
        tracker.advanceAndForget(new BigDecimal(50));
        pause();
        tracker.advanceAndForget(new BigDecimal(50));
        return ToolResponse.ofText(progress.token().get().asString());
    }

    // ===== CLIENT INTERACTIONS =====

    /**
     * Asks the client for an LLM completion.
     *
     * @param prompt the prompt to send to the LLM
     * @param sampling the sampling API
     * @return the tool response
     */
    @Tool(name = "test_sampling", description = "Tests server-initiated sampling (LLM completion request)")
    public ToolResponse testSampling(
            @ToolArg(name = "prompt", description = "The prompt to send to the LLM") String prompt, Sampling sampling) {
        if (!sampling.isSupported()) {
            return ToolResponse.ofText("Sampling not supported or error: Client does not support sampling");
        }
        try {
            SamplingResponse response = sampling.requestBuilder()
                    .addMessage(userMessage(prompt))
                    .setMaxTokens(100)
                    .build()
                    .sendAndAwait();
            return ToolResponse.ofText("LLM response: " + describe(response));
        } catch (RuntimeException e) {
            return ToolResponse.ofText("Sampling not supported or error: " + e.getMessage());
        }
    }

    /**
     * Asks the client for user input.
     *
     * @param message the message to show the user
     * @param elicitation the elicitation API
     * @return the tool response
     */
    @Tool(name = "test_elicitation", description = "Tests server-initiated elicitation (user input request)")
    public ToolResponse testElicitation(
            @ToolArg(name = "message", description = "The message to show the user") String message,
            Elicitation elicitation) {
        if (!elicitation.isSupported()) {
            return ToolResponse.ofText("Elicitation not supported or error: Client does not support elicitation");
        }
        try {
            ElicitationResponse response = elicitation
                    .requestBuilder()
                    .setMessage(message)
                    .addSchemaProperty("username", () -> Map.of("type", "string", "description", "User's response"))
                    .addSchemaProperty("email", () -> Map.of("type", "string", "description", "User's email address"))
                    .build()
                    .sendAndAwait();
            return ToolResponse.ofText("User response: " + describe(response));
        } catch (RuntimeException e) {
            return ToolResponse.ofText("Elicitation not supported or error: " + e.getMessage());
        }
    }

    /**
     * Diagnostic tool for the response stream: it reports progress and asks the client for input, so the harness can
     * check that the stream carries only results and notifications, never independent JSON-RPC requests (SEP-2575).
     *
     * @param progress the progress reporter
     * @param elicitation the elicitation API
     * @return the tool response
     */
    @Tool(name = "test_streaming_elicitation", description = "Diagnostic tool validating response progress streams")
    public ToolResponse testStreamingElicitation(Progress progress, Elicitation elicitation) {
        if (progress.token().isPresent()) {
            progress.notificationBuilder()
                    .setProgress(50)
                    .setTotal(100)
                    .setMessage("Streaming")
                    .build()
                    .sendAndForget();
        }
        ElicitationResponse response = elicitation
                .requestBuilder()
                .setMessage("Streaming elicitation")
                .addSchemaProperty("value", () -> Map.of("type", "string"))
                .build()
                .sendAndAwait();
        return ToolResponse.ofText("Streaming complete: " + describe(response));
    }

    /**
     * Requests elicitation with SEP-1034 default values for every primitive type.
     *
     * @param elicitation the elicitation API
     * @return the tool response
     */
    @Tool(
            name = "test_elicitation_sep1034_defaults",
            description = "Tests elicitation with default values per SEP-1034")
    public ToolResponse testElicitationSep1034Defaults(Elicitation elicitation) {
        if (!elicitation.isSupported()) {
            return ToolResponse.ofText("Elicitation not supported");
        }
        try {
            ElicitationResponse response = elicitation
                    .requestBuilder()
                    .setMessage("Please review and update the form fields with defaults")
                    .addSchemaProperty(
                            "name", () -> Map.of("type", "string", "description", "User name", "default", "John Doe"))
                    .addSchemaProperty("age", () -> Map.of("type", "integer", "description", "User age", "default", 30))
                    .addSchemaProperty(
                            "score", () -> Map.of("type", "number", "description", "User score", "default", 95.5))
                    .addSchemaProperty(
                            "status",
                            () -> Map.of(
                                    "type",
                                    "string",
                                    "description",
                                    "User status",
                                    "enum",
                                    List.of("active", "inactive", "pending"),
                                    "default",
                                    "active"))
                    .addSchemaProperty(
                            "verified",
                            () -> Map.of("type", "boolean", "description", "Verification status", "default", true))
                    .build()
                    .sendAndAwait();
            return ToolResponse.ofText("Elicitation completed: " + describe(response));
        } catch (RuntimeException e) {
            return ToolResponse.ofText("Elicitation not supported or error: " + e.getMessage());
        }
    }

    /**
     * Requests elicitation with the SEP-1330 enum schema variants.
     *
     * @param elicitation the elicitation API
     * @return the tool response
     */
    @Tool(
            name = "test_elicitation_sep1330_enums",
            description = "Test elicitation with enum schema improvements (SEP-1330)")
    public ToolResponse testElicitationSep1330Enums(Elicitation elicitation) {
        if (!elicitation.isSupported()) {
            return ToolResponse.ofText("Elicitation not supported");
        }
        try {
            ElicitationResponse response = elicitation
                    .requestBuilder()
                    .setMessage("Please review and update the form fields with defaults")
                    .addSchemaProperty(
                            "untitledSingle",
                            () -> Map.of(
                                    "type",
                                    "string",
                                    "description",
                                    "Select one option",
                                    "enum",
                                    List.of("option1", "option2", "option3")))
                    .addSchemaProperty(
                            "titledSingle",
                            () -> Map.of(
                                    "type",
                                    "string",
                                    "description",
                                    "Select one option with titles",
                                    "enum",
                                    List.of("value1", "value2", "value3"),
                                    "enumNames",
                                    List.of("First Option", "Second Option", "Third Option")))
                    .addSchemaProperty(
                            "legacyEnum",
                            () -> Map.of(
                                    "type",
                                    "string",
                                    "description",
                                    "Select one option (legacy)",
                                    "enum",
                                    List.of("opt1", "opt2", "opt3"),
                                    "enumNames",
                                    List.of("Option One", "Option Two", "Option Three")))
                    .addSchemaProperty(
                            "untitledMulti",
                            () -> Map.of(
                                    "type",
                                    "array",
                                    "description",
                                    "Select multiple options",
                                    "items",
                                    Map.of("type", "string", "enum", List.of("option1", "option2", "option3")),
                                    "minItems",
                                    1,
                                    "maxItems",
                                    3))
                    .addSchemaProperty(
                            "titledMulti",
                            () -> Map.of(
                                    "type",
                                    "array",
                                    "description",
                                    "Select multiple options with titles",
                                    "items",
                                    Map.of(
                                            "type",
                                            "string",
                                            "enum",
                                            List.of("value1", "value2", "value3"),
                                            "enumNames",
                                            List.of("First Choice", "Second Choice", "Third Choice")),
                                    "minItems",
                                    1,
                                    "maxItems",
                                    3))
                    .build()
                    .sendAndAwait();
            return ToolResponse.ofText("Elicitation completed: " + describe(response));
        } catch (RuntimeException e) {
            return ToolResponse.ofText("Elicitation not supported or error: " + e.getMessage());
        }
    }

    /**
     * Unconditionally uses sampling so that a client which did not declare the capability gets a
     * {@code MissingRequiredClientCapability} error (SEP-2575).
     *
     * @param sampling the sampling API
     * @return the tool response
     */
    @Tool(
            name = "test_missing_capability",
            description = "Requires the sampling client capability without checking whether it was declared")
    public ToolResponse testMissingCapability(Sampling sampling) {
        SamplingResponse response = sampling.requestBuilder()
                .addMessage(userMessage("Capability probe"))
                .setMaxTokens(10)
                .build()
                .sendAndAwait();
        return ToolResponse.ofText("Sampling succeeded: " + describe(response));
    }

    static McpSamplingMessage userMessage(String text) {
        return new McpSamplingMessage("user", Map.of("type", "text", "text", text));
    }

    static String describe(SamplingResponse response) {
        if (response == null) {
            return "No response";
        }
        return "content=" + response.content() + ", model=" + response.model() + ", stopReason="
                + response.stopReason();
    }

    static String describe(ElicitationResponse response) {
        if (response == null) {
            return "no response";
        }
        return "action=" + response.action() + ", content=" + response.content().asMap();
    }

    private static void pause() {
        try {
            Thread.sleep(50);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
