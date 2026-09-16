package dev.langchain4j.cdi.mcp.server.transport;

import java.util.Map;

/**
 * A single member of a batch of client interactions (MRTR, SEP-2322), declared but not yet sent.
 *
 * @param key the key to publish the request under, and to read its answer back with; never {@code null}
 * @param method JSON-RPC method, e.g. {@code elicitation/create}
 * @param params request params
 */
public record BatchRequestSpec(String key, String method, Map<String, Object> params) {}
