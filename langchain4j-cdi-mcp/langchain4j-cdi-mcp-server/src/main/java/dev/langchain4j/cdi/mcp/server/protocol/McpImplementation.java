package dev.langchain4j.cdi.mcp.server.protocol;

public class McpImplementation {

    private String name;
    private String version;

    public McpImplementation() {}

    public McpImplementation(String name, String version) {
        this.name = name;
        this.version = version;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getVersion() {
        return version;
    }

    public void setVersion(String version) {
        this.version = version;
    }
}
