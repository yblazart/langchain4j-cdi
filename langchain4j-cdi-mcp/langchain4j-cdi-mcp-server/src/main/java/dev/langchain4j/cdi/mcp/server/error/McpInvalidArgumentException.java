package dev.langchain4j.cdi.mcp.server.error;

/**
 * Raised when a client-supplied argument of a {@code tools/call}, {@code prompts/get} or {@code resources/read} cannot
 * be bound to its method parameter: a JSON value of the wrong type, a number that is not an integer or does not fit, or
 * an enum value the schema does not advertise.
 *
 * <p>The message names the argument and the expected type, and never a Java class name. It is answered as {@code -32602
 * Invalid params}, except for {@code tools/call} in the MCP 2026-07-28 era, where input validation errors are tool
 * execution errors and are returned as a {@code CallToolResult} with {@code isError: true}.
 */
public class McpInvalidArgumentException extends McpException {

    /** The wire name of the rejected argument. */
    private final String argumentName;

    /**
     * Creates the exception.
     *
     * @param requestId the JSON-RPC request ID that triggered the error
     * @param argumentName the wire name of the rejected argument
     * @param detail what was expected and what was received, for example {@code expected integer, got string "5"}
     */
    public McpInvalidArgumentException(Object requestId, String argumentName, String detail) {
        super(requestId, McpErrorCode.INVALID_PARAMS, "Invalid argument '" + argumentName + "': " + detail);
        this.argumentName = argumentName;
    }

    /**
     * Returns the wire name of the rejected argument.
     *
     * @return the argument name
     */
    public String getArgumentName() {
        return argumentName;
    }
}
