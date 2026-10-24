package com.orthodox.orthodoxapp.notify;

import java.io.ByteArrayOutputStream;
import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.AlgorithmParameters;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.SecureRandom;
import java.security.interfaces.ECPublicKey;
import java.security.spec.ECGenParameterSpec;
import java.security.spec.ECParameterSpec;
import java.security.spec.ECPoint;
import java.security.spec.ECPrivateKeySpec;
import java.security.spec.ECPublicKeySpec;
import java.util.Arrays;
import java.util.Base64;
import javax.crypto.Cipher;
import javax.crypto.KeyAgreement;
import javax.crypto.Mac;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

/**
 * WebPushCrypto: the encryption that web push messages need (RFC 8291,
 * "Message Encryption for Web Push").
 *
 * WHY ENCRYPT?
 * A notification travels from our server, through the push service of the
 * phone's or browser's maker (Apple, Google, Mozilla), to the device.
 * Encrypted, only that device can read it: the push service passes on
 * scrambled bytes.
 *
 * HOW, IN SHORT
 * When a device turned notifications on, it gave us its public key ("p256dh")
 * and a 16-byte secret ("auth"). For each message we:
 *   1. make a one-time key pair, and combine its private key with the device's
 *      public key (ECDH, "Elliptic-Curve Diffie-Hellman"): the device can
 *      work out the same shared secret, but nobody listening can;
 *   2. stir that together with the auth secret and a random "salt" (HKDF,
 *      made of HMAC-SHA256 steps) into a 16-byte AES key and a 12-byte nonce;
 *   3. encrypt the message with AES-128-GCM;
 *   4. send: salt + our one-time public key + the encrypted bytes.
 * The device repeats steps 1 and 2 with its own private key, and decrypts.
 *
 * Everything uses Java's built-in cryptography. WebPushCryptoTest checks it
 * against the worked example printed in the RFC.
 */
final class WebPushCrypto {

    // The curve every web push key uses: NIST P-256 (also called secp256r1).
    static final ECParameterSpec P256 = p256();

    // How big each encrypted "record" may be. Our messages fit in one record.
    private static final int RECORD_SIZE = 4096;

    private static final SecureRandom RANDOM = new SecureRandom();

    private WebPushCrypto() {
    }

    /** Encrypts a message for one device, with a new one-time key pair and salt. */
    static byte[] encrypt(byte[] plaintext, byte[] devicePublicKey, byte[] authSecret) {
        byte[] salt = new byte[16];
        RANDOM.nextBytes(salt);
        return encrypt(plaintext, devicePublicKey, authSecret, newKeyPair(), salt);
    }

