package com.example.vex360.features.mail;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mail.javamail.JavaMailSender;

@ExtendWith(MockitoExtension.class)
class SmtpEmailTransportUnitTest {

    @Mock
    private JavaMailSender mailSender;

    private SmtpEmailTransport smtpEmailTransport;

    @BeforeEach
    void setUp() {
        smtpEmailTransport = new SmtpEmailTransport(mailSender);
    }

    @Test
    void send_Success() {
        MimeMessage mimeMessage = new MimeMessage((Session) null);
        when(mailSender.createMimeMessage()).thenReturn(mimeMessage);

        smtpEmailTransport.send("user@example.com", "Subject", "<h1>Test</h1>");

        verify(mailSender).send(mimeMessage);
    }

    @Test
    void send_MailSenderThrowsException_ThrowsIllegalStateException() {
        when(mailSender.createMimeMessage()).thenThrow(new RuntimeException("SMTP Server down"));

        IllegalStateException ex = assertThrows(IllegalStateException.class, () ->
                smtpEmailTransport.send("user@example.com", "Subject", "<h1>Test</h1>"));

        assertEquals("SMTP email delivery failed", ex.getMessage());
    }
}
