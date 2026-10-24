package com.orthodox.orthodoxapp.notify;

import com.orthodox.orthodoxapp.CalendarService;
import com.orthodox.orthodoxapp.LiturgicalDay;
import com.orthodox.orthodoxapp.account.AppUser;
import com.orthodox.orthodoxapp.account.AppUserRepository;
import com.orthodox.orthodoxapp.auth.AuthException;
import com.orthodox.orthodoxapp.notify.PushSender.PushMessage;
import java.time.Clock;
import java.time.DateTimeException;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.MonthDay;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * NotificationService: which notifications go to whom, and when.
 *
 * Two kinds, both at the time each person chooses, in their own time zone:
 *   - Daily (if they turn it on): the day's feast or saint, the fast, and
 *     the readings.
 *   - Name day (on unless they turn it off): a cheerful greeting on their
 *     patron saint's feast day, when both are filled in on Profile.
 * Notifications are switched on per device (a phone, a computer) in Profile,
 * so an account with no devices gets nothing: that's how someone opts out.
 *
 * sendDue() runs every minute (NotificationScheduler). A notification counts
 * as due from its time until 3 hours later, so one that comes due while the
 * free server is asleep still goes out when it wakes, just a little late.
 * lastDailySent and lastNameDaySent make sure each goes only once a day.
 */
@Service
public class NotificationService {

    private static final Logger log = LoggerFactory.getLogger(NotificationService.class);

    static final Duration CATCH_UP = Duration.ofHours(3);

    private final AppUserRepository users;
    private final PushSubscriptionRepository devices;
    private final PushSender sender;
    private final CalendarService calendar;
    private final Clock clock;

    @Autowired
    public NotificationService(AppUserRepository users, PushSubscriptionRepository devices, PushSender sender,
                               CalendarService calendar) {
        this(users, devices, sender, calendar, Clock.systemUTC());
    }

    NotificationService(AppUserRepository users, PushSubscriptionRepository devices, PushSender sender,
                        CalendarService calendar, Clock clock) {
        this.users = users;
        this.devices = devices;
        this.sender = sender;
        this.calendar = calendar;
        this.clock = clock;
    }

    // ----- Devices -----

    /**
     * Notifications on for this device. The browser's subscription has the
     * device's address (endpoint) and its keys (p256dh, auth), in base64url.
     * If the device was registered by someone else (a shared computer), it
     * now belongs to this account. Returns how many devices the account has.
     */
    @Transactional
    public long addDevice(Long userId, String endpoint, String p256dh, String auth) {
        if (endpoint == null || endpoint.length() > 1000 || !PushSender.isPushService(endpoint)) {
            throw badDevice();
        }
        try {
            if (WebPushCrypto.fromBase64Url(p256dh).length != 65 || WebPushCrypto.fromBase64Url(auth).length != 16) {
                throw badDevice();
            }
        } catch (IllegalArgumentException | NullPointerException ex) {
            throw badDevice();
        }
        devices.findByEndpoint(endpoint).ifPresentOrElse(
                device -> device.update(userId, p256dh, auth),
                () -> devices.save(new PushSubscription(userId, endpoint, p256dh, auth)));
        return devices.countByUserId(userId);
    }

    /** Notifications off for this device (only if it's this account's). */
    @Transactional
    public long removeDevice(Long userId, String endpoint) {
        devices.findByEndpoint(endpoint == null ? "" : endpoint)
                .filter(device -> device.getUserId().equals(userId))
                .ifPresent(devices::delete);
        return devices.countByUserId(userId);
    }

    public long deviceCount(Long userId) {
        return devices.countByUserId(userId);
    }

    /** "Send a test notification", to every device of this account. Returns how many took it. */
    public int sendTest(AppUser user) {
        return sendToDevices(user.getId(), new PushMessage(
                "Notifications are on",
                "This is how Orthodox Daily will reach you. You can change them or turn them off any time in Profile.",
                "/profile", "test"));
    }

    // ----- Settings -----

    /**
     * Saves the choices from Profile.
     *
     * @param time     "07:30" (24-hour)
     * @param timeZone the browser's time zone, e.g. "America/Chicago"
     */
    @Transactional
    public AppUser updateSettings(AppUser user, boolean daily, boolean nameDay, String time, String timeZone) {
        LocalTime at;
        try {
            at = LocalTime.parse(time == null ? "" : time.strip());
        } catch (DateTimeException ex) {
            throw new AuthException(HttpStatus.BAD_REQUEST, "Choose a time, like 7:00 AM.");
        }
        user.setNotifyDaily(daily);
        user.setNotifyNameDay(nameDay);
        user.setNotifyTime(at.withSecond(0).withNano(0));
        try {
            user.setTimeZone(ZoneId.of(timeZone));
        } catch (DateTimeException | NullPointerException ex) {
            // An unknown time zone: keep the one we had.
        }
        return users.save(user);
    }

