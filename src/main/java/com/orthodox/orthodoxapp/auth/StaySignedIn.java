package com.orthodox.orthodoxapp.auth;

import com.orthodox.orthodoxapp.account.AppUser;
import com.orthodox.orthodoxapp.account.AppUserRepository;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Optional;
import java.util.UUID;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.security.authentication.RememberMeAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.web.authentication.RememberMeServices;
import org.springframework.security.web.authentication.logout.LogoutHandler;
import org.springframework.stereotype.Component;

/**
 * StaySignedIn: keeps you signed in until you tap "Sign out".
 *
 * THE PROBLEM
 * Signing in marks your session as yours (see SessionSignIn), but sessions
 * only live in the server's memory. A session ends after 30 minutes without
 * a visit, and whenever the server restarts. On Render's free plan the
 * server sleeps after 15 quiet minutes, so everyone kept being signed out.
 *
 * THE FIX: A SECOND, LONG-LASTING COOKIE
 * Signing in also gives your browser a "stay-signed-in" cookie such as
 *     7:1834000000:mJ3k...
 * meaning: account 7, good until a date (in seconds since 1970), and a
 * signature. The signature is worked out from the other two parts with a
 * secret key only the server knows (a ServerSecret), using HMAC-SHA256, so
 * nobody can make one up or change the account number in it.
 *
 * When a request for the app's data (/api/...) arrives without a signed-in
 * session, Spring Security asks autoLogin() below to check the cookie. If
 * the signature is right, the date hasn't passed and the account still
 * exists, you're quietly signed in again, with a new session, and the
 * cookie is renewed for another 400 days. So you stay signed in for good,
 * unless you don't open the app for more than a year (400 days is the
 * longest browsers keep a cookie).
 *
 * Signing out deletes the cookie (logout, below). The signature also covers
 * your password hash, so when a password changes, or is removed because
 * Google took over an account (AuthService.signInWithGoogle), all older
 * cookies stop working.
 *
 * Spring Security calls these methods because SecurityConfig registers this
 * class with http.rememberMe(...) ("remember me" is the usual name for this).
 */
@Component
public class StaySignedIn implements RememberMeServices, LogoutHandler {

    static final String COOKIE = "stay-signed-in";
    static final Duration LIFETIME = Duration.ofDays(400);
    private static final String SECRET_NAME = "stay-signed-in";

    private final AppUserRepository users;
    private final SecretKeySpec signingKey;

    // Spring Security double-checks that a "remember me" sign-in was made by
    // us, with this key. It never leaves the server, so a new one each start is fine.
    private final String tokenKey = UUID.randomUUID().toString();

    public StaySignedIn(AppUserRepository users, ServerSecretRepository secrets) {
        this.users = users;
        // Load the signing secret, or make one the very first time.
        String secret = secrets.findById(SECRET_NAME)
                .orElseGet(() -> secrets.save(new ServerSecret(SECRET_NAME, newSecret())))
                .getSecret();
        this.signingKey = new SecretKeySpec(Base64.getDecoder().decode(secret), "HmacSHA256");
    }

    /** The key SecurityConfig hands Spring Security, to check our sign-ins with. */
    String tokenKey() {
        return tokenKey;
    }

    /** Gives the browser a new cookie for this account, good for 400 days. */
    public void remember(AppUser user, HttpServletRequest request, HttpServletResponse response) {
        long expires = Instant.now().plus(LIFETIME).getEpochSecond();
        String value = user.getId() + ":" + expires + ":" + sign(user, expires);
        setCookie(value, LIFETIME, request, response);
    }

