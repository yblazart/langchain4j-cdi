package dev.langchain4j.cdi.mcp.server.error;

/**
 * Exception thrown when an MCP session cannot be found or has expired.
 *
 * <p>The Streamable HTTP transport requires an unknown or terminated session id to be answered {@code 404 Not Found},
 * which is the status this exception carries by default; a request that carries no session id at all is a {@code 400
 * Bad Request} instead.
 */
public class McpSessionException extends McpException {

    /** HTTP status answered for a request bearing an unknown or terminated session id. */
    public static final int NOT_FOUND = 404;

    /** HTTP status answered for a request that carries no session id at all. */
    public static final int BAD_REQUEST = 400;

    /**
     * Creates a session exception for an unknown or terminated session ({@code 404 Not Found}).
     *
     * @param requestId the JSON-RPC request ID that triggered the error
     * @param message a human-readable description of the session error
     */
    public McpSessionException(Object requestId, String message) {
        this(requestId, message, NOT_FOUND);
    }

    /**
     * Creates a session exception with an explicit HTTP status.
     *
     * @param requestId the JSON-RPC request ID that triggered the error
     * @param message a human-readable description of the session error
     * @param httpStatus the HTTP status to answer over Streamable HTTP
     */
    public McpSessionException(Object requestId, String message, int httpStatus) {
        super(requestId, McpErrorCode.SESSION_NOT_FOUND, message, httpStatus, null);
    }
}
