package dev.langchain4j.cdi.mcp.server.api;

/**
 * Lets a {@code @Tool}/{@code @Prompt}/{@code @Resource} method declare several client interactions (elicitation,
 * sampling, roots) as one batch and await all of their answers together (MRTR, SEP-2322).
 *
 * <p>{@code sendAndAwait()} on {@link Elicitation}, {@link Sampling} and {@link Roots} blocks, and the calling code
 * depends on its answer, so the framework cannot "keep going" to discover the next interaction the method would ask for
 * — with a stateless (REPLAY) client, a missing answer would surface one interaction at a time, one round trip per
 * interaction. A batch declares every request <em>before</em> waiting on any of them, so a client can answer all of
 * them in one round trip.
 *
 * <p>Injected as a method parameter, like {@link Elicitation}, {@link Sampling} and {@link Roots}.
 *
 * <pre>{@code
 * @Tool
 * public String plan(McpInteractions interactions, Elicitation elicitation, Sampling sampling, Roots roots) {
 *     McpInteractionResults answers = interactions.batch()
 *             .elicit("user_name", elicitation.requestBuilder().setMessage("Your name?").build())
 *             .sample("summary", sampling.requestBuilder().addMessage(msg).build())
 *             .roots("roots")
 *             .awaitAll();
 *     String name = answers.elicitation("user_name").content().getString("name");
 *     ...
 * }
 * }</pre>
 */
public interface McpInteractions {

    /**
     * Starts a new batch of client interactions.
     *
     * @return a new, empty batch
     */
    Batch batch();

    /** Declares the members of a batch of client interactions, all sent and awaited together. */
    interface Batch {

        /**
         * Adds an elicitation to the batch, under the given key.
         *
         * @param key the key to publish the request under, and to read its answer back with; must not be blank and must
         *     be unique within this batch
         * @param request the request, built via {@link Elicitation#requestBuilder()}; if it carries its own
         *     {@code setKey}, that key must equal {@code key} — the batch key is authoritative
         * @return this batch
         */
        Batch elicit(String key, ElicitationRequest request);

        /**
         * Adds a sampling request to the batch, under the given key.
         *
         * @param key the key to publish the request under, and to read its answer back with; must not be blank and must
         *     be unique within this batch
         * @param request the request, built via {@link Sampling#requestBuilder()}; if it carries its own
         *     {@code setKey}, that key must equal {@code key} — the batch key is authoritative
         * @return this batch
         */
        Batch sample(String key, SamplingRequest request);

        /**
         * Adds a roots listing to the batch, under the given key.
         *
         * @param key the key to publish the request under, and to read its answer back with; must not be blank and must
         *     be unique within this batch
         * @return this batch
         */
        Batch roots(String key);

        /**
         * Sends the batch and awaits every member's answer.
         *
         * <p>Every rule below is enforced before anything is sent: a duplicate key within the batch, an empty batch, a
         * request whose own key conflicts with its batch key, and a client capability missing for any member all fail
         * the whole batch up front, the same way a single interaction fails today.
         *
         * @return the collected answers
         * @throws IllegalArgumentException if two members share a key, or a member's own key conflicts with its batch
         *     key
         * @throws IllegalStateException if the batch is empty
         */
        McpInteractionResults awaitAll();
    }
}
