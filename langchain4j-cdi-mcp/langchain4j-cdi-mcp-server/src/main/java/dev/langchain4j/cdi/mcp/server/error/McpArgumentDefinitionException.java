package dev.langchain4j.cdi.mcp.server.error;

/**
 * Thrown at tool or prompt registration when a {@code @ToolArg(defaultValue = …)} or {@code @PromptArg(defaultValue =
 * …)} cannot be converted to the type of the parameter it annotates.
 *
 * <p>This is a deployment-time failure on purpose: a default that does not convert would otherwise only surface on the
 * first call that omits the argument, as an internal error the client cannot act on.
 */
public class McpArgumentDefinitionException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    /**
     * Creates the exception.
     *
     * @param message a description naming the tool or prompt, the parameter and the rejected default
     */
    public McpArgumentDefinitionException(String message) {
        super(message);
    }
}
