package dev.langchain4j.cdi.mcp.integrationtests;

public final class McpTestRequests {

    private McpTestRequests() {}

    public static String initializeRequest() {
        return """
                {"jsonrpc":"2.0","id":"1","method":"initialize","params":{
                    "protocolVersion":"2025-03-26",
                    "capabilities":{},
                    "clientInfo":{"name":"test-client","version":"1.0"}
                }}""";
    }

    public static String initializedNotification() {
        return """
                {"jsonrpc":"2.0","method":"notifications/initialized","params":{}}""";
    }

    public static String toolsListRequest() {
        return """
                {"jsonrpc":"2.0","id":"2","method":"tools/list","params":{}}""";
    }

    public static String toolsCallRequest(String toolName, String argsJson) {
        return """
                {"jsonrpc":"2.0","id":"3","method":"tools/call","params":{
                    "name":"%s",
                    "arguments":%s
                }}""".formatted(toolName, argsJson);
    }

    public static String pingRequest() {
        return """
                {"jsonrpc":"2.0","id":"4","method":"ping","params":{}}""";
    }
}
