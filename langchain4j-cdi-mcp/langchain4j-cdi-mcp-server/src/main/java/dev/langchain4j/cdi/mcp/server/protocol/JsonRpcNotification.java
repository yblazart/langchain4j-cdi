package dev.langchain4j.cdi.mcp.server.protocol;

public class JsonRpcNotification {

    private String jsonrpc = "2.0";
    private String method;

    public JsonRpcNotification() {}

    public JsonRpcNotification(String method) {
        this.method = method;
    }

    public static JsonRpcNotification toolsListChanged() {
        return new JsonRpcNotification("notifications/tools/list_changed");
    }

    public String getJsonrpc() {
        return jsonrpc;
    }

    public void setJsonrpc(String jsonrpc) {
        this.jsonrpc = jsonrpc;
    }

    public String getMethod() {
        return method;
    }

    public void setMethod(String method) {
        this.method = method;
    }
}
