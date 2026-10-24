package com.orthodox.orthodoxapp.notify;

import com.orthodox.orthodoxapp.auth.ServerSecret;
import com.orthodox.orthodoxapp.auth.ServerSecretRepository;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.PrivateKey;
import java.security.Signature;
import java.security.interfaces.ECPublicKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * VapidKeys: how push services know a notification really comes from us
 * (RFC 8292, "VAPID": Voluntary Application Server Identification).
 *
 * The server has its own key pair, made once and kept in the database (two
 * ServerSecret rows), because it must never change: each device's
 * subscription is tied to our public key. A browser gets the public key when
 * it turns notifications on (publicKey()). Every message we send then
 * carries a small signed note, a JWT ("JSON Web Token"), saying who we are
 * and which push service it's for; the push service checks the signature
 * with our public key.
 *
 * A JWT is three parts joined by dots, each base64url-encoded:
 *   {"typ":"JWT","alg":"ES256"} . {"aud":..., "exp":..., "sub":...} . signature
 * "aud" = the push service (e.g. https://web.push.apple.com), "exp" = when the
 * note expires (at most 24 hours ahead), "sub" = how to contact us.
 */
@Component
public class VapidKeys {

    private static final String PRIVATE_NAME = "vapid-private";
    private static final String PUBLIC_NAME = "vapid-public";

    // A note lasts 12 hours, and we reuse it until it has an hour left
    // (Apple asks us not to make new ones more than once an hour).
    private static final Duration LIFETIME = Duration.ofHours(12);
    private static final Duration RENEW_BEFORE = Duration.ofHours(1);

    private record Note(String jwt, Instant expires) {
    }

    private final PrivateKey privateKey;
    private final String publicKey;
    private final String subject;
    private final Clock clock;
    private final Map<String, Note> notes = new ConcurrentHashMap<>();

    @Autowired
    public VapidKeys(ServerSecretRepository secrets,
                     @Value("${app.push.subject:https://orthodox-daily.onrender.com}") String subject) {
        this(load(secrets), subject, Clock.systemUTC());
    }

    VapidKeys(KeyPair keys, String subject, Clock clock) {
        this.privateKey = keys.getPrivate();
        this.publicKey = WebPushCrypto.base64Url(WebPushCrypto.uncompressed((ECPublicKey) keys.getPublic()));
        this.subject = subject;
        this.clock = clock;
    }

    /** Our public key, as browsers want it ("applicationServerKey"), in base64url. */
    public String publicKey() {
        return publicKey;
    }

    /** The Authorization header for a message to this endpoint: "vapid t=<JWT>, k=<public key>". */
    public String authorization(String endpoint) {
        URI uri = URI.create(endpoint);
        String audience = uri.getScheme() + "://" + uri.getAuthority(); // the push service's origin
        Instant now = clock.instant();
        Note note = notes.compute(audience, (key, old) ->
                old != null && now.isBefore(old.expires().minus(RENEW_BEFORE)) ? old : newNote(audience, now));
        return "vapid t=" + note.jwt() + ", k=" + publicKey;
    }

    private Note newNote(String audience, Instant now) {
        Instant expires = now.plus(LIFETIME);
        String header = encode("{\"typ\":\"JWT\",\"alg\":\"ES256\"}");
        String claims = encode("{\"aud\":\"" + audience + "\",\"exp\":" + expires.getEpochSecond()
                + ",\"sub\":\"" + subject + "\"}");
        String signed = header + "." + claims;
        try {
            // "P1363" = the signature as the 64 raw bytes JWTs use (r then s).
            Signature signature = Signature.getInstance("SHA256withECDSAinP1363Format");
            signature.initSign(privateKey);
            signature.update(signed.getBytes(StandardCharsets.US_ASCII));
            return new Note(signed + "." + WebPushCrypto.base64Url(signature.sign()), expires);
        } catch (GeneralSecurityException ex) {
            throw new IllegalStateException("Couldn't sign the VAPID note", ex);
        }
    }

    // Load the key pair from the database, or make it the very first time.
    private static KeyPair load(ServerSecretRepository secrets) {
        var savedPrivate = secrets.findById(PRIVATE_NAME);
        var savedPublic = secrets.findById(PUBLIC_NAME);
        try {
            if (savedPrivate.isPresent() && savedPublic.isPresent()) {
                PrivateKey privateKey = KeyFactory.getInstance("EC").generatePrivate(
                        new PKCS8EncodedKeySpec(Base64.getDecoder().decode(savedPrivate.get().getSecret())));
                return new KeyPair(
                        WebPushCrypto.publicKey(WebPushCrypto.fromBase64Url(savedPublic.get().getSecret())), privateKey);
            }
        } catch (GeneralSecurityException ex) {
            throw new IllegalStateException("The saved VAPID keys can't be read", ex);
        }
        KeyPair keys = WebPushCrypto.newKeyPair();
        secrets.save(new ServerSecret(PRIVATE_NAME, Base64.getEncoder().encodeToString(keys.getPrivate().getEncoded())));
        secrets.save(new ServerSecret(PUBLIC_NAME,
                WebPushCrypto.base64Url(WebPushCrypto.uncompressed((ECPublicKey) keys.getPublic()))));
        return keys;
    }

    private static String encode(String json) {
        return WebPushCrypto.base64Url(json.getBytes(StandardCharsets.UTF_8));
    }
}
