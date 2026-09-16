package dev.langchain4j.cdi.mcp.conformance;

import jakarta.enterprise.context.ApplicationScoped;
import org.mcpjava.server.Role;
import org.mcpjava.server.content.EmbeddedResource;
import org.mcpjava.server.content.ImageContent;
import org.mcpjava.server.content.TextContent;
import org.mcpjava.server.prompts.Prompt;
import org.mcpjava.server.prompts.PromptArg;
import org.mcpjava.server.prompts.PromptResponse;

/** Prompts expected by the official MCP conformance suite. */
@ApplicationScoped
public class ConformancePrompts {

    /** Creates a new instance. */
    public ConformancePrompts() {}

    /**
     * Prompt without arguments.
     *
     * @return the prompt messages
     */
    @Prompt(
            name = "test_simple_prompt",
            title = "Simple Test Prompt",
            description = "A simple prompt without arguments")
    public PromptResponse testSimplePrompt() {
        return PromptResponse.builder()
                .addMessage(Role.USER, TextContent.of("This is a simple prompt for testing."))
                .build();
    }

    /**
     * Prompt with two required arguments.
     *
     * @param arg1 first test argument
     * @param arg2 second test argument
     * @return the prompt messages
     */
    @Prompt(
            name = "test_prompt_with_arguments",
            title = "Prompt With Arguments",
            description = "A prompt with required arguments")
    public PromptResponse testPromptWithArguments(
            @PromptArg(name = "arg1", description = "First test argument") String arg1,
            @PromptArg(name = "arg2", description = "Second test argument") String arg2) {
        return PromptResponse.builder()
                .addMessage(
                        Role.USER, TextContent.of("Prompt with arguments: arg1='" + arg1 + "', arg2='" + arg2 + "'"))
                .build();
    }

    /**
     * Prompt embedding a resource.
     *
     * @param resourceUri URI of the resource to embed
     * @return the prompt messages
     */
    @Prompt(
            name = "test_prompt_with_embedded_resource",
            title = "Prompt With Embedded Resource",
            description = "A prompt that includes an embedded resource")
    public PromptResponse testPromptWithEmbeddedResource(
            @PromptArg(name = "resourceUri", description = "URI of the resource to embed") String resourceUri) {
        return PromptResponse.builder()
                .addMessage(
                        Role.USER,
                        EmbeddedResource.builder("Embedded resource content for testing.", resourceUri)
                                .setMimeType("text/plain")
                                .build())
                .addMessage(Role.USER, TextContent.of("Please process the embedded resource above."))
                .build();
    }

    /**
     * Prompt including image content.
     *
     * @return the prompt messages
     */
    @Prompt(
            name = "test_prompt_with_image",
            title = "Prompt With Image",
            description = "A prompt that includes image content")
    public PromptResponse testPromptWithImage() {
        return PromptResponse.builder()
                .addMessage(Role.USER, ImageContent.of(ConformanceFixtures.testImage(), "image/png"))
                .addMessage(Role.USER, TextContent.of("Please analyze the image above."))
                .build();
    }
}
