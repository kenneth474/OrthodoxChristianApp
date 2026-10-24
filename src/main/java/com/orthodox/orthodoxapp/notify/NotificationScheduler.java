package com.orthodox.orthodoxapp.notify;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;

/**
 * NotificationScheduler: once a minute, sends the notifications that are due.
 *
 * @EnableScheduling switches on Spring's timer, and @Scheduled(cron = ...)
 * says when to run: "0 * * * * *" = second 0 of every minute. (A cron
 * expression lists second, minute, hour, day, month and weekday; * = every.)
 *
 * On Render's free plan the server sleeps after 15 minutes without visitors,
 * and a sleeping server can't send anything. .github/workflows/keep-awake.yml
 * visits it every 10 minutes so it stays awake.
 *
 * Tests that check sending switch this off (app.notify.scheduler=false), so
 * the timer can't send something in the middle of a test.
 */
@Configuration
@EnableScheduling
@ConditionalOnProperty(name = "app.notify.scheduler", havingValue = "true", matchIfMissing = true)
class NotificationScheduler {

    private final NotificationService notifications;

    NotificationScheduler(NotificationService notifications) {
        this.notifications = notifications;
    }

    @Scheduled(cron = "0 * * * * *")
    void sendDueNotifications() {
        notifications.sendDue();
    }
}
