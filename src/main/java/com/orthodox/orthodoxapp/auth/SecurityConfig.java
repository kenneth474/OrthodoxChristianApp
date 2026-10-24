package com.orthodox.orthodoxapp.auth;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.security.web.authentication.logout.HttpStatusReturningLogoutSuccessHandler;
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher;

/**
 * SecurityConfig: the security rules for the whole app.
 *
 * Spring Security checks every request BEFORE it reaches a controller, using
 * a chain of "filters" (the SecurityFilterChain built below). It:
 *   - decides which addresses need a signed-in user
 *   - loads the signed-in user from the session, or from the "stay signed
 *     in" cookie when the session has gone (see StaySignedIn)
 *   - blocks CSRF attacks (see below)
 *   - handles signing out, and Google sign-in
 *
 * CSRF ("cross-site request forgery"): a bad website could make your browser
 * secretly send a request to our app, with your session cookie attached.
 * To block that, every POST/PUT must also carry a secret token that only our
 * own pages can read (they get it from /api/auth/csrf; see frontend/src/api.ts).
 */
@Configuration
@EnableConfigurationProperties(AuthProperties.class)
public class SecurityConfig {

    /**
     * Hashes passwords with BCrypt. BCrypt is deliberately slow (about a tenth
     * of a second per check), which is nothing for one sign-in but makes
     * guessing billions of passwords impractical. Hashes are saved with a
     * "{bcrypt}" label, so we could move to a newer method later.
     */
    @Bean
    PasswordEncoder passwordEncoder() {
        return PasswordEncoderFactories.createDelegatingPasswordEncoder();
    }

    @Bean
    SocialClientRegistrations socialClientRegistrations(AuthProperties properties) {
        return new SocialClientRegistrations(properties);
    }

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http,
                                            SocialClientRegistrations social,
                                            SocialSignInHandler socialSignInHandler,
                                            StaySignedIn staySignedIn) throws Exception {
        http
            // WHO CAN SEE WHAT. Rules are checked top to bottom; the first match wins.
            // Anything not listed needs a signed-in user ("deny by default"), so
            // a new page or endpoint is private until we decide otherwise.
            .authorizeHttpRequests(auth -> auth
                // The React app: its page, its files (built by Vite into assets/),
                // the home-screen icons, the prayer book data, the service worker
                // that shows notifications, and the privacy policy. Every screen's
                // address is public too; screens that need an account ask the API,
                // which answers 401 when you're not signed in.
                .requestMatchers("/", "/index.html", "/assets/**", "/icons/**", "/data/**",
                        "/manifest.webmanifest", "/favicon.ico", "/sw.js", "/privacy", "/privacy.html",
                        "/error").permitAll()
                .requestMatchers("/bible", "/bible/**", "/prayers", "/prayers/**", "/church", "/church/**",
                        "/profile", "/profile/**", "/sign-in").permitAll()
                .requestMatchers("/api/today", "/api/day/**", "/api/bible/**", "/api/auth/**").permitAll()
                .anyRequest().authenticated())

            // Stay signed in: when a request has no signed-in session, check
            // the long-lasting cookie (StaySignedIn). The key lets Spring
            // Security confirm that those sign-ins were made by StaySignedIn.
            .rememberMe(remember -> remember
                .rememberMeServices(staySignedIn)
                .key(staySignedIn.tokenKey()))

            // For /api/... addresses, "not signed in" is a plain 401 error (the
            // page's JavaScript then shows the sign-in page) instead of a redirect.
            .exceptionHandling(ex -> ex.defaultAuthenticationEntryPointFor(
                    new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED),
                    PathPatternRequestMatcher.withDefaults().matcher("/api/**")))

            // POST /api/auth/sign-out ends the session, deletes the "stay signed
            // in" cookie (StaySignedIn.logout) and replies 200 OK.
            .logout(logout -> logout
                .logoutRequestMatcher(PathPatternRequestMatcher.withDefaults().matcher(HttpMethod.POST, "/api/auth/sign-out"))
                .logoutSuccessHandler(new HttpStatusReturningLogoutSuccessHandler()));

        // Google sign-in, only if its keys are set (secrets.properties, or Render's Environment).
        if (social.googleEnabled()) {
            http.oauth2Login(oauth -> oauth
                .loginPage("/sign-in")   // the React app's sign-in screen
                .successHandler(socialSignInHandler)
                .failureUrl("/sign-in?error=social"));
        }
        return http.build();
    }
}
