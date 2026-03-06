package dev.langchain4j.cdi.mcp.server.protocol;

public class McpInitializeResult {

    private String protocolVersion;
    private McpServerCapabilities capabilities;
    private McpImplementation serverInfo;

    public McpInitializeResult() {}

    public McpInitializeResult(
            String protocolVersion, McpServerCapabilities capabilities, McpImplementation serverInfo) {
        this.protocolVersion = protocolVersion;
        this.capabilities = capabilities;
        this.serverInfo = serverInfo;
    }

    public String getProtocolVersion() {
        return protocolVersion;
    }

    public void setProtocolVersion(String protocolVersion) {
        this.protocolVersion = protocolVersion;
    }

    public McpServerCapabilities getCapabilities() {
        return capabilities;
    }

    public void setCapabilities(McpServerCapabilities capabilities) {
        this.capabilities = capabilities;
    }

    public McpImplementation getServerInfo() {
        return serverInfo;
    }

    public void setServerInfo(McpImplementation serverInfo) {
        this.serverInfo = serverInfo;
    }
}
