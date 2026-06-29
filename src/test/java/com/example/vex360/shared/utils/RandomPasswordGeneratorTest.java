package com.example.vex360.shared.utils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class RandomPasswordGeneratorTest {

    @Test
    void generateReturnsPasswordWithRequiredCharacterGroups() {
        String password = RandomPasswordGenerator.generate();

        assertEquals(8, password.length());
        assertTrue(password.matches(".*[A-Z].*"));
        assertTrue(password.matches(".*[a-z].*"));
        assertTrue(password.matches(".*\\d.*"));
        assertTrue(password.matches(".*[!@#$%^&*].*"));
    }
}
