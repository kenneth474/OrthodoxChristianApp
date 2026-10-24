package com.orthodox.orthodoxapp.notify;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import java.time.Instant;

/**
 * PushSubscription: one device (phone or browser) that has turned on
 * notifications, as a row in the "push_subscription" table.
 *
 * When notifications are switched on, the browser asks its push service
 * (Apple's, Google's, Mozilla's...) for an address and hands us three things:
 *   endpoint = the address to send this device's notifications to
 *   p256dh   = the device's public key, to encrypt them with (WebPushCrypto)
 *   auth     = a 16-byte secret that goes into the encryption too
 * None of these is personal: they only let us reach that one device.
 * One account can have several (an iPhone and a computer, say).
 */
@Entity
public class PushSubscription {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long userId;

    @Column(nullable = false, unique = true, length = 1000)
    private String endpoint;

    @Column(nullable = false)
    private String p256dh;

    @Column(nullable = false)
    private String auth;

    @Column(nullable = false)
    private Instant createdAt;

    protected PushSubscription() {
    }

    public PushSubscription(Long userId, String endpoint, String p256dh, String auth) {
        this.userId = userId;
        this.endpoint = endpoint;
        this.p256dh = p256dh;
        this.auth = auth;
        this.createdAt = Instant.now();
    }

    /** The same device, now signed in to another account (or with new keys). */
    public void update(Long userId, String p256dh, String auth) {
        this.userId = userId;
        this.p256dh = p256dh;
        this.auth = auth;
    }

    public Long getUserId() { return userId; }

    public String getEndpoint() { return endpoint; }

    public String getP256dh() { return p256dh; }

    public String getAuth() { return auth; }
}
