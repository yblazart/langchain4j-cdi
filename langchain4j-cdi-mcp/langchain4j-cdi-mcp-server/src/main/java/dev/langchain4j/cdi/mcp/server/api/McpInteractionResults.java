package dev.langchain4j.cdi.mcp.server.api;

import dev.langchain4j.cdi.mcp.server.protocol.McpRoot;
import java.util.List;

/**
 * Holds the answers collected by {@link McpInteractions.Batch#awaitAll()}, keyed exactly as the batch declared them.
 */
public interface McpInteractionResults {

    /**
     * Returns the elicitation answer collected under the given key.
     *
     * @param key the key the batch declared this interaction under
     * @return the elicitation answer
     * @throws IllegalArgumentException if {@code key} is unknown, or was declared as a different kind of interaction
     */
    ElicitationResponse elicitation(String key);

    /**
     * Returns the sampling answer collected under the given key.
     *
     * @param key the key the batch declared this interaction under
     * @return the sampling answer
     * @throws IllegalArgumentException if {@code key} is unknown, or was declared as a different kind of interaction
     */
    SamplingResponse sampling(String key);

    /**
     * Returns the roots answer collected under the given key.
     *
     * @param key the key the batch declared this interaction under
     * @return the list of roots
     * @throws IllegalArgumentException if {@code key} is unknown, or was declared as a different kind of interaction
     */
    List<McpRoot> roots(String key);
}
