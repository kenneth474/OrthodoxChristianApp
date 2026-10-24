package com.orthodox.orthodoxapp.admin;

import com.orthodox.orthodoxapp.account.AppUser;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Admins: who may see the Members page (the list of everyone's accounts).
 *
 * The administrators' emails come from a setting, so they're never written
 * in the code (which is public on GitHub):
 *   on your computer: app.admin.emails=you@gmail.com   (secrets.properties)
 *   online:           APP_ADMIN_EMAILS = you@gmail.com (Render > Environment)
 * Several people? Separate the emails with commas.
 *
 * Being on the list isn't enough: the account must also have signed in with
 * Google at least once, which proves it owns that email. Emails typed when
 * creating an account aren't checked, so otherwise anyone could create an
 * account with an administrator's email (for example on a new, empty
 * database) and read every member's details.
 */
@Component
public class Admins {

    private final Set<String> emails;

    // ${app.admin.emails:} = the setting, or nothing ("") if it isn't set.
    // Spring splits "a@x.com,b@y.com" into a list for us.
    public Admins(@Value("${app.admin.emails:}") List<String> emails) {
        this.emails = emails.stream()
                .map(email -> email.strip().toLowerCase(Locale.ROOT))
                .filter(email -> !email.isEmpty())
                .collect(Collectors.toUnmodifiableSet());
    }

    public boolean isAdmin(AppUser user) {
        return user.isEmailVerified() && emails.contains(user.getEmail());
    }
}
