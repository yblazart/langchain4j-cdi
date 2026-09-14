package dev.langchain4j.cdi.mcp.server.transport;

import dev.langchain4j.cdi.mcp.server.error.McpException;
import dev.langchain4j.cdi.mcp.server.error.McpProtocolErrors;
import jakarta.json.Json;
import jakarta.json.JsonArray;
import jakarta.json.JsonNumber;
import jakarta.json.JsonObject;
import jakarta.json.JsonObjectBuilder;
import jakarta.json.JsonReader;
import jakarta.json.JsonString;
import jakarta.json.JsonValue;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/** Integrity-protected, expiring {@code requestState} bound to the originating request. */
public final class McpRequestStateCodec {

    private static final String HMAC = "HmacSHA256";
    private static final Base64.Encoder ENCODER = Base64.getUrlEncoder().withoutPadding();
    private static final Base64.Decoder DECODER = Base64.getUrlDecoder();

    /**
     * Decoded state.
     *
     * @param method originating JSON-RPC method
     * @param name tool/prompt name or resource URI
     * @param argumentsDigest digest of the arguments (or URI)
     * @param expiresAtMillis expiry, epoch millis
     * @param responses input responses collected so far (REPLAY mode)
     * @param continuationId continuation identifier (CONTINUATION mode)
     * @param pendingKeys input request keys issued with this state and awaiting an answer (REPLAY mode); never
     *     {@code null}
     */
    public record State(
            String method,
            String name,
            String argumentsDigest,
            long expiresAtMillis,
            JsonObject responses,
            String continuationId,
            List<String> pendingKeys) {

        /** Normalizes {@code pendingKeys} to an immutable, non-null list. */
        public State {
            pendingKeys = pendingKeys == null ? List.of() : List.copyOf(pendingKeys);
        }

        /**
         * Creates a state without pending input request keys.
         *
         * @param method originating JSON-RPC method
         * @param name tool/prompt name or resource URI
         * @param argumentsDigest digest of the arguments (or URI)
         * @param expiresAtMillis expiry, epoch millis
         * @param responses input responses collected so far (REPLAY mode)
         * @param continuationId continuation identifier (CONTINUATION mode)
         */
        public State(
                String method,
                String name,
                String argumentsDigest,
                long expiresAtMillis,
                JsonObject responses,
                String continuationId) {
            this(method, name, argumentsDigest, expiresAtMillis, responses, continuationId, List.of());
        }
    }

    private final byte[] secret;
    private final Clock clock;

    public McpRequestStateCodec(byte[] secret, Clock clock) {
        if (secret == null || secret.length < 32) {
            throw new IllegalArgumentException("requestState secret must be at least 32 bytes");
        }
        this.secret = secret.clone();
        this.clock = clock;
    }

    public long expiresAt(Duration ttl) {
        return clock.millis() + ttl.toMillis();
    }

    public String encode(State state) {
        JsonObjectBuilder json = Json.createObjectBuilder()
                .add("v", 1)
                .add("m", state.method())
                .add("n", state.name())
                .add("d", state.argumentsDigest())
                .add("e", state.expiresAtMillis());
        if (state.responses() != null && !state.responses().isEmpty()) {
            json.add("r", state.responses());
        }
        if (state.continuationId() != null) {
            json.add("c", state.continuationId());
        }
        if (!state.pendingKeys().isEmpty()) {
            json.add("p", Json.createArrayBuilder(state.pendingKeys()));
        }
        byte[] payload = json.build().toString().getBytes(StandardCharsets.UTF_8);
        return ENCODER.encodeToString(payload) + "." + ENCODER.encodeToString(sign(payload));
    }

    public State decode(Object requestId, String token, String method, String name, String argumentsDigest) {
        int dot = token == null ? -1 : token.indexOf('.');
        if (dot <= 0) {
            throw invalid(requestId);
        }
        byte[] payload;
        byte[] signature;
        try {
            payload = DECODER.decode(token.substring(0, dot));
            signature = DECODER.decode(token.substring(dot + 1));
        } catch (IllegalArgumentException e) {
            throw invalid(requestId);
        }
        if (!MessageDigest.isEqual(sign(payload), signature)) {
            throw invalid(requestId);
        }
        JsonObject json;
        try (JsonReader reader = Json.createReader(new StringReader(new String(payload, StandardCharsets.UTF_8)))) {
            json = reader.readObject();
        }
        if (json.getInt("v", 0) != 1
                || !method.equals(json.getString("m", ""))
                || !name.equals(json.getString("n", ""))
                || !argumentsDigest.equals(json.getString("d", ""))
                || !(json.get("e") instanceof JsonNumber expiry)) {
            throw invalid(requestId);
        }
        if (clock.millis() > expiry.longValue()) {
            throw McpProtocolErrors.invalidParams(requestId, "Expired requestState");
        }
        List<String> pendingKeys = new ArrayList<>();
        if (json.get("p") instanceof JsonArray pending) {
            pending.forEach(value -> {
                if (value instanceof JsonString key) {
                    pendingKeys.add(key.getString());
                }
            });
        }
        return new State(
                method,
                name,
                argumentsDigest,
                expiry.longValue(),
                json.get("r") instanceof JsonObject r ? r : JsonValue.EMPTY_JSON_OBJECT,
                json.getString("c", null),
                pendingKeys);
    }

    private byte[] sign(byte[] payload) {
        try {
            Mac mac = Mac.getInstance(HMAC);
            mac.init(new SecretKeySpec(secret, HMAC));
            return mac.doFinal(payload);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("HmacSHA256 not available", e);
        }
    }

    private static McpException invalid(Object requestId) {
        return McpProtocolErrors.invalidParams(requestId, "Invalid requestState");
    }
}
