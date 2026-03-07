package dev.langchain4j.cdi.mcp.server.protocol;

import java.util.List;

public class McpPromptWireFormat {

    private String name;
    private String description;
    private List<McpPromptArgWireFormat> arguments;

    public McpPromptWireFormat() {}

    public McpPromptWireFormat(String name, String description, List<McpPromptArgWireFormat> arguments) {
        this.name = name;
        this.description = description;
        this.arguments = arguments;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public List<McpPromptArgWireFormat> getArguments() {
        return arguments;
    }

    public void setArguments(List<McpPromptArgWireFormat> arguments) {
        this.arguments = arguments;
    }

    public static class McpPromptArgWireFormat {

        private String name;
        private String description;
        private boolean required;

        public McpPromptArgWireFormat() {}

        public McpPromptArgWireFormat(String name, String description, boolean required) {
            this.name = name;
            this.description = description;
            this.required = required;
        }

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public String getDescription() {
            return description;
        }

        public void setDescription(String description) {
            this.description = description;
        }

        public boolean isRequired() {
            return required;
        }

        public void setRequired(boolean required) {
            this.required = required;
        }
    }
}
