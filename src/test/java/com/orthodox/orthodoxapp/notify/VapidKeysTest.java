package com.orthodox.orthodoxapp.notify;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.Signature;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

/** The signed note (VAPID JWT) that comes with every notification we send. */
class VapidKeysTest {

    private static final Pattern HEADER = Pattern.compile("vapid t=([^,]+), k=(\\S+)");

    @Test
    void signsANoteThePushServiceCanCheck() throws Exception {
        KeyPair keys = WebPushCrypto.newKeyPair();
        MovableClock clock = new MovableClock(Instant.parse("2026-10-24T12:00:00Z"));
        VapidKeys vapid = new VapidKeys(keys, "https://orthodox-daily.onrender.com", clock);

        Matcher header = HEADER.matcher(vapid.authorization("https://web.push.apple.com/QGx9abc"));
        assertTrue(header.matches());
        assertEquals(vapid.publicKey(), header.group(2));

        String[] jwt = header.group(1).split("\\.");
        assertEquals(3, jwt.length, "header.claims.signature");
        String claims = new String(WebPushCrypto.fromBase64Url(jwt[1]), StandardCharsets.UTF_8);
        assertTrue(claims.contains("\"aud\":\"https://web.push.apple.com\""), claims); // the push service only
        assertTrue(claims.contains("\"exp\":" + Instant.parse("2026-10-25T00:00:00Z").getEpochSecond()), claims);
        assertTrue(claims.contains("\"sub\":\"https://orthodox-daily.onrender.com\""), claims);

        // The push service checks the signature with our public key.
        Signature check = Signature.getInstance("SHA256withECDSAinP1363Format");
        check.initVerify(keys.getPublic());
        check.update((jwt[0] + "." + jwt[1]).getBytes(StandardCharsets.US_ASCII));
        assertTrue(check.verify(WebPushCrypto.fromBase64Url(jwt[2])));
    }

    @Test
    void keepsUsingANoteForHoursThenMakesANewOne() {
        MovableClock clock = new MovableClock(Instant.parse("2026-10-24T12:00:00Z"));
        VapidKeys vapid = new VapidKeys(WebPushCrypto.newKeyPair(), "https://orthodox-daily.onrender.com", clock);
        String endpoint = "https://fcm.googleapis.com/fcm/send/abc";

        String first = vapid.authorization(endpoint);
        clock.add(Duration.ofHours(10));
        assertEquals(first, vapid.authorization(endpoint), "still the same note 10 hours later");
        clock.add(Duration.ofHours(1).plusMinutes(1));
        assertNotEquals(first, vapid.authorization(endpoint), "a new note when less than an hour is left");
    }

    /** A clock the test can move forward. */
    private static final class MovableClock extends Clock {
        private Instant now;

        MovableClock(Instant now) {
            this.now = now;
        }

        void add(Duration duration) {
            now = now.plus(duration);
        }

        @Override
        public Instant instant() {
            return now;
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }
    }
}
