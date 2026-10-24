package com.orthodox.orthodoxapp.account;

import com.orthodox.orthodoxapp.admin.Admins;
import com.orthodox.orthodoxapp.auth.AuthException;
import com.orthodox.orthodoxapp.auth.PhoneNumbers;
import java.time.DateTimeException;
import java.time.LocalDate;
import java.time.MonthDay;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * AccountService: the rules for changing your own account: your profile,
 * appearance and phone number. (Photos have their own ProfilePhotoService.)
 */
@Service
public class AccountService {

    private final AppUserRepository users;
    private final Admins admins;

    public AccountService(AppUserRepository users, Admins admins) {
        this.users = users;
        this.admins = admins;
    }

    /** Loads an account. 401 if it no longer exists (e.g. the database was reset). */
    public AppUser get(Long userId) {
        if (userId == null) {
            throw new AuthException(HttpStatus.UNAUTHORIZED, "Please sign in again.");
        }
        return users.findById(userId)
                .orElseThrow(() -> new AuthException(HttpStatus.UNAUTHORIZED, "Please sign in again."));
    }

    /** What the person's own pages may see about their account (see AccountView). */
    public AccountView view(AppUser user) {
        return AccountView.from(user, admins.isAdmin(user));
    }

    /**
     * Saves the profile card: name (required), patron saint and their feast
     * day (your name day), and home parish (all optional; blank removes them).
     *
     * @param nameDay the feast day as "12-06" (month-day); an older copy of the
     *                app may still send a full date ("2026-12-06"), whose year is dropped
     */
    @Transactional
    public AppUser updateProfile(Long userId, String displayName, String patronSaint,
                                 String nameDay, String homeParish) {
        String name = displayName == null ? "" : displayName.strip();
        if (name.isEmpty() || name.length() > 60) {
            throw new AuthException(HttpStatus.BAD_REQUEST, "Enter your name (up to 60 characters).");
        }
        AppUser user = get(userId);
        user.setDisplayName(name);
        user.setPatronSaint(optionalText(patronSaint, 100, "Patron saint"));
        user.setNameDay(normalizeNameDay(nameDay));
        user.setHomeParish(optionalText(homeParish, 120, "Home parish"));
        return users.save(user);
    }

    @Transactional
    public AppUser updateAppearance(Long userId, TextSize textSize, Theme theme, AppStyle style) {
        AppUser user = get(userId);
        user.setTextSize(textSize);
        user.setTheme(theme);
        // An older copy of the app may not send a style yet: keep the current one.
        if (style != null) {
            user.setStyle(style);
        }
        return users.save(user);
    }

    /** Adds, changes or removes (blank) the phone number. It isn't checked with a code. */
    @Transactional
    public AppUser updatePhone(Long userId, String phone) {
        AppUser user = get(userId);
        user.setPhoneNumber((phone == null || phone.isBlank()) ? null : PhoneNumbers.normalize(phone));
        return users.save(user);
    }

    // Blank -> null (removed); too long -> a friendly error.
    private static String optionalText(String value, int maxLength, String label) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String text = value.strip();
        if (text.length() > maxLength) {
            throw new AuthException(HttpStatus.BAD_REQUEST, label + " can be up to " + maxLength + " characters.");
        }
        return text;
    }

    // "12-06" (or "2026-12-06") -> "12-06". MonthDay is Java's type for a
    // date without a year, which is exactly what a feast day is.
    static String normalizeNameDay(String input) {
        if (input == null || input.isBlank()) {
            return null;
        }
        String text = input.strip();
        try {
            MonthDay day = text.length() == 10
                    ? MonthDay.from(LocalDate.parse(text))
                    : MonthDay.parse("--" + text); // MonthDay's own format is "--12-06"
            return "%02d-%02d".formatted(day.getMonthValue(), day.getDayOfMonth());
        } catch (DateTimeException ex) {
            throw new AuthException(HttpStatus.BAD_REQUEST, "Choose a real month and day for the feast day.");
        }
    }
}
