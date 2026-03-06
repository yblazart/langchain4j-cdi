package dev.langchain4j.cdi.mcp.server.error;

public class McpException extends RuntimeException {

    private final String requestId;
    private final McpErrorCode errorCode;

    public McpException(String requestId, McpErrorCode errorCode, String message) {
        super(message);
        this.requestId = requestId;
        this.errorCode = errorCode;
    }

    public String getRequestId() {
        return requestId;
    }

    public McpErrorCode getErrorCode() {
        return errorCode;
    }
}
