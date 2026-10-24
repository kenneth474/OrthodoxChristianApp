package com.orthodox.orthodoxapp.notify;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.security.SecureRandom;
import java.security.interfaces.ECPublicKey;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/**
 * The Notifications card's endpoints: turning notifications on and off for a
 * device, the settings, and a test notification. Nothing really goes out:
 * PushSender is a pretend one here, and the every-minute timer is off.
 */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:notifications;DB_CLOSE_DELAY=-1",
        "app.notify.scheduler=false"})
@AutoConfigureMockMvc
class NotificationTest {

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private PushSender sender;

    @Test
    void notificationsCanBeTurnedOnAndOffForADevice() throws Exception {
        MockHttpSession browser = register("phone@example.com");
        mvc.perform(get("/api/notifications").session(browser))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.publicKey").isString())
                .andExpect(jsonPath("$.devices").value(0));

        String endpoint = "https://web.push.apple.com/phone-1";
        send(post("/api/notifications/devices"), browser, device(endpoint))
                .andExpect(jsonPath("$.devices").value(1));
        send(post("/api/notifications/devices"), browser, device(endpoint)) // the same phone again
                .andExpect(jsonPath("$.devices").value(1));
        send(post("/api/notifications/devices/remove"), browser, """
                {"endpoint": "%s"}""".formatted(endpoint))
                .andExpect(jsonPath("$.devices").value(0));
    }

    @Test
    void onlyRealPushServiceAddressesAreAccepted() throws Exception {
        MockHttpSession browser = register("sneaky@example.com");
        send(post("/api/notifications/devices"), browser, device("https://example.com/not-a-push-service"))
                .andExpect(status().isBadRequest());
        send(post("/api/notifications/devices"), browser, device("http://localhost:8080/api/account"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void aSharedComputerMovesToWhoeverTurnsNotificationsOnLast() throws Exception {
        String endpoint = "https://fcm.googleapis.com/fcm/send/shared-computer";
        MockHttpSession first = register("first@example.com");
        send(post("/api/notifications/devices"), first, device(endpoint));

        MockHttpSession second = register("second@example.com");
        send(post("/api/notifications/devices"), second, device(endpoint))
                .andExpect(jsonPath("$.devices").value(1));
        mvc.perform(get("/api/notifications").session(first)).andExpect(jsonPath("$.devices").value(0));
    }

    @Test
    void savesTheDailyChoiceTheTimeAndTheNameDayGreeting() throws Exception {
        MockHttpSession browser = register("settings@example.com");
        mvc.perform(get("/api/account").session(browser))
                .andExpect(jsonPath("$.notifyDaily").value(false))  // off until chosen
                .andExpect(jsonPath("$.notifyNameDay").value(true)) // on unless turned off
                .andExpect(jsonPath("$.notifyTime").value("07:00"));

        send(put("/api/notifications/settings"), browser, """
                {"daily": true, "nameDay": false, "time": "06:30", "timeZone": "America/New_York"}""")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.notifyDaily").value(true))
                .andExpect(jsonPath("$.notifyNameDay").value(false))
                .andExpect(jsonPath("$.notifyTime").value("06:30"));

        send(put("/api/notifications/settings"), browser, """
                {"daily": true, "nameDay": true, "time": "half past six", "timeZone": "America/New_York"}""")
                .andExpect(status().isBadRequest());
    }

    @Test
    void aTestNotificationGoesToEveryDeviceOfTheAccount() throws Exception {
        when(sender.send(any(), any())).thenReturn(true);
        MockHttpSession browser = register("tester@example.com");
        send(post("/api/notifications/devices"), browser, device("https://web.push.apple.com/tester-phone"));
        send(post("/api/notifications/devices"), browser, device("https://fcm.googleapis.com/fcm/send/tester-pc"));

        send(post("/api/notifications/test"), browser, null)
                .andExpect(jsonPath("$.sent").value(2));
        verify(sender, org.mockito.Mockito.times(2))
                .send(any(), argThat(message -> message.title().equals("Notifications are on")));
    }

    @Test
    void notificationSettingsNeedASignedInUser() throws Exception {
        mvc.perform(get("/api/notifications")).andExpect(status().isUnauthorized());
    }

    // ----- Helpers -----

    private ResultActions send(MockHttpServletRequestBuilder request, MockHttpSession browser, String json)
            throws Exception {
        request.session(browser).with(csrf());
        if (json != null) {
            request.contentType(MediaType.APPLICATION_JSON).content(json);
        }
        return mvc.perform(request);
    }

    private MockHttpSession register(String email) throws Exception {
        MockHttpSession browser = new MockHttpSession();
        send(post("/api/auth/register"), browser, """
                {"email": "%s", "password": "Vespers-at-6:30pm", "displayName": "Test"}""".formatted(email))
                .andExpect(status().isCreated());
        return browser;
    }

    // A browser's subscription, with a real public key and auth secret.
    private static String device(String endpoint) {
        byte[] auth = new byte[16];
        new SecureRandom().nextBytes(auth);
        String key = WebPushCrypto.base64Url(
                WebPushCrypto.uncompressed((ECPublicKey) WebPushCrypto.newKeyPair().getPublic()));
        return """
                {"endpoint": "%s", "keys": {"p256dh": "%s", "auth": "%s"}}"""
                .formatted(endpoint, key, WebPushCrypto.base64Url(auth));
    }
}
