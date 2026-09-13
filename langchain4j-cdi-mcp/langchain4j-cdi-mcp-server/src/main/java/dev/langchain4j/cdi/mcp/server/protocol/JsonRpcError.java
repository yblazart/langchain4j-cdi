package dev.langchain4j.cdi.mcp.server.protocol;

/** Represents a JSON-RPC 2.0 error object. */
public class JsonRpcError {

    private int code;
    private String message;
    private Object data;

    /** Default constructor for JSON-B deserialization. */
    public JsonRpcError() {}

    /**
     * Creates an error with the given code and message.
     *
     * @param code the error code
     * @param message the error message
     */
    public JsonRpcError(int code, String message) {
        this.code = code;
        this.message = message;
    }

    /**
     * Creates an error with the given code, message, and optional data.
     *
     * @param code the error code
     * @param message the error message
     * @param data optional error data, or {@code null}
     */
    public JsonRpcError(int code, String message, Object data) {
        this.code = code;
        this.message = message;
        this.data = data;
    }

    /**
     * Returns the error code.
     *
     * @return the error code
     */
    public int getCode() {
        return code;
    }

    /**
     * Sets the error code.
     *
     * @param code the error code
     */
    public void setCode(int code) {
        this.code = code;
    }

    /**
     * Returns the error message.
     *
     * @return the error message
     */
    public String getMessage() {
        return message;
    }

    /**
     * Sets the error message.
     *
     * @param message the error message
     */
    public void setMessage(String message) {
        this.message = message;
    }

    /**
     * Returns the optional error data.
     *
     * @return the error data, or {@code null}
     */
    public Object getData() {
        return data;
    }

    /**
     * Sets the optional error data.
     *
     * @param data the error data, or {@code null}
     */
    public void setData(Object data) {
        this.data = data;
    }
}
