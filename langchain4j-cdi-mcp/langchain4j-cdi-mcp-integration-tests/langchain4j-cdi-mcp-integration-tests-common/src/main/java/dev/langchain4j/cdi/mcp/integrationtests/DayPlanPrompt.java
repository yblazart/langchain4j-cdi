package dev.langchain4j.cdi.mcp.integrationtests;

import jakarta.enterprise.context.ApplicationScoped;
import org.mcpjava.server.prompts.Prompt;
import org.mcpjava.server.prompts.PromptArg;

/** MCP prompt with a non-string argument that has a default (langchain4j-cdi#298). */
@ApplicationScoped
public class DayPlanPrompt {

    /** Creates a new instance. */
    public DayPlanPrompt() {}

    /**
     * Produces a planning prompt. Prompt arguments are strings on the wire, so {@code hours} is parsed from one.
     *
     * @param hours the hours available, defaults to 8
     * @return the planning prompt
     */
    @Prompt(name = McpTestConstants.PLAN_DAY, description = "Plan my day")
    public String planDay(@PromptArg(name = "hours", description = "Hours available", defaultValue = "8") int hours) {
        return "hours=" + hours;
    }
}
