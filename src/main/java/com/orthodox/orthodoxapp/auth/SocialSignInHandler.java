package com.orthodox.orthodoxapp.auth;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import java.io.IOException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.oidc.StandardClaimNames;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.stereotype.Component;

/**
 * SocialSignInHandler: runs after Google has confirmed who you are.
 *
 * Spring Security has already checked Google's ID token. Here we find or
 * create your AppUser (AuthService.signInWithGoogle), sign you in the same
 * way a password sign-in does, and send you on:
 *   - a brand-new account: to Profile, which welcomes you and asks (if you
 *     like) for your phone number
 *   - an account whose password Google's sign-in replaced: to Profile, which
 *     explains that you now sign in with Google
 *   - otherwise: Home
 */
@Component
public class SocialSignInHandler implements AuthenticationSuccessHandler {

    private final AuthService authService;
    private final SessionSignIn sessionSignIn;

    public SocialSignInHandler(AuthService authService, SessionSignIn sessionSignIn) {
        this.authService = authService;
        this.sessionSignIn = sessionSignIn;
    }

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request, HttpServletResponse response,
                                        Authentication authentication) throws IOException {
        OAuth2AuthenticationToken token = (OAuth2AuthenticationToken) authentication;
        OidcUser person = (OidcUser) token.getPrincipal();

        // email_verified: whether Google has confirmed the address. String.valueOf
        // copes with it arriving as true/false or as the text "true"/"false".
        boolean emailVerified = "true".equals(String.valueOf(person.getClaims().get(StandardClaimNames.EMAIL_VERIFIED)));

        try {
            AuthService.GoogleSignIn result = authService.signInWithGoogle(
                    person.getSubject(),
                    person.getEmail(),
                    emailVerified,
                    person.getFullName());
            sessionSignIn.signIn(result.user(), request, response);
            if (result.newAccount()) {
                response.sendRedirect("/profile?welcome=google");
            } else if (result.passwordRemoved()) {
                response.sendRedirect("/profile?google=linked");
            } else {
                response.sendRedirect("/");
            }
        } catch (AuthException ex) {
            // e.g. no verified email: undo the half-finished sign-in.
            SecurityContextHolder.clearContext();
            HttpSession session = request.getSession(false);
            if (session != null) {
                session.invalidate();
            }
            response.sendRedirect("/sign-in?error=social");
        }
    }
}
