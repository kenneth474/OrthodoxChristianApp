package com.orthodox.orthodoxapp.notify;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.orthodox.orthodoxapp.notify.PushSender.PushMessage;
import com.sun.net.httpserver.HttpServer;
import java.io.ByteArrayOutputStream;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.SecureRandom;
import java.security.interfaces.ECPublicKey;
import java.time.Clock;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import javax.crypto.Cipher;
import javax.crypto.KeyAgreement;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

/**
 * PushSender, end to end: a tiny pretend push service runs on this computer
 * (Java's built-in HttpServer), and the test plays the phone, decrypting
 * what arrives with the phone's private key, as a real phone would.
 */
class PushSenderTest {

    private record Received(Map<String, String> headers, byte[] body) {
    }

    private final List<Received> received = new CopyOnWriteArrayList<>();
    private final PushSubscriptionRepository subscriptions = mock(PushSubscriptionRepository.class);
    private HttpServer pushService;
    private int answer = 201;

    @BeforeEach
    void startPushService() throws Exception {
        pushService = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0); // 0 = any free port
        pushService.createContext("/push/", exchange -> {
            Map<String, String> headers = new HashMap<>();
            exchange.getRequestHeaders().forEach((name, values) -> headers.put(name.toLowerCase(), values.get(0)));
            received.add(new Received(headers, exchange.getRequestBody().readAllBytes()));
            exchange.sendResponseHeaders(answer, -1); // -1 = no body
            exchange.close();
        });
        pushService.start();
    }

    @AfterEach
    void stopPushService() {
        pushService.stop(0);
    }

    @Test
    void sendsAMessageOnlyThePhoneCanRead() throws Exception {
        KeyPair phone = WebPushCrypto.newKeyPair();
        byte[] auth = randomBytes(16);

        boolean accepted = sender().send(subscription(phone, auth),
                new PushMessage("Happy name day, Anna!", "May God grant you many years!", "/", "nameday"));

        assertTrue(accepted);
        Received request = received.get(0);
        assertEquals("aes128gcm", request.headers().get("content-encoding"));
        assertEquals("86400", request.headers().get("ttl")); // kept for a day if the phone is off
        assertEquals("nameday", request.headers().get("topic"));
        assertTrue(request.headers().get("authorization").startsWith("vapid t="));

        String json = new String(decrypt(request.body(), phone, auth), StandardCharsets.UTF_8);
        assertTrue(json.contains("\"title\":\"Happy name day, Anna!\""), json);
        assertTrue(json.contains("\"url\":\"/\""), json);
    }

    @Test
    void forgetsAPhoneWhosePushServiceSaysItsGone() throws Exception {
        answer = 410; // "Gone": notifications were turned off on the phone
        PushSubscription phone = subscription(WebPushCrypto.newKeyPair(), randomBytes(16));

        assertFalse(sender().send(phone, new PushMessage("Hello", "Text", "/", "test")));
        verify(subscriptions).deleteByEndpoint(phone.getEndpoint());
    }

    @Test
    void otherErrorsKeepThePhone() throws Exception {
        answer = 429; // "Too many requests": try again next time
        PushSubscription phone = subscription(WebPushCrypto.newKeyPair(), randomBytes(16));

        assertFalse(sender().send(phone, new PushMessage("Hello", "Text", "/", "test")));
        verify(subscriptions, never()).deleteByEndpoint(phone.getEndpoint());
    }

    @Test
    void onlyTheBrowsersPushServicesAreAllowed() {
        assertTrue(PushSender.isPushService("https://web.push.apple.com/QGx9abc"));
        assertTrue(PushSender.isPushService("https://fcm.googleapis.com/fcm/send/abc"));
        assertTrue(PushSender.isPushService("https://updates.push.services.mozilla.com/wpush/v2/abc"));
        assertTrue(PushSender.isPushService("https://wns2-par02p.notify.windows.com/w/?token=abc"));

        assertFalse(PushSender.isPushService("http://fcm.googleapis.com/fcm/send/abc")); // not https
        assertFalse(PushSender.isPushService("https://localhost:8080/api/account"));
        assertFalse(PushSender.isPushService("https://web.push.apple.com.example.com/x"));
        assertFalse(PushSender.isPushService("https://notpush.apple.com/x"));
        assertFalse(PushSender.isPushService("not an address"));
    }

    // ----- Helpers -----

    // A PushSender that may send to the pretend push service.
    private PushSender sender() {
        VapidKeys vapid = new VapidKeys(WebPushCrypto.newKeyPair(), "https://orthodox-daily.onrender.com",
                Clock.systemUTC());
        return new PushSender(vapid, subscriptions, JsonMapper.builder().build(), endpoint -> true);
    }

    private PushSubscription subscription(KeyPair phone, byte[] auth) {
        String endpoint = "http://127.0.0.1:" + pushService.getAddress().getPort() + "/push/phone-1";
        return new PushSubscription(1L, endpoint,
                WebPushCrypto.base64Url(WebPushCrypto.uncompressed((ECPublicKey) phone.getPublic())),
                WebPushCrypto.base64Url(auth));
    }

    /**
     * The phone's side of RFC 8291: undo WebPushCrypto.encrypt with the
     * phone's private key. Message = salt (16) | record size (4) | key
     * length (1) | server's one-time public key | ciphertext.
     */
    private static byte[] decrypt(byte[] message, KeyPair phone, byte[] auth) throws Exception {
        ByteBuffer buffer = ByteBuffer.wrap(message);
        byte[] salt = new byte[16];
        buffer.get(salt);
        buffer.getInt(); // record size
        byte[] serverKey = new byte[buffer.get()];
        buffer.get(serverKey);
        byte[] ciphertext = new byte[buffer.remaining()];
        buffer.get(ciphertext);

        KeyAgreement agreement = KeyAgreement.getInstance("ECDH");
        agreement.init(phone.getPrivate());
        agreement.doPhase(WebPushCrypto.publicKey(serverKey), true);
        byte[] phoneKey = WebPushCrypto.uncompressed((ECPublicKey) phone.getPublic());

        byte[] prkKey = WebPushCrypto.hmac(auth, agreement.generateSecret());
        byte[] ikm = expand(prkKey, concat(ascii("WebPush: info\0"), phoneKey, serverKey), 32);
        byte[] prk = WebPushCrypto.hmac(salt, ikm);
        byte[] key = expand(prk, ascii("Content-Encoding: aes128gcm\0"), 16);
        byte[] nonce = expand(prk, ascii("Content-Encoding: nonce\0"), 12);

        Cipher aes = Cipher.getInstance("AES/GCM/NoPadding");
        aes.init(Cipher.DECRYPT_MODE, new SecretKeySpec(key, "AES"), new GCMParameterSpec(128, nonce));
        byte[] padded = aes.doFinal(ciphertext);
        assertEquals(2, padded[padded.length - 1], "the last record ends with a 2");
        return Arrays.copyOf(padded, padded.length - 1);
    }

    private static byte[] expand(byte[] prk, byte[] info, int length) throws Exception {
        return Arrays.copyOf(WebPushCrypto.hmac(prk, concat(info, new byte[] {1})), length);
    }

    private static byte[] ascii(String text) {
        return text.getBytes(StandardCharsets.US_ASCII);
    }

    private static byte[] concat(byte[]... parts) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        for (byte[] part : parts) {
            out.writeBytes(part);
        }
        return out.toByteArray();
    }

    private static byte[] randomBytes(int count) {
        byte[] bytes = new byte[count];
        new SecureRandom().nextBytes(bytes);
        return bytes;
    }
}
