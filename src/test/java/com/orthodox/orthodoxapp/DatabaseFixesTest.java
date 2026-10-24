package com.orthodox.orthodoxapp;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.orthodox.orthodoxapp.account.AppStyle;
import com.orthodox.orthodoxapp.account.AppUser;
import com.orthodox.orthodoxapp.account.AppUserRepository;
import com.orthodox.orthodoxapp.account.TextSize;
import com.orthodox.orthodoxapp.auth.AuthService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;

/**
 * DatabaseFixes, on a database laid out like the ones made before
 * 2026-10-24: columns from two-step verification that must always be filled
 * in, a text size column that only takes the old sizes (H2's way), and a
 * check rule that only allows two styles (PostgreSQL's way).
 */
@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:fixes;DB_CLOSE_DELAY=-1")
@DirtiesContext // this test changes its database's tables, so no other test may reuse it
class DatabaseFixesTest {

    private static final String PASSWORD = "Vespers-at-6:30pm";

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private DatabaseFixes fixes;

    @Autowired
    private AuthService authService;

    @Autowired
    private AppUserRepository users;

    @Test
    void anOldDatabaseTakesNewAccountsAndTheNewChoices() {
        // Make the old layout, with one account in it.
        jdbc.execute("alter table app_user add column phone_verified boolean not null");
        jdbc.execute("alter table app_user add column two_factor_method varchar(255) not null");
        jdbc.execute("alter table app_user alter column text_size set data type enum ('NORMAL', 'LARGE', 'LARGER')");
        jdbc.execute("alter table app_user add constraint old_styles check (style in ('ILLUMINATED', 'MOSAIC'))");
        jdbc.update("""
                insert into app_user (email, display_name, created_at, email_verified, failed_sign_ins,
                                      phone_verified, two_factor_method, text_size)
                values ('old@example.com', 'Old Account', current_timestamp, false, 0, false, 'NONE', 'LARGER')""");

        // The problems: a new account can't be saved, nor the new choices...
        assertThrows(DataIntegrityViolationException.class,
                () -> authService.register("new@example.com", PASSWORD, "New", null, null));

        fixes.afterPropertiesSet();

        // ...until the fixes have run.
        authService.register("new@example.com", PASSWORD, "New", null, AppStyle.VIGIL); // not in old_styles
        AppUser old = users.findByEmail("old@example.com").orElseThrow();
        assertEquals(TextSize.LARGE, old.getTextSize(), "LARGER became LARGE");

        old.setTextSize(TextSize.SMALL); // not in the old list of sizes
        users.save(old);
        assertEquals(TextSize.SMALL, users.findByEmail("old@example.com").orElseThrow().getTextSize());

        fixes.afterPropertiesSet(); // and running again changes nothing
        assertEquals(TextSize.SMALL, users.findByEmail("old@example.com").orElseThrow().getTextSize());
    }
}
