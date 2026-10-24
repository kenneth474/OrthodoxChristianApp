package com.orthodox.orthodoxapp.auth;

import com.orthodox.orthodoxapp.account.AccountService;
import com.orthodox.orthodoxapp.account.AccountView;
import com.orthodox.orthodoxapp.account.AppStyle;
import com.orthodox.orthodoxapp.account.AppUser;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * AuthController: the web endpoints for creating accounts and signing in.
 *
 *   GET  /api/auth/csrf      -> the CSRF token pages must send with POST/PUT
 *   GET  /api/auth/options   -> what this server offers: is Google sign-in set up?
 *   POST /api/auth/register  -> create an account (then signed in)
 *   POST /api/auth/sign-in   -> email + password
 *   POST /api/auth/sign-out  -> handled by Spring Security (see SecurityConfig)
 *
 * Signing in with Google starts at /oauth2/authorization/google instead,
 * handled by Spring Security and then SocialSignInHandler.
 *
 * @RequestBody turns the JSON the page sends into one of the small records
 * below, e.g. {"email": "...", "password": "..."} -> SignInRequest.
 */
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    // phone is optional; style is the look chosen while creating the account (optional).
    public record RegisterRequest(String email, String password, String displayName, String phone, AppStyle style) {
    }

    public record SignInRequest(String email, String password) {
    }

    /** What this server offers. The sign-in page greys out the Google button until it's set up. */
    public record SignInOptions(boolean google) {
    }

    /**
     * The reply to signing in or creating an account: status "SIGNED_IN" and
     * the person's details. (The status stays, so that copies of the app from
     * before 2026-10-24, still open on someone's phone, keep working.)
     */
    public record SignInResponse(String status, AccountView account) {

        static SignInResponse signedIn(AccountView account) {
            return new SignInResponse("SIGNED_IN", account);
        }
    }

    private final AuthService authService;
    private final AccountService accountService;
    private final SessionSignIn sessionSignIn;
    private final SocialClientRegistrations social;

    public AuthController(AuthService authService, AccountService accountService, SessionSignIn sessionSignIn,
                          SocialClientRegistrations social) {
        this.authService = authService;
        this.accountService = accountService;
        this.sessionSignIn = sessionSignIn;
        this.social = social;
    }

    /** Spring Security passes in this session's CSRF token; we hand it to the page. */
    @GetMapping("/csrf")
    public Map<String, String> csrf(CsrfToken token) {
        return Map.of("headerName", token.getHeaderName(), "token", token.getToken());
    }

    @GetMapping("/options")
    public SignInOptions options() {
        return new SignInOptions(social.googleEnabled());
    }

    /** Creates an account and signs straight in. 201 = "Created". */
    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    public SignInResponse register(@RequestBody RegisterRequest body,
                                   HttpServletRequest request, HttpServletResponse response) {
        AppUser user = authService.register(
                body.email(), body.password(), body.displayName(), body.phone(), body.style());
        sessionSignIn.signIn(user, request, response);
        return SignInResponse.signedIn(accountService.view(user));
    }

    /** Email and password. Right: signed in. Wrong: 401 (see AuthService.checkPassword). */
    @PostMapping("/sign-in")
    public SignInResponse signIn(@RequestBody SignInRequest body,
                                 HttpServletRequest request, HttpServletResponse response) {
        AppUser user = authService.checkPassword(body.email(), body.password());
        sessionSignIn.signIn(user, request, response);
        return SignInResponse.signedIn(accountService.view(user));
    }
}
