package com.orthodox.orthodoxapp;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.forwardedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Every screen of the React app can be opened directly (a bookmark or a
 * refresh): the server hands back index.html, and React shows the screen.
 * (Same settings as ProfileTest, so Spring reuses that test's app instead of
 * starting another.)
 */
@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:profile;DB_CLOSE_DELAY=-1")
@AutoConfigureMockMvc
class SpaControllerTest {

    @Autowired
    private MockMvc mvc;

    @Test
    void reactScreensAreServedTheAppPage() throws Exception {
        for (String address : new String[] {"/bible", "/bible/PSA", "/bible/PSA/50", "/prayers",
                "/prayers/morning", "/church", "/church/dioceses", "/profile", "/profile/members", "/sign-in"}) {
            mvc.perform(get(address))
                    .andExpect(status().isOk())
                    .andExpect(forwardedUrl("/index.html"));
        }
    }

    @Test
    void thePrivacyPolicyIsPublic() throws Exception {
        mvc.perform(get("/privacy"))
                .andExpect(status().isOk())
                .andExpect(forwardedUrl("/privacy.html"));
    }

    @Test
    void theApiIsNotAffected() throws Exception {
        mvc.perform(get("/api/bible/PSA/50")).andExpect(status().isOk());
        mvc.perform(get("/api/account")).andExpect(status().isUnauthorized());
    }
}
