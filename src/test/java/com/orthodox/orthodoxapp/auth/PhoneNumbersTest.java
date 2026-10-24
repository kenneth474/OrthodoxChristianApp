package com.orthodox.orthodoxapp.auth;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class PhoneNumbersTest {

    @Test
    void usNumbersInAnyCommonFormat() {
        assertEquals("+12145550123", PhoneNumbers.normalize("(214) 555-0123"));
        assertEquals("+12145550123", PhoneNumbers.normalize("214.555.0123"));
        assertEquals("+12145550123", PhoneNumbers.normalize("1 214 555 0123"));
        assertEquals("+12145550123", PhoneNumbers.normalize("+1 (214) 555-0123"));
    }

    @Test
    void otherCountriesStartWithPlus() {
        assertEquals("+442079460958", PhoneNumbers.normalize("+44 20 7946 0958"));
    }

    @Test
    void rejectsThingsThatArentPhoneNumbers() {
        assertThrows(AuthException.class, () -> PhoneNumbers.normalize("555-0123"));      // too short
        assertThrows(AuthException.class, () -> PhoneNumbers.normalize("call me maybe"));
        assertThrows(AuthException.class, () -> PhoneNumbers.normalize("+0123456789"));   // no country starts with 0
    }
}