    // ----- Every minute (NotificationScheduler) -----

    /** Sends every notification that's due now, to every account with a device. */
    public void sendDue() {
        Instant now = clock.instant();
        for (AppUser user : users.findAllById(devices.findUserIdsWithDevices())) {
            sendDue(user, now);
        }
    }

    void sendDue(AppUser user, Instant now) {
        ZoneId zone = user.getTimeZone();
        ZonedDateTime local = now.atZone(zone);
        LocalDate today = local.toLocalDate();
        ZonedDateTime dueAt = today.atTime(user.getNotifyTime()).atZone(zone);
        if (local.isBefore(dueAt) || !local.isBefore(dueAt.plus(CATCH_UP))) {
            return; // not yet, or too late to still be useful
        }

        boolean sent = false;
        if (user.isNotifyDaily() && !today.equals(user.getLastDailySent())) {
            sendToDevices(user.getId(), dailyMessage(today));
            user.setLastDailySent(today);
            sent = true;
        }
        // Sent second, so on a phone it's the one on top.
        if (user.isNotifyNameDay() && isNameDay(user, today) && !today.equals(user.getLastNameDaySent())) {
            sendToDevices(user.getId(), nameDayMessage(user));
            user.setLastNameDaySent(today);
            sent = true;
        }
        if (sent) {
            users.save(user);
        }
    }

    // ----- The messages -----

    /**
     * The daily notification, e.g.
     *   Memorial (Demetrius) Saturday
     *   No fast. Today's readings: 2 Corinthians 3:12-18 and Luke 7:1-10.
     */
    PushMessage dailyMessage(LocalDate date) {
        try {
            LiturgicalDay day = calendar.getDay(date);
            String title = day.title() == null || day.title().isBlank() ? "Today in the Church" : day.title();
            StringBuilder body = new StringBuilder(day.fasting().label()).append('.');
            List<String> readings = day.readings().stream()
                    .map(LiturgicalDay.Reading::reference)
                    .filter(reference -> reference != null && !reference.isBlank())
                    .toList();
            if (!readings.isEmpty()) {
                body.append(" Today's readings: ").append(joinWithAnd(readings)).append('.');
            }
            return new PushMessage(title, body.toString(), "/", "daily");
        } catch (RuntimeException ex) {
            // The calendar service is down: still send something useful.
            return new PushMessage("Today in the Church",
                    "Today's saints, fast and readings are waiting for you in Orthodox Daily.", "/", "daily");
        }
    }

    /**
     * The name-day greeting, e.g.
     *   Happy name day, Kenneth!
     *   Today the Church celebrates your patron saint, St. Nicholas the
     *   Wonderworker. May God grant you many years!
     * ("Many years!" is the traditional Orthodox name-day wish.)
     */
    PushMessage nameDayMessage(AppUser user) {
        String firstName = user.getDisplayName().strip().split("\\s+")[0];
        return new PushMessage("Happy name day, " + firstName + "!",
                "Today the Church celebrates your patron saint, " + user.getPatronSaint()
                        + ". May God grant you many years!",
                "/", "nameday");
    }

    /**
     * Is today this person's name day? Only if they've filled in both their
     * patron saint and the feast day. A feast on February 29th is kept on the
     * 28th in other years.
     */
    static boolean isNameDay(AppUser user, LocalDate date) {
        if (user.getPatronSaint() == null || user.getNameDay() == null) {
            return false;
        }
        MonthDay feast = MonthDay.parse("--" + user.getNameDay());
        if (feast.equals(MonthDay.of(2, 29)) && !date.isLeapYear()) {
            feast = MonthDay.of(2, 28);
        }
        return MonthDay.from(date).equals(feast);
    }

    // Sends a message to every device of an account; returns how many took it.
    // (The log says what kind went out and where, never anyone's details.)
    private int sendToDevices(Long userId, PushMessage message) {
        List<PushSubscription> targets = devices.findByUserId(userId);
        int accepted = 0;
        for (PushSubscription device : targets) {
            if (sender.send(device, message)) {
                accepted++;
            }
        }
        log.info("Sent a \"{}\" notification: {} of {} device(s) took it", message.tag(), accepted, targets.size());
        return accepted;
    }

    // ["a"] -> "a"; ["a", "b"] -> "a and b"; ["a", "b", "c"] -> "a, b and c"
    static String joinWithAnd(List<String> items) {
        if (items.size() == 1) {
            return items.get(0);
        }
        return String.join(", ", items.subList(0, items.size() - 1)) + " and " + items.get(items.size() - 1);
    }

    private static AuthException badDevice() {
        return new AuthException(HttpStatus.BAD_REQUEST, "This browser's notification details weren't accepted.");
    }
}
