package dev.langchain4j.cdi.mcp.conformance;

import java.util.Base64;

/** Shared constants used by the conformance fixtures. */
public final class ConformanceFixtures {

    /** 1x1 red pixel PNG, base64-encoded, as used by the reference conformance servers. */
    public static final String TEST_IMAGE_BASE64 =
            "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mP8z8DwHwAFBQIAX8jx0gAAAABJRU5ErkJggg==";

    /** Minimal WAV file, base64-encoded, as used by the reference conformance servers. */
    public static final String TEST_AUDIO_BASE64 = "UklGRiYAAABXQVZFZm10IBAAAAABAAEAQB8AAAB9AAACABAAZGF0YQIAAAA=";

    private ConformanceFixtures() {}

    /**
     * Returns the decoded bytes of the test PNG.
     *
     * @return the PNG bytes
     */
    public static byte[] testImage() {
        return Base64.getDecoder().decode(TEST_IMAGE_BASE64);
    }

    /**
     * Returns the decoded bytes of the test WAV.
     *
     * @return the WAV bytes
     */
    public static byte[] testAudio() {
        return Base64.getDecoder().decode(TEST_AUDIO_BASE64);
    }
}
