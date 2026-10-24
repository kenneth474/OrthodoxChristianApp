package com.orthodox.orthodoxapp.auth;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.orthodox.orthodoxapp.account.AppUser;
import com.orthodox.orthodoxapp.account.AppUserRepository;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/**
 * AuthFlowTest: tests the whole sign-in system, end to end.
 *
 * @SpringBootTest starts the real app (all our classes, Spring Security, the
 * database) but with an in-memory database that disappears afterwards.
 * MockMvc sends pretend web requests, exactly as a browser would, without a
 * real server. MockHttpSession plays the part of the browser's session
 * cookie; the "stay signed in" cookie is passed along by hand.
 */
@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:authflow;DB_CLOSE_DELAY=-1")
@AutoConfigureMockMvc
class AuthFlowTest {

    private static final String STRONG_PASSWORD = "Vespers-at-6:30pm";

    @Autowired
    private MockMvc mvc;

    @Autowired
    private AuthService authService;

    @Autowired
    private AppUserRepository users;

    // ----- Creating an account -----

    @Test
    void aWeakPasswordIsRejectedWithEveryProblemListed() throws Exception {
        send(post("/api/auth/register"), new MockHttpSession(), """
                {"email": "weak@example.com", "password": "short", "displayName": "Weak"}""")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("Please choose a stronger password."))
                .andExpect(jsonPath("$.problems.length()").value(4));
    }

    @Test
    void registeringSignsYouInAndSigningOutSignsYouOut() throws Exception {
        MockHttpSession browser = register("anna@example.com", "(214) 555-0123");

        mvc.perform(get("/api/account").session(browser))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("anna@example.com"))
                .andExpect(jsonPath("$.phoneNumber").value("+12145550123")) // saved as typed, tidied, not checked
                .andExpect(jsonPath("$.hasPassword").value(true))
                .andExpect(jsonPath("$.admin").value(false));

        mvc.perform(post("/api/auth/sign-out").session(browser).with(csrf()))
                .andExpect(status().isOk());
        mvc.perform(get("/api/account").session(new MockHttpSession()))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void theSameEmailCantRegisterTwice() throws Exception {
        register("twice@example.com", null);
        // Capital letters and spaces don't make it a different email.
        send(post("/api/auth/register"), new MockHttpSession(), registration(" TWICE@example.com", null))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("An account with this email already exists. Try signing in instead."));
    }

    // ----- Signing in -----

    @Test
    void theRightPasswordSignsYouInWithNoCodeToType() throws Exception {
        register("plain@example.com", null);
        MockHttpSession browser = new MockHttpSession();
        send(post("/api/auth/sign-in"), browser, signIn("plain@example.com"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SIGNED_IN"))
                .andExpect(jsonPath("$.account.email").value("plain@example.com"));
        mvc.perform(get("/api/account").session(browser)).andExpect(status().isOk());
    }

    @Test
    void wrongPasswordsAreRefusedAndFiveInARowLockTheAccount() throws Exception {
        register("locked@example.com", null);
        String wrong = """
                {"email": "locked@example.com", "password": "Not-the-password-1"}""";

        for (int attempt = 1; attempt <= 5; attempt++) {
            send(post("/api/auth/sign-in"), new MockHttpSession(), wrong)
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.detail").value("Email or password is incorrect."));
        }
        // Now even the right password is refused for 15 minutes.
        send(post("/api/auth/sign-in"), new MockHttpSession(), signIn("locked@example.com"))
                .andExpect(status().isTooManyRequests());
    }

    @Test
    void anUnknownEmailGetsTheSameMessageAsAWrongPassword() throws Exception {
        send(post("/api/auth/sign-in"), new MockHttpSession(), signIn("nobody@example.com"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.detail").value("Email or password is incorrect."));
    }

    @Test
    void theOptionsSayWhetherGoogleSignInIsSetUp() throws Exception {
        // The tests run without Google keys.
        mvc.perform(get("/api/auth/options")).andExpect(jsonPath("$.google").value(false));
    }

    // ----- Staying signed in -----

    @Test
    void theStaySignedInCookieSignsYouInAgainWhenTheSessionHasGone() throws Exception {
        Cookie cookie = stayCookie(send(post("/api/auth/register"), new MockHttpSession(),
                registration("stay@example.com", null)).andExpect(status().isCreated()).andReturn().getResponse());
        assertTrue(cookie.isHttpOnly(), "page scripts must not be able to read it");
        assertEquals(StaySignedIn.LIFETIME.toSeconds(), cookie.getMaxAge());

        // A brand-new session (as after the server restarts), with only the cookie:
        MockHttpServletResponse reply = mvc.perform(get("/api/account").cookie(cookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("stay@example.com"))
                .andReturn().getResponse();
        assertNotNull(stayCookie(reply), "using the cookie renews it for another 400 days");
    }

    @Test
    void signingOutDeletesTheStaySignedInCookie() throws Exception {
        MockHttpSession browser = new MockHttpSession();
        send(post("/api/auth/register"), browser, registration("leaving@example.com", null));

        MockHttpServletResponse reply = mvc.perform(post("/api/auth/sign-out").session(browser).with(csrf()))
                .andExpect(status().isOk())
                .andReturn().getResponse();
        assertEquals(0, stayCookie(reply).getMaxAge(), "a max age of 0 tells the browser to delete it");
    }

    @Test
    void aChangedOrForgedCookieDoesntSignAnyoneIn() throws Exception {
        Cookie real = stayCookie(send(post("/api/auth/register"), new MockHttpSession(),
                registration("target@example.com", null)).andReturn().getResponse());
        String[] parts = real.getValue().split(":"); // id : expiry : signature

        // Someone edits the account number, or makes up a signature:
        String otherAccount = (Long.parseLong(parts[0]) + 1) + ":" + parts[1] + ":" + parts[2];
        String madeUp = parts[0] + ":" + parts[1] + ":" + "not-the-signature";
        for (String forged : new String[] {otherAccount, madeUp, "rubbish"}) {
            MockHttpServletResponse reply = mvc.perform(get("/api/account")
                            .cookie(new Cookie(StaySignedIn.COOKIE, forged)))
                    .andExpect(status().isUnauthorized())
                    .andReturn().getResponse();
            assertEquals(0, stayCookie(reply).getMaxAge(), "a bad cookie is deleted");
        }
    }

    @Test
    void changingThePasswordStopsOldCookiesWorking() throws Exception {
        Cookie cookie = stayCookie(send(post("/api/auth/register"), new MockHttpSession(),
                registration("newpass@example.com", null)).andReturn().getResponse());

        AppUser user = users.findByEmail("newpass@example.com").orElseThrow();
        user.setPasswordHash("{bcrypt}a-different-hash");
        users.save(user);

        mvc.perform(get("/api/account").cookie(cookie)).andExpect(status().isUnauthorized());
    }

    // ----- Google -----

    @Test
    void theFirstGoogleSignInCreatesAnAccountAndTheNextOnesFindIt() {
        AuthService.GoogleSignIn first = authService.signInWithGoogle("google-new", "New@Example.com", true, "New Person");
        assertTrue(first.newAccount());
        assertEquals("new@example.com", first.user().getEmail());
        assertEquals("New Person", first.user().getDisplayName());
        assertFalse(first.user().hasPassword());

        AuthService.GoogleSignIn again = authService.signInWithGoogle("google-new", "new@example.com", true, null);
        assertFalse(again.newAccount());
        assertEquals(first.user().getId(), again.user().getId());
    }

    @Test
    void googleSignInWithAnEmailThatHasAnAccountUsesThatAccount() throws Exception {
        register("both@example.com", null);
        AppUser registered = users.findByEmail("both@example.com").orElseThrow();

        AuthService.GoogleSignIn google = authService.signInWithGoogle("google-both", "both@example.com", true, "Both");

        assertFalse(google.newAccount(), "one email, one account");
        assertEquals(registered.getId(), google.user().getId());
        assertEquals(1, users.findAll().stream().filter(u -> u.getEmail().equals("both@example.com")).count());
        // Typed emails aren't proven, so Google's sign-in replaces the password (see the next test).
        assertTrue(google.passwordRemoved());
        // ...and you can't then make a second account with that email either.
        send(post("/api/auth/register"), new MockHttpSession(), registration("both@example.com", null))
                .andExpect(status().isConflict());
    }

    @Test
    void googleSignInTakesOverAnAccountWhoseEmailWasNeverProven() {
        // Someone creates an account with an email they don't own...
        AppUser planted = authService.register("victim@example.com", STRONG_PASSWORD, "Not Me", "214-555-0100", null);

        // ...then the real owner signs in with Google, which has verified the email.
        AppUser owner = authService.signInWithGoogle("google-123", "Victim@Example.com", true, "Real Owner").user();

        assertEquals(planted.getId(), owner.getId());
        assertFalse(owner.hasPassword(), "the stranger's password must be removed");
        assertNull(owner.getPhoneNumber(), "and their phone number");
        assertTrue(owner.isEmailVerified());
        assertEquals("google-123", owner.getGoogleId());
    }

    @Test
    void anUnverifiedEmailFromGoogleIsRefused() {
        assertThrows(AuthException.class,
                () -> authService.signInWithGoogle("google-1", "x@example.com", false, null));
    }

    // ----- Security basics -----

    @Test
    void accountDetailsNeedASignedInUser() throws Exception {
        mvc.perform(get("/api/account")).andExpect(status().isUnauthorized());
    }

    @Test
    void requestsWithoutACsrfTokenAreRefused() throws Exception {
        mvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registration("csrf@example.com", null)))
                .andExpect(status().isForbidden());
    }

    // ----- Helpers -----

    // Sends a JSON request in a "browser" session, with a valid CSRF token.
    private ResultActions send(MockHttpServletRequestBuilder request, MockHttpSession browser, String json)
            throws Exception {
        request.session(browser).with(csrf());
        if (json != null) {
            request.contentType(MediaType.APPLICATION_JSON).content(json);
        }
        return mvc.perform(request);
    }

    private MockHttpSession register(String email, String phone) throws Exception {
        MockHttpSession browser = new MockHttpSession();
        send(post("/api/auth/register"), browser, registration(email, phone))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("SIGNED_IN"));
        return browser;
    }

    // The "stay signed in" cookie a reply set (or deleted).
    private static Cookie stayCookie(MockHttpServletResponse response) {
        Cookie cookie = response.getCookie(StaySignedIn.COOKIE);
        assertNotNull(cookie, "the reply should set the stay-signed-in cookie");
        return cookie;
    }

    private static String registration(String email, String phone) {
        return """
                {"email": "%s", "password": "%s", "displayName": "Test Person", "phone": %s}"""
                .formatted(email, STRONG_PASSWORD, phone == null ? "null" : "\"" + phone + "\"");
    }

    private static String signIn(String email) {
        return """
                {"email": "%s", "password": "%s"}""".formatted(email, STRONG_PASSWORD);
    }
}
