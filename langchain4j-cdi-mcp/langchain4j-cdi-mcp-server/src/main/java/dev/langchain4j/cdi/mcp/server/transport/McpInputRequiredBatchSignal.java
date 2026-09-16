package dev.langchain4j.cdi.mcp.server.transport;

import java.util.List;

/**
 * Control-flow signal raised by a stateless client requester (MRTR REPLAY mode) when a batch of client interactions has
 * one or more members whose answer is not yet known. Unlike {@link McpInputRequiredSignal}, which carries a single
 * pending request, this carries every member of the batch still missing an answer — so the client is told about all of
 * them in one {@code input_required} result, never just the first. Caught by the modern handler and turned into an
 * {@code input_required} result listing every {@link #missing()} request.
 */
public final class McpInputRequiredBatchSignal extends RuntimeException {

    private final transient List<McpInputRequiredSignal> missing;

    /**
     * Creates a signal carrying every batch member still missing an answer.
     *
     * @param missing the missing requests, in the order the batch declared them; must not be empty
     */
    public McpInputRequiredBatchSignal(List<McpInputRequiredSignal> missing) {
        super("Client input required for " + missing.size() + " batched request(s)", null, false, false);
        this.missing = List.copyOf(missing);
    }

    /**
     * Returns every batch member still missing an answer.
     *
     * @return the missing requests, in the order the batch declared them
     */
    public List<McpInputRequiredSignal> missing() {
        return missing;
    }
}
