package com.orthodox.orthodoxapp.notify;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.interfaces.ECPublicKey;
import org.junit.jupiter.api.Test;

/**
 * Checks our web push encryption against the worked example in RFC 8291,
 * Appendix A: with the RFC's keys, salt and message, we must produce exactly
 * the RFC's bytes. (If even one step were wrong, phones couldn't decrypt.)
 */
class WebPushCryptoTest {

    // The values printed in RFC 8291, Appendix A (base64url).
    private static final String PLAINTEXT = "When I grow up, I want to be a watermelon";
    private static final String SERVER_PRIVATE = "yfWPiYE-n46HLnH0KqZOF1fJJU3MYrct3AELtAQ-oRw";
    private static final String SERVER_PUBLIC =
            "BP4z9KsN6nGRTbVYI_c7VJSPQTBtkgcy27mlmlMoZIIgDll6e3vCYLocInmYWAmS6TlzAC8wEqKK6PBru3jl7A8";
    private static final String DEVICE_PUBLIC =
            "BCVxsr7N_eNgVRqvHtD0zTZsEc6-VV-JvLexhqUzORcxaOzi6-AYWXvTBHm4bjyPjs7Vd8pZGH6SRpkNtoIAiw4";
    private static final String AUTH_SECRET = "BTBZMqHH6r4Tts7J_aSIgg";
    private static final String SALT = "DGv6ra1nlYgDCS1FRnbzlw";
    private static final String HEADER =
            "DGv6ra1nlYgDCS1FRnbzlwAAEABBBP4z9KsN6nGRTbVYI_c7VJSPQTBtkgcy27mlmlMoZIIgDll6e3vCYLocInmYWAmS6TlzAC8wEqKK6PBru3jl7A8";
    private static final String CIPHERTEXT =
            "8pfeW0KbunFT06SuDKoJH9Ql87S1QUrdirN6GcG7sFz1y1sqLgVi1VhjVkHsUoEsbI_0LpXMuGvnzQ";

    @Test
    void producesExactlyTheRfcExample() throws Exception {
        KeyPair serverKeys = WebPushCrypto.keyPair(bytes(SERVER_PRIVATE), bytes(SERVER_PUBLIC));

        byte[] message = WebPushCrypto.encrypt(PLAINTEXT.getBytes(StandardCharsets.UTF_8),
                bytes(DEVICE_PUBLIC), bytes(AUTH_SECRET), serverKeys, bytes(SALT));

        // The RFC's message is its 86-byte header followed by the ciphertext.
        assertArrayEquals(concat(bytes(HEADER), bytes(CIPHERTEXT)), message);
    }

    @Test
    void publicKeysGoBackAndForthUnchanged() throws Exception {
        ECPublicKey key = WebPushCrypto.publicKey(bytes(DEVICE_PUBLIC));
        assertEquals(DEVICE_PUBLIC, WebPushCrypto.base64Url(WebPushCrypto.uncompressed(key)));
    }

    @Test
    void everyMessageIsEncryptedDifferently() {
        // A new one-time key and salt each time, so the same text never looks the same twice.
        byte[] text = PLAINTEXT.getBytes(StandardCharsets.UTF_8);
        byte[] first = WebPushCrypto.encrypt(text, bytes(DEVICE_PUBLIC), bytes(AUTH_SECRET));
        byte[] second = WebPushCrypto.encrypt(text, bytes(DEVICE_PUBLIC), bytes(AUTH_SECRET));
        assertNotEquals(WebPushCrypto.base64Url(first), WebPushCrypto.base64Url(second));
    }

    private static byte[] bytes(String base64Url) {
        return WebPushCrypto.fromBase64Url(base64Url);
    }

    private static byte[] concat(byte[] a, byte[] b) {
        byte[] both = new byte[a.length + b.length];
        System.arraycopy(a, 0, both, 0, a.length);
        System.arraycopy(b, 0, both, a.length, b.length);
        return both;
    }
}
