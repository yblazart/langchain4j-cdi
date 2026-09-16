package dev.langchain4j.cdi.mcp.server.api;

import dev.langchain4j.cdi.mcp.server.transport.McpClientRequester;

/** Implementation of {@link McpInteractions} that delegates batches to {@link McpClientRequester#requestBatch}. */
public class CdiInteractions implements McpInteractions {

    private final McpClientRequester requester;

    /**
     * Creates a new interactions wrapper.
     *
     * @param requester the client requester used to send the batch
     */
    public CdiInteractions(McpClientRequester requester) {
        this.requester = requester;
    }

    @Override
    public Batch batch() {
        return new CdiBatch(requester);
    }
}
