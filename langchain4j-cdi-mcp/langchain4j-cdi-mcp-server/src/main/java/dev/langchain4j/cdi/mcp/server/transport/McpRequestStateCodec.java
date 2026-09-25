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
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import javax.crypto.AEADBadTagException;
import javax.crypto.Cipher;
import javax.crypto.Mac;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

/**
 * Encrypted, authenticated, expiring {@code requestState} bound to the originating request.
 *
 * <p>A token is {@code v2.} followed by the base64url of a random 12-byte nonce, the AES-256-GCM ciphertext of the
 * state and its 16-byte tag: a client, a proxy or an access log holding the token can neither read the state, which
 * carries the elicitation and sampling answers collected so far, nor alter it. The AES key is derived from the secret
 * ({@code HMAC-SHA256(secret, "mcp-request-state/aes-256-gcm/v2")}), never the secret itself.
 *
 * <p>Any other token is rejected, the first format ({@code base64url(json) + "." + base64url(HMAC-SHA256)}, signed but
 * not encrypted) included: no released version ever issued it (#304).
 */
public final class McpRequestStateCodec {

    private static final String HMAC = "HmacSHA256";
    private static final String AES = "AES";
    private static final String AES_GCM = "AES/GCM/NoPadding";
    private static final String V2_PREFIX = "v2.";
    private static final byte[] V2_AAD = "mcp-request-state/v2".getBytes(StandardCharsets.UTF_8);
    private static final byte[] V2_KEY_LABEL = "mcp-request-state/aes-256-gcm/v2".getBytes(StandardCharsets.UTF_8);
    private static final int NONCE_BYTES = 12;
    private static final int TAG_BITS = 128;
    private static final SecureRandom RANDOM = new SecureRandom();
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
    private final SecretKeySpec aesKey;
    private final Clock clock;

    public McpRequestStateCodec(byte[] secret, Clock clock) {
        if (secret == null || secret.length < 32) {
            throw new IllegalArgumentException("requestState secret must be at least 32 bytes");
        }
        this.secret = secret.clone();
        this.aesKey = new SecretKeySpec(sign(V2_KEY_LABEL), AES);
        this.clock = clock;
    }

    public long expiresAt(Duration ttl) {
        return clock.millis() + ttl.toMillis();
    }

    public String encode(State state) {
        // "v" is the version of the payload's schema; the token format is the "v2." prefix, which the AAD also binds.
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
        return V2_PREFIX + ENCODER.encodeToString(encrypt(payload));
    }

    public State decode(Object requestId, String token, String method, String name, String argumentsDigest) {
        if (token == null || !token.startsWith(V2_PREFIX)) {
            throw invalid(requestId);
        }
        byte[] payload = decrypted(requestId, token.substring(V2_PREFIX.length()));
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

    /** The payload of a {@code v2} token, decrypted and authenticated. */
    private byte[] decrypted(Object requestId, String blob) {
        byte[] bytes;
        try {
            bytes = DECODER.decode(blob);
        } catch (IllegalArgumentException e) {
            throw invalid(requestId);
        }
        if (bytes.length < NONCE_BYTES + TAG_BITS / 8) {
            throw invalid(requestId);
        }
        try {
            Cipher cipher = Cipher.getInstance(AES_GCM);
            cipher.init(Cipher.DECRYPT_MODE, aesKey, new GCMParameterSpec(TAG_BITS, bytes, 0, NONCE_BYTES));
            cipher.updateAAD(V2_AAD);
            return cipher.doFinal(bytes, NONCE_BYTES, bytes.length - NONCE_BYTES);
        } catch (AEADBadTagException e) {
            throw invalid(requestId);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("AES/GCM not available", e);
        }
    }

    /** {@code nonce || ciphertext || tag}, with a fresh random nonce. */
    private byte[] encrypt(byte[] payload) {
        byte[] nonce = new byte[NONCE_BYTES];
        RANDOM.nextBytes(nonce);
        try {
            Cipher cipher = Cipher.getInstance(AES_GCM);
            cipher.init(Cipher.ENCRYPT_MODE, aesKey, new GCMParameterSpec(TAG_BITS, nonce));
            cipher.updateAAD(V2_AAD);
            byte[] sealed = cipher.doFinal(payload);
            byte[] out = new byte[NONCE_BYTES + sealed.length];
            System.arraycopy(nonce, 0, out, 0, NONCE_BYTES);
            System.arraycopy(sealed, 0, out, NONCE_BYTES, sealed.length);
            return out;
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("AES/GCM not available", e);
        }
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
