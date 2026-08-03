package com.example.vex360.features.mail;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.resend.Resend;
import com.resend.core.exception.ResendException;
import com.resend.services.emails.Emails;
import com.resend.services.emails.model.CreateEmailOptions;
import com.resend.services.emails.model.CreateEmailResponse;

@ExtendWith(MockitoExtension.class)
class ResendEmailTransportUnitTest {

    @Mock
    private Resend resend;

    @Mock
    private Emails emails;

    private ResendEmailTransport resendEmailTransport;

    @BeforeEach
    void setUp() {
        resendEmailTransport = new ResendEmailTransport(resend, "noreply@example.com");
    }

    @Test
    void constructor_AutowiredConstructor_InstantiatesSuccessfully() {
        ResendEmailTransport transport = new ResendEmailTransport("re_dummy_key", "noreply@example.com");
        assertNotNull(transport);
    }

    @Test
    void send_Success() throws ResendException {
        when(resend.emails()).thenReturn(emails);
        when(emails.send(any(CreateEmailOptions.class))).thenReturn(new CreateEmailResponse());

        resendEmailTransport.send("user@example.com", "Subject", "<p>Content</p>");

        verify(emails).send(any(CreateEmailOptions.class));
    }

    @Test
    void send_ResendException_ThrowsIllegalStateException() throws ResendException {
        when(resend.emails()).thenReturn(emails);
        when(emails.send(any(CreateEmailOptions.class))).thenThrow(new ResendException("Invalid API key"));

        IllegalStateException ex = assertThrows(IllegalStateException.class, () ->
                resendEmailTransport.send("user@example.com", "Subject", "<p>Content</p>"));

        assertEquals("Resend email delivery failed: Invalid API key", ex.getMessage());
    }
}
