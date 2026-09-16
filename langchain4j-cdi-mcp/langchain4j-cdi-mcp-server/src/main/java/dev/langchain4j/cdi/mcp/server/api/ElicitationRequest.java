package dev.langchain4j.cdi.mcp.server.api;

import java.time.Duration;
import java.util.Map;

/** Represents a user elicitation request sent to the MCP client. */
public interface ElicitationRequest {

    /** Schema descriptor for a single primitive property in the elicitation form. */
    interface PrimitiveSchema {
        Object asJson();
    }

    String message();

    Map<String, PrimitiveSchema> requestedSchema();

    <T> T send();

    ElicitationResponse sendAndAwait();

    /** Builder for constructing {@link ElicitationRequest} instances. */
    interface Builder {

        Builder setMessage(String message);

        Builder addSchemaProperty(String name, PrimitiveSchema schema);

        Builder setTimeout(Duration timeout);

        /**
         * Sets the key this request is emitted under in {@code inputRequests}, and the key its answer is looked up by
         * in {@code inputResponses} (MRTR, SEP-2322). When no key is set, the server assigns one by call order
         * ({@code input-0}, {@code input-1}, …), exactly as before this method existed.
         *
         * @param key the key to use; must not be blank
         * @return this builder
         */
        Builder setKey(String key);

        /**
         * Builds the request.
         *
         * @return the built request
         * @throws IllegalArgumentException if {@link #setKey(String)} was called with a blank key
         */
        ElicitationRequest build();
    }
}
