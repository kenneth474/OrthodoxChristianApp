package com.orthodox.orthodoxapp.auth;

import java.util.regex.Pattern;
import org.springframework.http.HttpStatus;

/**
 * PhoneNumbers: tidies up phone numbers people type.
 *
 * People write numbers in many ways: "(214) 555-0123", "214.555.0123",
 * "+1 214 555 0123". We save every number in one exact format, called
 * E.164: a "+", the country code, then the number with no spaces:
 * +12145550123. (It's also what text-message services want, if the app
 * ever sends texts.)
 *
 * Numbers without a "+" are treated as US numbers, since most of the
 * Antiochian Archdiocese is in the US. Other countries start with "+".
 */
public final class PhoneNumbers {

    // E.164: "+", a first digit 1-9, then 7 to 14 more digits.
    // In a regular expression, \d means "a digit" and {7,14} means "7 to 14 of them".
    private static final Pattern E164 = Pattern.compile("\\+[1-9]\\d{7,14}");

    private PhoneNumbers() {
    }

    /**
     * Turns what the person typed into E.164 format.
     *
     * @throws AuthException (400) if it doesn't look like a phone number
     */
    public static String normalize(String input) {
        // Remove spaces, brackets, dots and dashes: "(214) 555-0123" -> "2145550123"
        String cleaned = input == null ? "" : input.strip().replaceAll("[\\s().-]", "");

        if (cleaned.matches("\\d{10}")) {
            cleaned = "+1" + cleaned;        // US number without the country code
        } else if (cleaned.matches("1\\d{10}")) {
            cleaned = "+" + cleaned;         // US number starting with 1
        }

        if (!E164.matcher(cleaned).matches()) {
            throw new AuthException(HttpStatus.BAD_REQUEST,
                    "Enter a valid phone number, like (214) 555-0123, or +44 20 7946 0958 outside the US.");
        }
        return cleaned;
    }
}