    /** The same, with a given key pair and salt: lets the test use the RFC's example values. */
    static byte[] encrypt(byte[] plaintext, byte[] devicePublicKey, byte[] authSecret,
                          KeyPair serverKeys, byte[] salt) {
        try {
            byte[] serverPublicKey = uncompressed((ECPublicKey) serverKeys.getPublic());

            // 1. The shared secret (ECDH).
            KeyAgreement agreement = KeyAgreement.getInstance("ECDH");
            agreement.init(serverKeys.getPrivate());
            agreement.doPhase(publicKey(devicePublicKey), true);
            byte[] sharedSecret = agreement.generateSecret();

            // 2. Keys from the secrets (HKDF: "extract" with HMAC, then "expand").
            byte[] prkKey = hmac(authSecret, sharedSecret);
            byte[] keyInfo = concat(ascii("WebPush: info\0"), devicePublicKey, serverPublicKey);
            byte[] ikm = expand(prkKey, keyInfo, 32);
            byte[] prk = hmac(salt, ikm);
            byte[] contentKey = expand(prk, ascii("Content-Encoding: aes128gcm\0"), 16);
            byte[] nonce = expand(prk, ascii("Content-Encoding: nonce\0"), 12);

            // 3. Encrypt. The 2 at the end marks "this is the last record".
            Cipher aes = Cipher.getInstance("AES/GCM/NoPadding");
            aes.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(contentKey, "AES"), new GCMParameterSpec(128, nonce));
            byte[] ciphertext = aes.doFinal(concat(plaintext, new byte[] {2}));

            // 4. Header (salt, record size, our public key), then the ciphertext.
            ByteBuffer header = ByteBuffer.allocate(16 + 4 + 1 + serverPublicKey.length);
            header.put(salt).putInt(RECORD_SIZE).put((byte) serverPublicKey.length).put(serverPublicKey);
            return concat(header.array(), ciphertext);
        } catch (GeneralSecurityException ex) {
            throw new IllegalStateException("Couldn't encrypt the notification", ex);
        }
    }

    // ----- Keys -----

    static KeyPair newKeyPair() {
        try {
            KeyPairGenerator generator = KeyPairGenerator.getInstance("EC");
            generator.initialize(P256);
            return generator.generateKeyPair();
        } catch (GeneralSecurityException ex) {
            throw new IllegalStateException("This Java can't make P-256 keys", ex);
        }
    }

    /** A public key as the 65 bytes web push uses: 0x04, then the point's x and y. */
    static byte[] uncompressed(ECPublicKey key) {
        byte[] point = new byte[65];
        point[0] = 4;
        copyFixed(key.getW().getAffineX(), point, 1);
        copyFixed(key.getW().getAffineY(), point, 33);
        return point;
    }

    /** The other way round: 65 bytes from a browser -> a Java public key. */
    static ECPublicKey publicKey(byte[] uncompressed) throws GeneralSecurityException {
        if (uncompressed.length != 65 || uncompressed[0] != 4) {
            throw new GeneralSecurityException("Not an uncompressed P-256 public key");
        }
        ECPoint point = new ECPoint(new BigInteger(1, Arrays.copyOfRange(uncompressed, 1, 33)),
                new BigInteger(1, Arrays.copyOfRange(uncompressed, 33, 65)));
        return (ECPublicKey) KeyFactory.getInstance("EC").generatePublic(new ECPublicKeySpec(point, P256));
    }

    /** A key pair from its raw parts (the RFC's example keys, in the test). */
    static KeyPair keyPair(byte[] privateValue, byte[] uncompressedPublic) throws GeneralSecurityException {
        var privateKey = KeyFactory.getInstance("EC")
                .generatePrivate(new ECPrivateKeySpec(new BigInteger(1, privateValue), P256));
        return new KeyPair(publicKey(uncompressedPublic), privateKey);
    }

    // ----- Small helpers -----

    static byte[] hmac(byte[] key, byte[] data) throws GeneralSecurityException {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(key, "HmacSHA256"));
        return mac.doFinal(data);
    }

    // HKDF-Expand for up to 32 bytes: one HMAC of the info and a 1.
    private static byte[] expand(byte[] prk, byte[] info, int length) throws GeneralSecurityException {
        return Arrays.copyOf(hmac(prk, concat(info, new byte[] {1})), length);
    }

    static String base64Url(byte[] bytes) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    static byte[] fromBase64Url(String text) {
        return Base64.getUrlDecoder().decode(text.strip().replace('+', '-').replace('/', '_').replace("=", ""));
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

    // A BigInteger as exactly 32 bytes (no sign byte, zeros in front if short).
    private static void copyFixed(BigInteger value, byte[] target, int offset) {
        byte[] bytes = value.toByteArray();
        int length = Math.min(bytes.length, 32);
        System.arraycopy(bytes, bytes.length - length, target, offset + 32 - length, length);
    }

    private static ECParameterSpec p256() {
        try {
            AlgorithmParameters parameters = AlgorithmParameters.getInstance("EC");
            parameters.init(new ECGenParameterSpec("secp256r1"));
            return parameters.getParameterSpec(ECParameterSpec.class);
        } catch (GeneralSecurityException ex) {
            throw new IllegalStateException("This Java has no P-256 curve", ex);
        }
    }
}
