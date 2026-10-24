package com.orthodox.orthodoxapp.account;

/**
 * How big the app's text is. The pages scale every font by this much
 * (see frontend/src/styles/theme.css, data-text-size).
 *
 * Until 2026-10-24 the choices were NORMAL, LARGE and LARGER. DatabaseFixes
 * turned every saved LARGER into LARGE when the names changed.
 */
public enum TextSize {
    SMALL,   // about 10% smaller
    NORMAL,
    LARGE    // about 20% bigger
}
