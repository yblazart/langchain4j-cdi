package dev.langchain4j.cdi.mcp.server.protocol;

public class JsonRpcResponse {

    private String jsonrpc = "2.0";
    private String id;
    private Object result;
    private JsonRpcError error;

    public JsonRpcResponse() {}

    public static JsonRpcResponse success(String id, Object result) {
        JsonRpcResponse response = new JsonRpcResponse();
        response.id = id;
        response.result = result;
        return response;
    }

    public static JsonRpcResponse error(String id, JsonRpcError error) {
        JsonRpcResponse response = new JsonRpcResponse();
        response.id = id;
        response.error = error;
        return response;
    }

    public String getJsonrpc() {
        return jsonrpc;
    }

    public void setJsonrpc(String jsonrpc) {
        this.jsonrpc = jsonrpc;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public Object getResult() {
        return result;
    }

    public void setResult(Object result) {
        this.result = result;
    }

    public JsonRpcError getError() {
        return error;
    }

    public void setError(JsonRpcError error) {
        this.error = error;
    }
}
