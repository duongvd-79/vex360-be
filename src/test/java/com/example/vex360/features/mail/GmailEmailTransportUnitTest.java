package com.example.vex360.features.mail;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class GmailEmailTransportUnitTest {

    @ParameterizedTest
    @ValueSource(strings = {"", "   "})
    void send_InvalidCredentials_ThrowsIllegalStateException(String blank) {
        assertCredentialError(new GmailEmailTransport(null, "secret", "token", "u@e.com"));
        assertCredentialError(new GmailEmailTransport(blank, "secret", "token", "u@e.com"));
        assertCredentialError(new GmailEmailTransport("id", null, "token", "u@e.com"));
        assertCredentialError(new GmailEmailTransport("id", blank, "token", "u@e.com"));
        assertCredentialError(new GmailEmailTransport("id", "secret", null, "u@e.com"));
        assertCredentialError(new GmailEmailTransport("id", "secret", blank, "u@e.com"));
    }

    @Test
    void send_WithCredentials_ExecutionFailsAndThrowsIllegalStateException() {
        GmailEmailTransport transport = new GmailEmailTransport("dummy_id", "dummy_secret", "dummy_token", "user@example.com");

        IllegalStateException ex = assertThrows(IllegalStateException.class, () ->
                transport.send("to@example.com", "Subject", "Body"));

        assertTrue(ex.getMessage().startsWith("Gmail API email delivery failed:"));
    }

    @Test
    void send_WithNullOrBlankUserEmail_ExecutionFailsAndThrowsIllegalStateException() {
        GmailEmailTransport transportNullUser = new GmailEmailTransport("dummy_id", "dummy_secret", "dummy_token", null);
        GmailEmailTransport transportBlankUser = new GmailEmailTransport("dummy_id", "dummy_secret", "dummy_token", "   ");

        IllegalStateException ex1 = assertThrows(IllegalStateException.class, () ->
                transportNullUser.send("to@example.com", "Subject", "Body"));
        IllegalStateException ex2 = assertThrows(IllegalStateException.class, () ->
                transportBlankUser.send("to@example.com", "Subject", "Body"));

        assertTrue(ex1.getMessage().startsWith("Gmail API email delivery failed:"));
        assertTrue(ex2.getMessage().startsWith("Gmail API email delivery failed:"));
    }

    private void assertCredentialError(GmailEmailTransport transport) {
        IllegalStateException ex = assertThrows(IllegalStateException.class, () ->
                transport.send("to@example.com", "Subject", "Body"));
        assertTrue(ex.getMessage().contains("Gmail credentials (client-id, client-secret, refresh-token) are not configured."));
    }
}
