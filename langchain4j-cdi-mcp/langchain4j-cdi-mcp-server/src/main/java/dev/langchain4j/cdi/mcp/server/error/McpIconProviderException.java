package dev.langchain4j.cdi.mcp.server.error;

/**
 * Thrown at feature registration when the {@code org.mcpjava.server.IconProvider} named by an
 * {@code org.mcpjava.server.Icons} annotation cannot be resolved, is ambiguous, cannot be instantiated, fails while
 * producing icons, or returns an icon without a {@code src}.
 *
 * <p>This is a deployment-time failure on purpose. Icons are optional everywhere in the MCP schema, so a provider that
 * blew up at {@code tools/list} time would simply make the icons disappear from the client's catalogue with nothing the
 * server author could see; the message therefore names both the feature and the offending provider class.
 */
public class McpIconProviderException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    /**
     * Creates the exception.
     *
     * @param message a description naming the feature type, the feature name and the provider class
     */
    public McpIconProviderException(String message) {
        super(message);
    }

    /**
     * Creates the exception with the underlying failure.
     *
     * @param message a description naming the feature type, the feature name and the provider class
     * @param cause the failure that prevented the provider from being resolved or invoked
     */
    public McpIconProviderException(String message, Throwable cause) {
        super(message, cause);
    }
}
