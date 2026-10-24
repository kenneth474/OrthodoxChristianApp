package com.orthodox.orthodoxapp.account;

/**
 * AccountView: what the frontend is allowed to see about the signed-in user.
 *
 * We never send the AppUser entity itself as JSON: it holds the password hash
 * and lockout counters. This record copies only the safe, useful fields.
 * (The same idea as LiturgicalDay vs OrthocalDay: a clean "output" format.)
 *
 * photoVersion changes whenever the photo does (null = no photo). Pages add
 * it to the photo's address (/api/account/photo?v=...) so the browser fetches
 * the new picture instead of showing an old saved copy.
 *
 * admin = this person may open the Members page (see admin/Admins.java).
 * notifyTime is "07:30" (24-hour), in the person's own time zone.
 */
public record AccountView(
        String email,
        String displayName,
        String phoneNumber,
        boolean hasPassword,
        boolean googleLinked,
        String patronSaint,
        String nameDay,
        String homeParish,
        Long photoVersion,
        TextSize textSize,
        Theme theme,
        AppStyle style,
        boolean admin,
        boolean notifyDaily,
        boolean notifyNameDay,
        String notifyTime) {

    public static AccountView from(AppUser user, boolean admin) {
        return new AccountView(
                user.getEmail(),
                user.getDisplayName(),
                user.getPhoneNumber(),
                user.hasPassword(),
                user.getGoogleId() != null,
                user.getPatronSaint(),
                user.getNameDay(),
                user.getHomeParish(),
                user.getPhotoUpdatedAt() == null ? null : user.getPhotoUpdatedAt().toEpochMilli(),
                user.getTextSize(),
                user.getTheme(),
                user.getStyle(),
                admin,
                user.isNotifyDaily(),
                user.isNotifyNameDay(),
                user.getNotifyTime().toString());
    }
}
