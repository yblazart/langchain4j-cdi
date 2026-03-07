package dev.langchain4j.cdi.mcp.server.protocol;

import java.util.List;

public class McpResourceReadResult {

    private List<McpResourceContent> contents;

    public McpResourceReadResult() {}

    public McpResourceReadResult(List<McpResourceContent> contents) {
        this.contents = contents;
    }

    public static McpResourceReadResult text(String uri, String mimeType, String text) {
        return new McpResourceReadResult(List.of(new McpResourceContent(uri, mimeType, text, null)));
    }

    public static McpResourceReadResult blob(String uri, String mimeType, String base64) {
        return new McpResourceReadResult(List.of(new McpResourceContent(uri, mimeType, null, base64)));
    }

    public List<McpResourceContent> getContents() {
        return contents;
    }

    public void setContents(List<McpResourceContent> contents) {
        this.contents = contents;
    }

    public static class McpResourceContent {

        private String uri;
        private String mimeType;
        private String text;
        private String blob;

        public McpResourceContent() {}

        public McpResourceContent(String uri, String mimeType, String text, String blob) {
            this.uri = uri;
            this.mimeType = mimeType;
            this.text = text;
            this.blob = blob;
        }

        public String getUri() {
            return uri;
        }

        public void setUri(String uri) {
            this.uri = uri;
        }

        public String getMimeType() {
            return mimeType;
        }

        public void setMimeType(String mimeType) {
            this.mimeType = mimeType;
        }

        public String getText() {
            return text;
        }

        public void setText(String text) {
            this.text = text;
        }

        public String getBlob() {
            return blob;
        }

        public void setBlob(String blob) {
            this.blob = blob;
        }
    }
}
