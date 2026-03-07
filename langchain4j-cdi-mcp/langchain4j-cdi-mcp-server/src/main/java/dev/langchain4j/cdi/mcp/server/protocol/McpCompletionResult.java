package dev.langchain4j.cdi.mcp.server.protocol;

import java.util.List;

public class McpCompletionResult {

    private Completion completion;

    public McpCompletionResult() {}

    public McpCompletionResult(List<String> values, boolean hasMore, int total) {
        this.completion = new Completion(values, hasMore, total);
    }

    public static McpCompletionResult empty() {
        return new McpCompletionResult(List.of(), false, 0);
    }

    public static McpCompletionResult of(List<String> values) {
        return new McpCompletionResult(values, false, values.size());
    }

    public Completion getCompletion() {
        return completion;
    }

    public void setCompletion(Completion completion) {
        this.completion = completion;
    }

    public static class Completion {

        private List<String> values;
        private boolean hasMore;
        private int total;

        public Completion() {}

        public Completion(List<String> values, boolean hasMore, int total) {
            this.values = values;
            this.hasMore = hasMore;
            this.total = total;
        }

        public List<String> getValues() {
            return values;
        }

        public void setValues(List<String> values) {
            this.values = values;
        }

        public boolean isHasMore() {
            return hasMore;
        }

        public void setHasMore(boolean hasMore) {
            this.hasMore = hasMore;
        }

        public int getTotal() {
            return total;
        }

        public void setTotal(int total) {
            this.total = total;
        }
    }
}
