package com.orthodox.orthodoxapp;

import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.context.annotation.DependsOn;
import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * DatabaseFixes: small repairs to the tables that Hibernate's automatic
 * updates can't make.
 *
 * Hibernate (spring.jpa.hibernate.ddl-auto=update) ADDS tables and columns
 * for new classes and fields, but never changes or removes what's already
 * there. On a database that already has accounts (like the one online), two
 * things then go wrong:
 *
 *  1. Choice fields (enums, such as TextSize) get a column that only
 *     accepts the choices that existed when the column was made: PostgreSQL
 *     gets a "check" rule listing them (NORMAL, LARGE, LARGER), and H2 a
 *     special ENUM column type. Add a new choice (SMALL) and the database
 *     refuses to save it. Fix: make those columns plain text, with no list.
 *     Our Java enums already make sure only real choices get saved.
 *
 *  2. A removed field leaves its column behind. If that column is "not null"
 *     with no default value, creating an account fails, because nothing fills
 *     it in. Fix: give each such old column a default.
 *
 * And when a choice is renamed, the saved values are renamed too.
 *
 * Every fix can safely run again, so they run on every start: right after
 * Hibernate has updated the tables (@DependsOn), and before the app takes
 * its first visitor. Later these become proper numbered "migrations"
 * (Flyway); see application.properties.
 */
@Component
@DependsOn("entityManagerFactory")
class DatabaseFixes implements InitializingBean {

    private static final Logger log = LoggerFactory.getLogger(DatabaseFixes.class);

    // The app_user columns that hold a choice (an enum).
    private static final List<String> CHOICE_COLUMNS = List.of("text_size", "theme", "style", "two_factor_method");

    // app_user columns left behind by two-step verification (taken out on
    // 2026-10-24), and the value each new account gets in them.
    private static final Map<String, String> OLD_COLUMN_DEFAULTS = Map.of(
            "phone_verified", "false",
            "two_factor_method", "'NONE'",
            "failed_codes", "0");

    private final JdbcTemplate jdbc;

    DatabaseFixes(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void afterPropertiesSet() {
        String database = jdbc.execute((ConnectionCallback<String>) c -> c.getMetaData().getDatabaseProductName());
        if (!"PostgreSQL".equals(database) && !"H2".equals(database)) {
            return; // a database we don't use: leave it alone
        }

        removeChoiceRules(database, "app_user");
        for (String column : CHOICE_COLUMNS) {
            makePlainText(database, "app_user", column);
        }
        OLD_COLUMN_DEFAULTS.forEach((column, value) -> {
            if (hasColumn("app_user", column)) {
                jdbc.execute("alter table app_user alter column " + column + " set default " + value);
            }
        });

        // Text sizes became Small, Normal and Large: "Larger" is now "Large".
        int renamed = jdbc.update("update app_user set text_size = 'LARGE' where text_size = 'LARGER'");
        if (renamed > 0) {
            log.info("Changed {} saved text size(s) from LARGER to LARGE", renamed);
        }
    }

    /** Removes the "check" rules (lists of allowed words) from a table. */
    private void removeChoiceRules(String database, String table) {
        List<String> rules = "PostgreSQL".equals(database)
                // contype 'c' = a check rule. (PostgreSQL 18 keeps "not null" apart, as 'n'.)
                ? jdbc.queryForList(
                        "select conname from pg_constraint where conrelid = ?::regclass and contype = 'c'",
                        String.class, table)
                : jdbc.queryForList("""
                        select constraint_name from information_schema.table_constraints
                        where table_schema = current_schema and lower(table_name) = ? and constraint_type = 'CHECK'""",
                        String.class, table);
        for (String rule : rules) {
            jdbc.execute("alter table " + table + " drop constraint \"" + rule + "\"");
            log.info("Removed the rule {} from the {} table", rule, table);
        }
    }

    /** Turns a column into plain text (keeping what's in it), unless it already is. */
    private void makePlainText(String database, String table, String column) {
        List<String> types = jdbc.queryForList("""
                select data_type from information_schema.columns
                where table_schema = current_schema and lower(table_name) = ? and lower(column_name) = ?""",
                String.class, table, column);
        if (types.isEmpty() || types.get(0).equalsIgnoreCase("character varying")) {
            return; // no such column, or already plain text
        }
        jdbc.execute("PostgreSQL".equals(database)
                ? "alter table " + table + " alter column " + column + " type varchar(255) using " + column + "::varchar"
                : "alter table " + table + " alter column " + column + " set data type varchar(255)");
        log.info("Made {}.{} plain text (it was {})", table, column, types.get(0));
    }

    private boolean hasColumn(String table, String column) {
        Integer found = jdbc.queryForObject("""
                select count(*) from information_schema.columns
                where table_schema = current_schema and lower(table_name) = ? and lower(column_name) = ?""",
                Integer.class, table, column);
        return found != null && found > 0;
    }
}
