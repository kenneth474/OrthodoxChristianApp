package com.orthodox.orthodoxapp.account;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.ColumnDefault;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;

/**
 * AppUser: one person's account, stored as one row in the "app_user" table.
 *
 * WHAT IS AN ENTITY?
 * @Entity tells JPA (Hibernate) "objects of this class are saved in a database
 * table". Each field becomes a column. We never write SQL for this: the
 * AppUserRepository interface loads and saves AppUser objects for us.
 *
 * Entities can't be records, because JPA needs to create an empty object and
 * then fill in its fields. That's why this class has a no-argument constructor,
 * plus getters and setters.
 *
 * WHAT WE NEVER STORE
 * The password itself. We store only a BCrypt "hash": a scrambled version that
 * can check a password but can't be turned back into it. If the database ever
 * leaked, the passwords would still be safe.
 */
@Entity
@Table(name = "app_user") // "user" is a reserved word in SQL, so we avoid it
public class AppUser {

    // @Id = the primary key. IDENTITY = the database numbers rows 1, 2, 3...
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // Always stored in lowercase, so "Kenneth@Gmail.com" and "kenneth@gmail.com" match.
    @Column(nullable = false, unique = true)
    private String email;

    // BCrypt hash of the password, or null for accounts that only use Google.
    private String passwordHash;

    @Column(nullable = false)
    private String displayName;

    // Optional, in international "E.164" format, e.g. +12145550123.
    // Not checked with a code: we simply keep the number the person gives us.
    private String phoneNumber;

    // True once Google has confirmed that this person owns the email (see
    // AuthService.signInWithGoogle). Emails typed when creating an account
    // aren't checked, so they stay false.
    // @ColumnDefault: the value the database gives existing rows when a
    // column is added later. A number or true/false column must always
    // have one, or adding it to a table that already has accounts fails
    // (that broke signing in on 2026-10-24; EntityColumnsTest checks it).
    @ColumnDefault("false")
    private boolean emailVerified;

    // The unchanging account ID Google gives us ("sub" in its tokens).
    @Column(unique = true)
    private String googleId;

    // Protection against password guessing: see recordFailedSignIn below.
    @ColumnDefault("0")
    private int failedSignIns;
    private Instant lockedUntil;

    // (Two-step verification was taken out on 2026-10-24. Its old columns,
    // such as two_factor_method, are still in the database but unused; see
    // DatabaseFixes for how new accounts get by without them.)

    // ----- Profile and preferences (added in Session 2) -----
    // These columns were added to a table that may already have rows, so
    // they're allowed to be empty (null). The getters below give a sensible
    // default for older accounts that don't have a value yet.

    private String patronSaint;

    // Your patron saint's feast day (your "name day"), as "MM-DD": a month
    // and day without a year, e.g. "12-06" for St Nicholas.
    private String nameDay;

    // Free text for now; becomes a link to a Parish once the parish finder exists.
    private String homeParish;

    // When the profile photo last changed (null = no photo). The photo itself
    // lives in its own table (ProfilePhoto), so loading an account stays fast.
    private Instant photoUpdatedAt;

    @Enumerated(EnumType.STRING)
    private TextSize textSize;

    @Enumerated(EnumType.STRING)
    private Theme theme;

    // Which of the app's five looks (see AppStyle). Null = never chosen.
    @Enumerated(EnumType.STRING)
    private AppStyle style;

    // ----- Notifications (added 2026-10-24; see notify/NotificationService) -----
    // The daily notification (off until chosen) and the name-day greeting
    // (on), both at notifyTime in the person's own time zone, e.g.
    // "America/Chicago" (from their browser). lastDailySent/lastNameDaySent
    // remember the last day each went out, so nothing is sent twice.
    @ColumnDefault("false")
    private boolean notifyDaily;
    @ColumnDefault("true")
    private boolean notifyNameDay = true;
    private LocalTime notifyTime;
    private String timeZone;
    private LocalDate lastDailySent;
    private LocalDate lastNameDaySent;

    @Column(nullable = false)
    private Instant createdAt;

    /** For JPA only: it creates an empty object, then fills in the fields. */
    protected AppUser() {
    }