    /**
     * No signed-in session: is there a good cookie? Returns who it belongs
     * to, or null (and deletes a cookie that's wrong, too old, or for an
     * account that no longer exists).
     */
    @Override
    public Authentication autoLogin(HttpServletRequest request, HttpServletResponse response) {
        // Pages, pictures and scripts don't need to know who you are; only
        // the app's data does. (Otherwise one page load would sign in again
        // for every file it loads.)
        if (!request.getRequestURI().startsWith("/api/")) {
            return null;
        }
        String value = cookieValue(request);
        if (value == null) {
            return null;
        }
        Optional<AppUser> user = check(value);
        if (user.isEmpty()) {
            forget(request, response);
            return null;
        }
        remember(user.get(), request, response); // another 400 days from today
        return new RememberMeAuthenticationToken(tokenKey,
                new SignedInUser(user.get().getId(), user.get().getEmail()),
                AuthorityUtils.createAuthorityList("ROLE_USER"));
    }

    /** "Sign out": delete the cookie. */
    @Override
    public void logout(HttpServletRequest request, HttpServletResponse response, Authentication authentication) {
        forget(request, response);
    }

    // Spring Security also calls these two around its own sign-in steps
    // (Google's, for us). Our sign-ins call remember() instead, and a Google
    // sign-in that goes wrong shouldn't sign you out, so they do nothing.
    @Override
    public void loginFail(HttpServletRequest request, HttpServletResponse response) {
    }

    @Override
    public void loginSuccess(HttpServletRequest request, HttpServletResponse response,
                             Authentication successfulAuthentication) {
    }

    // ----- Checking a cookie -----

    // "7:1834000000:signature" -> account 7, if everything checks out.
    private Optional<AppUser> check(String value) {
        String[] parts = value.split(":");
        if (parts.length != 3) {
            return Optional.empty();
        }
        long userId;
        long expires;
        try {
            userId = Long.parseLong(parts[0]);
            expires = Long.parseLong(parts[1]);
        } catch (NumberFormatException ex) {
            return Optional.empty();
        }
        if (Instant.now().getEpochSecond() > expires) {
            return Optional.empty(); // too old
        }
        // MessageDigest.isEqual takes the same time however much of the
        // signature matches, so timing the reply gives nothing away.
        return users.findById(userId).filter(user -> MessageDigest.isEqual(
                sign(user, expires).getBytes(StandardCharsets.US_ASCII),
                parts[2].getBytes(StandardCharsets.US_ASCII)));
    }

    // HMAC-SHA256 of "id:expires:passwordHash", with the server's secret key.
    private String sign(AppUser user, long expires) {
        String password = user.getPasswordHash() == null ? "" : user.getPasswordHash();
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(signingKey);
            byte[] signature = mac.doFinal(
                    (user.getId() + ":" + expires + ":" + password).getBytes(StandardCharsets.UTF_8));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(signature);
        } catch (GeneralSecurityException ex) {
            throw new IllegalStateException("This Java has no HmacSHA256", ex);
        }
    }

    // ----- The cookie itself -----

    private static String cookieValue(HttpServletRequest request) {
        if (request.getCookies() == null) {
            return null;
        }
        for (Cookie cookie : request.getCookies()) {
            if (COOKIE.equals(cookie.getName())) {
                return cookie.getValue();
            }
        }
        return null;
    }

    private static void forget(HttpServletRequest request, HttpServletResponse response) {
        setCookie("", Duration.ZERO, request, response); // a max age of 0 deletes it
    }

    /**
     * HttpOnly: the page's scripts can't read it, so a bad script can't steal
     * it. Secure: only sent over https (online). SameSite=Lax: not sent with
     * requests that other websites make in the background.
     */
    private static void setCookie(String value, Duration maxAge, HttpServletRequest request,
                                  HttpServletResponse response) {
        ResponseCookie cookie = ResponseCookie.from(COOKIE, value)
                .path("/")
                .maxAge(maxAge)
                .httpOnly(true)
                .secure(request.isSecure())
                .sameSite("Lax")
                .build();
        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
    }

    private static String newSecret() {
        byte[] bytes = new byte[32];
        new SecureRandom().nextBytes(bytes);
        return Base64.getEncoder().encodeToString(bytes);
    }
}
