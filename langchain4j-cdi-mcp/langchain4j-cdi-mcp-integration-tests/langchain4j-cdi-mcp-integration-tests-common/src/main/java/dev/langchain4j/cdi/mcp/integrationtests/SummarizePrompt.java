package dev.langchain4j.cdi.mcp.integrationtests;

import dev.langchain4j.cdi.mcp.server.McpPrompt;
import dev.langchain4j.cdi.mcp.server.McpPromptArg;
import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class SummarizePrompt {

    @McpPrompt("Summarize the given text")
    public String summarize(@McpPromptArg("The text to summarize") String text) {
        return "Please summarize the following text:\n\n" + text;
    }
}
