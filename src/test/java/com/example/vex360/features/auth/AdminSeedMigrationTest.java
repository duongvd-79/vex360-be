package com.example.vex360.features.auth;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

class AdminSeedMigrationTest {

    @Test
    void adminSeedMigrationCreatesActiveLocalAdminWithExpectedPassword() throws Exception {
        String migration = Files.readString(Path.of("src/main/resources/db/migration/V6__seed_admin_user.sql"));

        assertTrue(migration.contains("admin@vex360.local"));
        assertTrue(migration.contains("'ADMIN'"));
        assertTrue(migration.contains("'LOCAL'"));
        assertTrue(migration.contains("'ACTIVE'"));

        Matcher matcher = Pattern.compile("'(\\$2[aby]\\$12\\$[^']+)'").matcher(migration);
        assertTrue(matcher.find(), "Migration should contain a BCrypt cost 12 password hash");
        assertTrue(new BCryptPasswordEncoder(12).matches("admin123", matcher.group(1)));
    }
}
