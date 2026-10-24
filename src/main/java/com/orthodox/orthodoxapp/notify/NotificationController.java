package com.orthodox.orthodoxapp.notify;

import com.orthodox.orthodoxapp.account.AccountService;
import com.orthodox.orthodoxapp.account.AccountView;
import com.orthodox.orthodoxapp.auth.SignedInUser;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * NotificationController: the Notifications card in Profile.
 * Every address needs a signed-in user (SecurityConfig).
 *
 *   GET  /api/notifications                -> our public key (for the browser) and how many devices are on
 *   POST /api/notifications/devices        -> notifications on for this device (the browser's subscription)
 *   POST /api/notifications/devices/remove -> notifications off for this device
 *   PUT  /api/notifications/settings       -> daily on/off, name day on/off, the time, the time zone
 *   POST /api/notifications/test           -> send a test notification to this account's devices
 */
@RestController
@RequestMapping("/api/notifications")
public class NotificationController {

    /** A browser's push subscription, exactly as subscription.toJSON() gives it. */
    public record DeviceRequest(String endpoint, Keys keys) {
        public record Keys(String p256dh, String auth) {
        }
    }

    public record EndpointRequest(String endpoint) {
    }

    public record SettingsRequest(boolean daily, boolean nameDay, String time, String timeZone) {
    }

    /** publicKey: what browsers call the "applicationServerKey"; devices: how many have notifications on. */
    public record Status(String publicKey, long devices) {
    }

    public record TestResult(int sent) {
    }

    private final NotificationService notifications;
    private final AccountService accounts;
    private final VapidKeys vapid;

    public NotificationController(NotificationService notifications, AccountService accounts, VapidKeys vapid) {
        this.notifications = notifications;
        this.accounts = accounts;
        this.vapid = vapid;
    }

    @GetMapping
    public Status status(@AuthenticationPrincipal SignedInUser me) {
        return new Status(vapid.publicKey(), notifications.deviceCount(me.id()));
    }

    @PostMapping("/devices")
    public Status addDevice(@AuthenticationPrincipal SignedInUser me, @RequestBody DeviceRequest body) {
        DeviceRequest.Keys keys = body.keys() == null ? new DeviceRequest.Keys(null, null) : body.keys();
        long devices = notifications.addDevice(me.id(), body.endpoint(), keys.p256dh(), keys.auth());
        return new Status(vapid.publicKey(), devices);
    }

    @PostMapping("/devices/remove")
    public Status removeDevice(@AuthenticationPrincipal SignedInUser me, @RequestBody EndpointRequest body) {
        return new Status(vapid.publicKey(), notifications.removeDevice(me.id(), body.endpoint()));
    }

    @PutMapping("/settings")
    public AccountView updateSettings(@AuthenticationPrincipal SignedInUser me, @RequestBody SettingsRequest body) {
        return accounts.view(notifications.updateSettings(
                accounts.get(me.id()), body.daily(), body.nameDay(), body.time(), body.timeZone()));
    }

    @PostMapping("/test")
    public TestResult test(@AuthenticationPrincipal SignedInUser me) {
        return new TestResult(notifications.sendTest(accounts.get(me.id())));
    }
}
