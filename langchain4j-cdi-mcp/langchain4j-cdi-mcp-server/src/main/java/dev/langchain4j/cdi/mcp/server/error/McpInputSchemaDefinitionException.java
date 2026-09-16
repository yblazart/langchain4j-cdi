package dev.langchain4j.cdi.mcp.server.error;

/**
 * Thrown at tool registration when a {@code @McpInputSchema}-supplied document violates one of the three consistency
 * rules enforced between the hand-written schema and the method it describes.
 *
 * <p>This is a deployment-time failure on purpose: a schema that lies about its method — advertising a required
 * argument that can never bind, or validating a header designation the client was never told about — would otherwise
 * only surface as a confusing failure at call time, or not at all.
 */
public class McpInputSchemaDefinitionException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    /**
     * Creates the exception.
     *
     * @param message a description naming the tool and the violated rule
     */
    public McpInputSchemaDefinitionException(String message) {
        super(message);
    }
}
