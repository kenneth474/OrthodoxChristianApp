package com.orthodox.orthodoxapp.notify;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.orthodox.orthodoxapp.CalendarService;
import com.orthodox.orthodoxapp.FastingRule;
import com.orthodox.orthodoxapp.LiturgicalDay;
import com.orthodox.orthodoxapp.account.AppUser;
import com.orthodox.orthodoxapp.account.AppUserRepository;
import com.orthodox.orthodoxapp.notify.PushSender.PushMessage;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.web.client.RestClientException;

/**
 * When notifications go out, and what they say. The database, the push
 * services and the calendar are all pretend ("mocks"), and each test says
 * exactly what time it is, so no test waits for a real clock.
 *
 * Anna lives in Texas (Central time: 7:00 AM there is 12:00 in UTC on
 * October 24th, during daylight saving time).
 */
class NotificationServiceTest {

    private final AppUserRepository users = mock(AppUserRepository.class);
    private final PushSubscriptionRepository devices = mock(PushSubscriptionRepository.class);
    private final PushSender sender = mock(PushSender.class);
    private final CalendarService calendar = mock(CalendarService.class);
    private final NotificationService notifications = new NotificationService(users, devices, sender, calendar);

    private AppUser anna;

    @BeforeEach
    void setUp() {
        anna = new AppUser("anna@example.com", "Anna Smith");
        anna.setTimeZone(ZoneId.of("America/Chicago"));
        anna.setNotifyTime(LocalTime.of(7, 0));
        anna.setNotifyNameDay(false);
        PushSubscription phone = new PushSubscription(1L, "https://web.push.apple.com/anna", "key", "auth");
        when(devices.findByUserId(any())).thenReturn(List.of(phone));
        when(sender.send(any(), any())).thenReturn(true);
        when(calendar.getDay(any())).thenReturn(memorialSaturday());
    }

    @Test
    void theDailyNotificationComesAtTheChosenTimeOnce() {
        anna.setNotifyDaily(true);

        notifications.sendDue(anna, Instant.parse("2026-10-24T11:59:00Z")); // 6:59 AM in Texas
        verify(sender, never()).send(any(), any());

        notifications.sendDue(anna, Instant.parse("2026-10-24T12:00:00Z")); // 7:00 AM
        PushMessage message = sent().get(0);
        assertEquals("Memorial (Demetrius) Saturday", message.title());
        assertEquals("No fast. Today's readings: 2 Corinthians 3:12-18 and Luke 7:1-10.", message.body());
        assertEquals("daily", message.tag());

        notifications.sendDue(anna, Instant.parse("2026-10-24T12:01:00Z")); // a minute later: not again
        verify(sender, times(1)).send(any(), any());
    }

    @Test
    void aLateNotificationStillComesWithinThreeHours() {
        anna.setNotifyDaily(true);
        notifications.sendDue(anna, Instant.parse("2026-10-24T15:00:00Z")); // 10:00 AM: too late
        verify(sender, never()).send(any(), any());

        notifications.sendDue(anna, Instant.parse("2026-10-24T14:30:00Z")); // 9:30 AM: late, but sent
        verify(sender, times(1)).send(any(), any());
    }

    @Test
    void timeIsInEachPersonsOwnTimeZone() {
        anna.setNotifyDaily(true);
        anna.setTimeZone(ZoneId.of("America/New_York")); // 7:00 AM in New York is 11:00 UTC
        notifications.sendDue(anna, Instant.parse("2026-10-24T11:00:00Z"));
        verify(sender, times(1)).send(any(), any());
    }

    @Test
    void aCheerfulGreetingOnTheNameDay() {
        anna.setPatronSaint("St. Anna, mother of the Theotokos");
        anna.setNameDay("10-24");
        anna.setNotifyNameDay(true);

        notifications.sendDue(anna, Instant.parse("2026-10-24T12:00:00Z"));

        PushMessage greeting = sent().get(0);
        assertEquals("Happy name day, Anna!", greeting.title());
        assertEquals("Today the Church celebrates your patron saint, St. Anna, mother of the Theotokos. "
                + "May God grant you many years!", greeting.body());
        assertEquals(LocalDate.of(2026, 10, 24), anna.getLastNameDaySent());
    }

    @Test
    void noGreetingWhenTurnedOffOrNotFilledIn() {
        anna.setNameDay("10-24");
        anna.setNotifyNameDay(true);
        notifications.sendDue(anna, Instant.parse("2026-10-24T12:00:00Z")); // no patron saint
        anna.setPatronSaint("St. Anna");
        anna.setNotifyNameDay(false);
        notifications.sendDue(anna, Instant.parse("2026-10-24T12:00:00Z")); // turned off
        verify(sender, never()).send(any(), any());
    }

    @Test
    void aFebruary29thFeastIsKeptOnThe28thInOtherYears() {
        anna.setPatronSaint("St. John Cassian");
        anna.setNameDay("02-29");
        assertTrue(NotificationService.isNameDay(anna, LocalDate.of(2027, 2, 28)));
        assertFalse(NotificationService.isNameDay(anna, LocalDate.of(2028, 2, 28)));
        assertTrue(NotificationService.isNameDay(anna, LocalDate.of(2028, 2, 29)));
    }

    @Test
    void ifTheCalendarIsDownTheDailyNotificationStillComes() {
        anna.setNotifyDaily(true);
        when(calendar.getDay(any())).thenThrow(new RestClientException("orthocal.info is down"));

        notifications.sendDue(anna, Instant.parse("2026-10-24T12:00:00Z"));

        assertEquals("Today in the Church", sent().get(0).title());
    }

    @Test
    void joinsListsTheWayPeopleWrite() {
        assertEquals("a", NotificationService.joinWithAnd(List.of("a")));
        assertEquals("a and b", NotificationService.joinWithAnd(List.of("a", "b")));
        assertEquals("a, b and c", NotificationService.joinWithAnd(List.of("a", "b", "c")));
    }

    // ----- Helpers -----

    private List<PushMessage> sent() {
        ArgumentCaptor<PushMessage> messages = ArgumentCaptor.forClass(PushMessage.class);
        verify(sender, times(1)).send(any(), messages.capture());
        return messages.getAllValues();
    }

    // October 24th, 2026, as the calendar service would give it.
    private static LiturgicalDay memorialSaturday() {
        return new LiturgicalDay(
                LocalDate.of(2026, 10, 24),
                "Memorial (Demetrius) Saturday",
                List.of("Saturday of the 5th week of Luke"),
                new LiturgicalDay.Feast(0, "", List.of()),
                new LiturgicalDay.Fasting(FastingRule.NO_FAST, FastingRule.NO_FAST.label(), "", List.of()),
                3,
                List.of(),
                List.of(new LiturgicalDay.Reading("Epistle", "St. Paul's Second Letter to the Corinthians", "3:12-18",
                                "2 Corinthians 3:12-18", "", List.of()),
                        new LiturgicalDay.Reading("Gospel", "Luke", "7:1-10", "Luke 7:1-10", "", List.of())),
                List.of());
    }
}
