package dev.langchain4j.cdi.mcp.integrationtests;

import dev.langchain4j.cdi.mcp.server.McpResource;
import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class ConfigResource {

    @McpResource(
            uri = "config://app",
            name = "Application Config",
            description = "Current application configuration",
            mimeType = "application/json")
    public String getConfig() {
        return "{\"version\":\"1.0\",\"env\":\"test\"}";
    }

    @McpResource(uri = "data://status", name = "Status", description = "Server status")
    public String getStatus() {
        return "running";
    }
}
