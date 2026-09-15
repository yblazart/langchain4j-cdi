package dev.langchain4j.cdi.mcp.server.error;

/**
 * Thrown at tool registration when an {@code x-mcp-header} argument designation violates one of the four SEP-2243
 * constraints.
 *
 * <p>This is a deployment-time failure on purpose. SEP-2243 requires a client to <em>exclude</em> a tool carrying an
 * invalid designation from {@code tools/list}, so the tool would otherwise disappear from the client's catalogue with
 * no error raised anywhere the server author could see.
 */
public class McpHeaderArgDefinitionException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    /**
     * Creates the exception.
     *
     * @param message a description naming the tool, the parameter and the violated rule
     */
    public McpHeaderArgDefinitionException(String message) {
        super(message);
    }
}
