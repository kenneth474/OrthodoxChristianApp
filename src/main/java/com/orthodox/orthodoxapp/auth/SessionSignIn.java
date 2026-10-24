package com.orthodox.orthodoxapp.auth;

import com.orthodox.orthodoxapp.account.AppUser;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.stereotype.Component;

/**
 * SessionSignIn: signs a person in, after a right password or after Google.
 *
 * HOW SIGNING IN WORKS
 * The web is "stateless": each request arrives on its own. To remember who
 * you are, the server gives your browser a session cookie (JSESSIONID), a
 * random ID that points to a small storage area on the server: your session.
 * Signing in means writing "this session belongs to account 7" into it.
 * Spring Security then reads that on every later request.
 *
 * Sessions end when the server restarts, so signing in also sets the
 * long-lasting "stay signed in" cookie (see StaySignedIn), which starts a
 * new session for you whenever the old one has gone.
 */
@Component
public class SessionSignIn {

    // Where Spring Security keeps the signed-in user: inside the HTTP session.
    private final SecurityContextRepository contextRepository = new HttpSessionSecurityContextRepository();

    private final StaySignedIn staySignedIn;

    public SessionSignIn(StaySignedIn staySignedIn) {
        this.staySignedIn = staySignedIn;
    }

    /** Signs this person in: for this session, and with a cookie that keeps them signed in. */
    public void signIn(AppUser user, HttpServletRequest request, HttpServletResponse response) {
        request.getSession(true);

        // Give the session a brand-new ID. This blocks "session fixation": an
        // attacker who planted a session ID in your browser before you signed
        // in can't use it afterwards.
        request.changeSessionId();

        // An "Authentication" is Spring Security's record of who is signed in
        // and what they're allowed to do (ROLE_USER = an ordinary member).
        var authentication = UsernamePasswordAuthenticationToken.authenticated(
                new SignedInUser(user.getId(), user.getEmail()),
                null, // no need to keep the password around
                AuthorityUtils.createAuthorityList("ROLE_USER"));

        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(authentication);
        SecurityContextHolder.setContext(context);              // for the rest of this request
        contextRepository.saveContext(context, request, response); // for later requests

        staySignedIn.remember(user, request, response);
    }
}
