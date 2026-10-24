package com.orthodox.orthodoxapp.admin;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.orthodox.orthodoxapp.account.AppUser;
import com.orthodox.orthodoxapp.auth.AuthService;
import com.orthodox.orthodoxapp.auth.SignedInUser;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

/**
 * The Members page: only administrators see it, and only once Google has
 * proven their email. (app.admin.emails is set just for this test.)
 */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:members;DB_CLOSE_DELAY=-1",
        "app.admin.emails=Boss@Example.com, helper@example.com"})
@AutoConfigureMockMvc
class MembersTest {

    // JsonPath filters: "the members whose email is ..."
    private static final String MEMBER = "$.members[?(@.email == 'member@example.com')]";
    private static final String BOSS = "$.members[?(@.email == 'boss@example.com')]";

    @Autowired
    private MockMvc mvc;

    @Autowired
    private AuthService authService;

    @Test
    void anAdministratorSeesEveryAccountButNoPasswords() throws Exception {
        authService.register("member@example.com", "Vespers-at-6:30pm", "A Member", "214-555-0142", null);
        AppUser boss = authService.signInWithGoogle("google-boss", "boss@example.com", true, "The Boss").user();

        mvc.perform(get("/api/account").with(signedInAs(boss)))
                .andExpect(jsonPath("$.admin").value(true));

        // (Other tests here add accounts too, so look each one up by its email.)
        mvc.perform(get("/api/admin/members").with(signedInAs(boss)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.count").value(greaterThanOrEqualTo(2)))
                .andExpect(jsonPath(MEMBER + ".name").value(hasItem("A Member")))
                .andExpect(jsonPath(MEMBER + ".phoneNumber").value(hasItem("+12145550142")))
                .andExpect(jsonPath(MEMBER + ".password").value(hasItem(true)))
                .andExpect(jsonPath(MEMBER + ".google").value(hasItem(false)))
                .andExpect(jsonPath(BOSS + ".google").value(hasItem(true)))
                .andExpect(content().string(not(containsString("bcrypt")))); // no password hashes, ever
    }

    @Test
    void theRightEmailWithoutGoogleIsNotEnough() throws Exception {
        // Anyone could type an administrator's email when creating an account.
        MockHttpSession browser = new MockHttpSession();
        mvc.perform(post("/api/auth/register").session(browser).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email": "helper@example.com", "password": "Vespers-at-6:30pm", "displayName": "Imposter"}"""))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.account.admin").value(false));
        mvc.perform(get("/api/admin/members").session(browser)).andExpect(status().isForbidden());
    }

    @Test
    void ordinaryMembersAndVisitorsCantSeeTheList() throws Exception {
        AppUser someone = authService.signInWithGoogle("google-someone", "someone@example.com", true, "Someone").user();
        mvc.perform(get("/api/admin/members").with(signedInAs(someone))).andExpect(status().isForbidden());
        mvc.perform(get("/api/admin/members")).andExpect(status().isUnauthorized());
    }

    // A request from a browser where this account is signed in.
    private static RequestPostProcessor signedInAs(AppUser user) {
        return authentication(UsernamePasswordAuthenticationToken.authenticated(
                new SignedInUser(user.getId(), user.getEmail()), null, AuthorityUtils.createAuthorityList("ROLE_USER")));
    }
}