    public AppUser(String email, String displayName) {
        this.email = email;
        this.displayName = displayName;
        this.createdAt = Instant.now();
    }

    // ----- Behaviour -----

    /** False for accounts that only sign in with Google. */
    public boolean hasPassword() {
        return passwordHash != null;
    }

    /** True while the account is locked after too many wrong passwords. */
    public boolean isLocked(Instant now) {
        return lockedUntil != null && now.isBefore(lockedUntil);
    }

    /**
     * Counts a wrong password. After maxAttempts in a row, the account locks
     * for lockTime, so a program can't try thousands of passwords.
     */
    public void recordFailedSignIn(Instant now, int maxAttempts, Duration lockTime) {
        failedSignIns++;
        if (failedSignIns >= maxAttempts) {
            lockedUntil = now.plus(lockTime);
            failedSignIns = 0;
        }
    }

    public void recordSuccessfulSignIn() {
        failedSignIns = 0;
        lockedUntil = null;
    }

    // ----- Getters and setters -----

    public Long getId() { return id; }

    public String getEmail() { return email; }

    public String getPasswordHash() { return passwordHash; }
    public void setPasswordHash(String passwordHash) { this.passwordHash = passwordHash; }

    public String getDisplayName() { return displayName; }
    public void setDisplayName(String displayName) { this.displayName = displayName; }

    public String getPhoneNumber() { return phoneNumber; }
    public void setPhoneNumber(String phoneNumber) { this.phoneNumber = phoneNumber; }

    public boolean isEmailVerified() { return emailVerified; }
    public void setEmailVerified(boolean emailVerified) { this.emailVerified = emailVerified; }

    public String getGoogleId() { return googleId; }
    public void setGoogleId(String googleId) { this.googleId = googleId; }

    public Instant getCreatedAt() { return createdAt; }

    public String getPatronSaint() { return patronSaint; }
    public void setPatronSaint(String patronSaint) { this.patronSaint = patronSaint; }

    public String getNameDay() { return nameDay; }
    public void setNameDay(String nameDay) { this.nameDay = nameDay; }

    public String getHomeParish() { return homeParish; }
    public void setHomeParish(String homeParish) { this.homeParish = homeParish; }

    public Instant getPhotoUpdatedAt() { return photoUpdatedAt; }
    public void setPhotoUpdatedAt(Instant photoUpdatedAt) { this.photoUpdatedAt = photoUpdatedAt; }

    public TextSize getTextSize() { return textSize == null ? TextSize.NORMAL : textSize; }
    public void setTextSize(TextSize textSize) { this.textSize = textSize; }

    public Theme getTheme() { return theme == null ? Theme.SYSTEM : theme; }
    public void setTheme(Theme theme) { this.theme = theme; }

    public AppStyle getStyle() { return style == null ? AppStyle.ILLUMINATED : style; }
    public void setStyle(AppStyle style) { this.style = style; }

    public boolean isNotifyDaily() { return notifyDaily; }
    public void setNotifyDaily(boolean notifyDaily) { this.notifyDaily = notifyDaily; }

    public boolean isNotifyNameDay() { return notifyNameDay; }
    public void setNotifyNameDay(boolean notifyNameDay) { this.notifyNameDay = notifyNameDay; }

    // 7:00 in the morning until chosen.
    public LocalTime getNotifyTime() { return notifyTime == null ? LocalTime.of(7, 0) : notifyTime; }
    public void setNotifyTime(LocalTime notifyTime) { this.notifyTime = notifyTime; }

    // Central time (most of the Archdiocese's parishes) until the browser tells us.
    public ZoneId getTimeZone() { return timeZone == null ? ZoneId.of("America/Chicago") : ZoneId.of(timeZone); }
    public void setTimeZone(ZoneId timeZone) { this.timeZone = timeZone.getId(); }

    public LocalDate getLastDailySent() { return lastDailySent; }
    public void setLastDailySent(LocalDate lastDailySent) { this.lastDailySent = lastDailySent; }

    public LocalDate getLastNameDaySent() { return lastNameDaySent; }
    public void setLastNameDaySent(LocalDate lastNameDaySent) { this.lastNameDaySent = lastNameDaySent; }
}
