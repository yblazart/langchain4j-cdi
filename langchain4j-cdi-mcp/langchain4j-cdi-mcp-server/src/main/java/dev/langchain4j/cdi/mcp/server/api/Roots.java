package dev.langchain4j.cdi.mcp.server.api;

import dev.langchain4j.cdi.mcp.server.protocol.McpRoot;
import java.util.List;

/** Provides access to the client's file system roots. */
public interface Roots {

    boolean isSupported();

    <T> T list();

    List<McpRoot> listAndAwait();

    /**
     * Requests the client's file system roots, using the given key (MRTR, SEP-2322): the key the request is emitted
     * under in {@code inputRequests}, and the key its answer is looked up by in {@code inputResponses}.
     *
     * @param key the key to use; must not be blank
     * @return the list of roots
     * @throws IllegalArgumentException if {@code key} is blank
     */
    List<McpRoot> listAndAwait(String key);
}
