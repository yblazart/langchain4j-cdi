package dev.langchain4j.cdi.mcp.server.error;

/** Runtime exception carrying a JSON-RPC error code and the originating request ID. */
public class McpException extends RuntimeException {

    /** The JSON-RPC request ID that triggered the error. */
    private final Object requestId;

    /** The MCP error code describing the failure category. */
    private final McpErrorCode errorCode;

    /** The HTTP status to use when this error is returned over Streamable HTTP. */
    private final int httpStatus;

    /** Optional JSON-RPC error {@code data}, or {@code null}. */
    private final Object data;

    /**
     * Creates an MCP exception with the given request context and message.
     *
     * @param requestId the JSON-RPC request ID that triggered the error
     * @param errorCode the MCP error code describing the failure
     * @param message a human-readable error description
     */
    public McpException(Object requestId, McpErrorCode errorCode, String message) {
        this(requestId, errorCode, message, 200, null);
    }

    /**
     * Creates an MCP exception with request context, error code, message, HTTP status, and optional error data.
     *
     * @param requestId the JSON-RPC request ID that triggered the error
     * @param errorCode the MCP error code describing the failure
     * @param message a human-readable error description
     * @param httpStatus the HTTP status to use when this error is returned over Streamable HTTP
     * @param data optional JSON-RPC error {@code data}, or {@code null}
     */
    public McpException(Object requestId, McpErrorCode errorCode, String message, int httpStatus, Object data) {
        super(message);
        this.requestId = requestId;
        this.errorCode = errorCode;
        this.httpStatus = httpStatus;
        this.data = data;
    }

    /**
     * Returns the JSON-RPC request ID associated with this error.
     *
     * @return the request ID
     */
    public Object getRequestId() {
        return requestId;
    }

    /**
     * Returns the MCP error code describing the failure.
     *
     * @return the error code
     */
    public McpErrorCode getErrorCode() {
        return errorCode;
    }

    /**
     * Returns the HTTP status to use when this error is returned over Streamable HTTP.
     *
     * @return the HTTP status
     */
    public int getHttpStatus() {
        return httpStatus;
    }

    /**
     * Returns the optional JSON-RPC error {@code data}, or {@code null}.
     *
     * @return the error data, or {@code null}
     */
    public Object getData() {
        return data;
    }
}
