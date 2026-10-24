package com.orthodox.orthodoxapp.notify;

import java.net.URI;
import java.net.http.HttpClient;
import java.time.Duration;
import java.util.List;
import java.util.function.Predicate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import tools.jackson.databind.json.JsonMapper;

/**
 * PushSender: delivers one notification to one device, through that device's
 * push service (RFC 8030, "Generic Event Delivery Using HTTP Push").
 *
 * The message (title, text, and the page that opens when it's tapped) becomes
 * JSON, is encrypted for the device (WebPushCrypto), and is POSTed to the
 * device's endpoint with our signed VAPID note (VapidKeys). The push service
 * answers:
 *   201        accepted: delivered now, or kept until the device is online
 *   404 / 410  this device's subscription is gone (notifications turned off
 *              in Settings, the app removed...), so we forget the device
 *   other      logged; the next notification tries again
 */
@Component
public class PushSender {

    /**
     * What a notification shows. url = the page that opens when it's tapped;
     * tag = a newer notification with the same tag replaces an older one.
     */
    public record PushMessage(String title, String body, String url, String tag) {
    }

    private static final Logger log = LoggerFactory.getLogger(PushSender.class);

    // How long the push service keeps a message for a phone that's off.
    private static final Duration KEEP_FOR = Duration.ofDays(1);

    // The push services browsers use. Endpoints anywhere else are refused, so
    // nobody can make our server send requests to an address of their choosing.
    private static final List<String> PUSH_SERVICES = List.of(
            "push.apple.com",              // Safari, and iPhone Home Screen apps
            "fcm.googleapis.com",          // Chrome, Android and most others
            "push.services.mozilla.com",   // Firefox
            "notify.windows.com");         // Edge on Windows

    private final RestClient http;
    private final VapidKeys vapid;
    private final PushSubscriptionRepository subscriptions;
    private final JsonMapper json;
    private final Predicate<String> allowed;

    @Autowired
    public PushSender(VapidKeys vapid, PushSubscriptionRepository subscriptions, JsonMapper json) {
        this(vapid, subscriptions, json, PushSender::isPushService);
    }

    // For PushSenderTest, which plays the push service on this computer.
    PushSender(VapidKeys vapid, PushSubscriptionRepository subscriptions, JsonMapper json,
               Predicate<String> allowed) {
        // Give up on a slow push service instead of waiting forever.
        HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(client);
        factory.setReadTimeout(Duration.ofSeconds(15));
        this.http = RestClient.builder().requestFactory(factory).build();
        this.vapid = vapid;
        this.subscriptions = subscriptions;
        this.json = json;
        this.allowed = allowed;
    }

    /** True for an https address at one of the browsers' push services. */
    public static boolean isPushService(String endpoint) {
        try {
            URI uri = URI.create(endpoint);
            String host = uri.getHost();
            return "https".equals(uri.getScheme()) && host != null
                    && PUSH_SERVICES.stream().anyMatch(service -> host.equals(service) || host.endsWith("." + service));
        } catch (IllegalArgumentException ex) {
            return false;
        }
    }

    /** Sends the message to one device. True if its push service accepted it. */
    public boolean send(PushSubscription device, PushMessage message) {
        if (!allowed.test(device.getEndpoint())) {
            return false;
        }
        byte[] body = WebPushCrypto.encrypt(json.writeValueAsBytes(message),
                WebPushCrypto.fromBase64Url(device.getP256dh()), WebPushCrypto.fromBase64Url(device.getAuth()));
        try {
            HttpStatusCode status = http.post()
                    .uri(URI.create(device.getEndpoint()))
                    .header("TTL", String.valueOf(KEEP_FOR.toSeconds()))
                    .header("Urgency", "normal")
                    .header("Topic", message.tag())
                    .header(HttpHeaders.CONTENT_ENCODING, "aes128gcm")
                    .header(HttpHeaders.AUTHORIZATION, vapid.authorization(device.getEndpoint()))
                    .contentType(MediaType.APPLICATION_OCTET_STREAM)
                    .body(body)
                    .exchange((request, response) -> response.getStatusCode());
            if (status.is2xxSuccessful()) {
                return true;
            }
            if (status.value() == 404 || status.value() == 410) {
                subscriptions.deleteByEndpoint(device.getEndpoint());
                log.info("Forgot a device whose notifications were turned off ({})", status.value());
            } else {
                log.warn("A push service answered {} for one device", status.value());
            }
        } catch (RestClientException ex) {
            log.warn("Couldn't reach a push service: {}", ex.getMessage());
        }
        return false;
    }
}
