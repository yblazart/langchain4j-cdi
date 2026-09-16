package dev.langchain4j.cdi.mcp.server.api;

/**
 * Holds the result of an LLM sampling request.
 *
 * @param content the sampled content block, as sent by the client: a {@code jakarta.json.JsonObject} for a structured
 *     content block (itself a {@code Map<String, JsonValue>}, so {@code getString("text")} reads the answer), a
 *     {@link String} when the client sent a bare string, or {@code null} when the client sent none
 * @param model the identifier of the model that produced the answer, or {@code null}
 * @param role the role of the sampled message ({@code "assistant"}), or {@code null}
 * @param stopReason why the model stopped generating, or {@code null}
 */
public record SamplingResponse(Object content, String model, Object role, String stopReason) {}
