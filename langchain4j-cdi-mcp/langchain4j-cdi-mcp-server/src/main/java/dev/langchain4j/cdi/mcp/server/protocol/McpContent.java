package dev.langchain4j.cdi.mcp.server.protocol;

import jakarta.json.bind.annotation.JsonbTransient;

/**
 * Represents content in MCP protocol responses. Supports text, image (base64), and embedded resource types. Use the
 * static factory methods {@link #text(String)}, {@link #image(String, String)}, and {@link #resource(String, String,
 * String)} to create instances.
 */
public class McpContent {

    private String type;
    private String text;
    private String data;
    private String mimeType;
    private String uri;

    public McpContent() {}

    public McpContent(String type, String text) {
        this.type = type;
        this.text = text;
    }

    public static McpContent text(String text) {
        return new McpContent("text", text);
    }

    public static McpContent image(String base64Data, String mimeType) {
        McpContent content = new McpContent();
        content.type = "image";
        content.data = base64Data;
        content.mimeType = mimeType;
        return content;
    }

    public static McpContent resource(String uri, String mimeType, String text) {
        McpContent content = new McpContent();
        content.type = "resource";
        content.uri = uri;
        content.mimeType = mimeType;
        content.text = text;
        return content;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getText() {
        return text;
    }

    public void setText(String text) {
        this.text = text;
    }

    public String getData() {
        return data;
    }

    public void setData(String data) {
        this.data = data;
    }

    public String getMimeType() {
        return mimeType;
    }

    public void setMimeType(String mimeType) {
        this.mimeType = mimeType;
    }

    public String getUri() {
        return uri;
    }

    public void setUri(String uri) {
        this.uri = uri;
    }

    @JsonbTransient
    public boolean isTextContent() {
        return "text".equals(type);
    }

    @JsonbTransient
    public boolean isImageContent() {
        return "image".equals(type);
    }

    @JsonbTransient
    public boolean isResourceContent() {
        return "resource".equals(type);
    }
}
