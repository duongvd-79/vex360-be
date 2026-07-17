package com.example.vex360.features.mail;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.Mockito.*;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.JavaMailSenderImpl;

import com.example.vex360.shared.enums.Role;

import jakarta.mail.internet.MimeMessage;

@ExtendWith(MockitoExtension.class)
class MailServiceUnitTest {

    @Mock
    private ObjectProvider<JavaMailSender> mailSenderProvider;

    @Mock
    private JavaMailSender mailSender;

    private MailServiceImpl mailService;

    @BeforeEach
    void setUp() {
        when(mailSenderProvider.getIfAvailable()).thenReturn(mailSender);
        mailService = new MailServiceImpl(mailSenderProvider);
    }

    @Test
    void testConstructor_MailSenderNotAvailable() {
        ObjectProvider<JavaMailSender> providerNull = mock(ObjectProvider.class);
        when(providerNull.getIfAvailable()).thenReturn(null);

        MailServiceImpl serviceNoSender = new MailServiceImpl(providerNull);
        // Should not throw, should log warning when sending email
        assertDoesNotThrow(() -> {
            serviceNoSender.sendForgotPasswordEmail("test@example.com", "http://reset");
        });
    }

    @Test
    void testSendForgotPasswordEmail_Success() {
        JavaMailSenderImpl dummySender = new JavaMailSenderImpl();
        MimeMessage mimeMessage = dummySender.createMimeMessage();
        when(mailSender.createMimeMessage()).thenReturn(mimeMessage);

        mailService.sendForgotPasswordEmail("test@example.com", "http://reset");

        verify(mailSender).send(mimeMessage);
    }

    @Test
    void testSendRegistrationVerificationEmail_Success() {
        JavaMailSenderImpl dummySender = new JavaMailSenderImpl();
        MimeMessage mimeMessage = dummySender.createMimeMessage();
        when(mailSender.createMimeMessage()).thenReturn(mimeMessage);

        mailService.sendRegistrationVerificationEmail("verify@example.com", "http://verify");

        verify(mailSender).send(mimeMessage);
    }

    @Test
    void testSendPasswordChangeNotificationEmail_Success() {
        JavaMailSenderImpl dummySender = new JavaMailSenderImpl();
        MimeMessage mimeMessage = dummySender.createMimeMessage();
        when(mailSender.createMimeMessage()).thenReturn(mimeMessage);

        mailService.sendPasswordChangeNotificationEmail("notify@example.com");

        verify(mailSender).send(mimeMessage);
    }

    @Test
    void testSendNewUserCredentialsEmail_Success() {
        JavaMailSenderImpl dummySender = new JavaMailSenderImpl();
        MimeMessage mimeMessage = dummySender.createMimeMessage();
        when(mailSender.createMimeMessage()).thenReturn(mimeMessage);

        // Test with regular fullName
        mailService.sendNewUserCredentialsEmail("credentials@example.com", "John Doe", "temp-pass");

        // Test with null and empty fullName to cover branch cases and escapeHtml
        mailService.sendNewUserCredentialsEmail("credentials@example.com", null, "pass&<'\"_val");
        mailService.sendNewUserCredentialsEmail("credentials@example.com", "   ", "pass");

        verify(mailSender, times(3)).send(mimeMessage);
    }

    @Test
    void testSendPartnershipApprovedEmail_Success() {
        JavaMailSenderImpl dummySender = new JavaMailSenderImpl();
        MimeMessage mimeMessage = dummySender.createMimeMessage();
        when(mailSender.createMimeMessage()).thenReturn(mimeMessage);

        mailService.sendPartnershipApprovedEmail("approved@example.com", "Owner", Role.EXHIBITOR, "My Org");
        mailService.sendPartnershipApprovedEmail("approved@example.com", null, Role.ORGANIZER, "My Org");
        mailService.sendPartnershipApprovedEmail("approved@example.com", "   ", Role.ORGANIZER, "My Org");

        verify(mailSender, times(3)).send(mimeMessage);
    }

    @Test
    void testSendPartnershipRejectedEmail_Success() {
        JavaMailSenderImpl dummySender = new JavaMailSenderImpl();
        MimeMessage mimeMessage = dummySender.createMimeMessage();
        when(mailSender.createMimeMessage()).thenReturn(mimeMessage);

        mailService.sendPartnershipRejectedEmail("rejected@example.com", "Owner", "My Org", "Bad documents");
        mailService.sendPartnershipRejectedEmail("rejected@example.com", null, "My Org", null);
        mailService.sendPartnershipRejectedEmail("rejected@example.com", "   ", "My Org", "   ");

        verify(mailSender, times(3)).send(mimeMessage);
    }

    @Test
    void testSendPartnershipVerificationEmail_Success() {
        JavaMailSenderImpl dummySender = new JavaMailSenderImpl();
        MimeMessage mimeMessage = dummySender.createMimeMessage();
        when(mailSender.createMimeMessage()).thenReturn(mimeMessage);

        mailService.sendPartnershipVerificationEmail(
                "verify@example.com",
                "John Owner",
                "Vex Org",
                "http://confirm"
        );

        verify(mailSender).send(mimeMessage);
    }

    @Test
    void testSendMail_SenderNull_DoesNotSend() {
        ObjectProvider<JavaMailSender> providerNull = mock(ObjectProvider.class);
        when(providerNull.getIfAvailable()).thenReturn(null);
        MailServiceImpl serviceNoSender = new MailServiceImpl(providerNull);

        serviceNoSender.sendPartnershipApprovedEmail("approved@example.com", "Owner", Role.EXHIBITOR, "My Org");

        verifyNoInteractions(mailSender);
    }

    @Test
    void testSendHtmlMail_ThrowsException_ExceptionCaught() {
        JavaMailSenderImpl dummySender = new JavaMailSenderImpl();
        MimeMessage mimeMessage = dummySender.createMimeMessage();
        when(mailSender.createMimeMessage()).thenReturn(mimeMessage);
        doThrow(new RuntimeException("SMTP failed")).when(mailSender).send(mimeMessage);

        // Exception should be caught and not thrown
        assertDoesNotThrow(() -> {
            mailService.sendForgotPasswordEmail("test@example.com", "http://reset");
        });
    }
}
