package com.example.vex360.features.mail;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.example.vex360.shared.enums.Role;

@ExtendWith(MockitoExtension.class)
class MailServiceUnitTest {

    @Mock
    private EmailTransport emailTransport;

    private MailServiceImpl mailService;

    @BeforeEach
    void setUp() {
        mailService = new MailServiceImpl(emailTransport);
    }

    @Test
    void testSendForgotPasswordEmail_Success() {
        mailService.sendForgotPasswordEmail("test@example.com", "http://reset");

        verify(emailTransport).send(
                eq("test@example.com"),
                eq("Yêu cầu khôi phục mật khẩu - VEX360"),
                contains("http://reset"));
    }

    @Test
    void testSendRegistrationVerificationEmail_Success() {
        mailService.sendRegistrationVerificationEmail("verify@example.com", "http://verify");

        verify(emailTransport).send(
                eq("verify@example.com"),
                eq("Xác thực tài khoản VEX360"),
                contains("http://verify"));
    }

    @Test
    void testSendPasswordChangeNotificationEmail_Success() {
        mailService.sendPasswordChangeNotificationEmail("notify@example.com");

        verify(emailTransport).send(
                eq("notify@example.com"),
                eq("Mật khẩu của bạn đã được thay đổi thành công - VEX360"),
                contains("CẢNH BÁO"));
    }

    @Test
    void testSendNewUserCredentialsEmail_Success() {
        mailService.sendNewUserCredentialsEmail("credentials@example.com", "John Doe", "temp-pass");
        mailService.sendNewUserCredentialsEmail("credentials@example.com", null, "pass&<'\"_val");
        mailService.sendNewUserCredentialsEmail("credentials@example.com", "   ", "pass");

        verify(emailTransport, times(3)).send(
                eq("credentials@example.com"),
                eq("Thông tin tài khoản VEX360"),
                contains("credentials@example.com"));
    }

    @Test
    void testSendPartnershipApprovedEmail_Success() {
        mailService.sendPartnershipApprovedEmail("approved@example.com", "Owner", Role.EXHIBITOR, "My Org");
        mailService.sendPartnershipApprovedEmail("approved@example.com", null, Role.ORGANIZER, "My Org");
        mailService.sendPartnershipApprovedEmail("approved@example.com", "   ", Role.ORGANIZER, "My Org");

        verify(emailTransport, times(3)).send(
                eq("approved@example.com"),
                eq("Yêu cầu hợp tác đã được phê duyệt - VEX360"),
                contains("My Org"));
    }

    @Test
    void testSendPartnershipRejectedEmail_Success() {
        mailService.sendPartnershipRejectedEmail("rejected@example.com", "Owner", "My Org", "Bad documents");
        mailService.sendPartnershipRejectedEmail("rejected@example.com", null, "My Org", null);
        mailService.sendPartnershipRejectedEmail("rejected@example.com", "   ", "My Org", "   ");

        verify(emailTransport, times(3)).send(
                eq("rejected@example.com"),
                eq("Yêu cầu hợp tác chưa được phê duyệt - VEX360"),
                contains("My Org"));
    }

    @Test
    void testSendPartnershipVerificationEmail_Success() {
        mailService.sendPartnershipVerificationEmail(
                "verify@example.com",
                "John Owner",
                "Vex Org",
                "http://confirm");

        verify(emailTransport).send(
                eq("verify@example.com"),
                eq("Xác nhận yêu cầu hợp tác - Vex360"),
                contains("http://confirm"));
    }

    @Test
    void testVerificationLinks_AreEscapedBeforeEmbeddingInHtml() {
        String dangerousUrl = "https://example.com/?next=\"><script>alert(1)</script>";

        mailService.sendForgotPasswordEmail("test@example.com", dangerousUrl);
        mailService.sendRegistrationVerificationEmail("test@example.com", dangerousUrl);
        mailService.sendPartnershipVerificationEmail(
                "test@example.com",
                "Owner",
                "Vex Org",
                dangerousUrl);

        ArgumentCaptor<String> htmlCaptor = ArgumentCaptor.forClass(String.class);
        verify(emailTransport, times(3)).send(anyString(), anyString(), htmlCaptor.capture());
        assertTrue(htmlCaptor.getAllValues().stream()
                .allMatch(html -> !html.contains("<script>") && html.contains("&lt;script&gt;")));
    }

    @Test
    void testSendMail_TransportThrows_ExceptionCaught() {
        doThrow(new RuntimeException("Email provider failed"))
                .when(emailTransport).send(eq("test@example.com"), contains("VEX360"), contains("http://reset"));

        assertDoesNotThrow(() -> mailService.sendForgotPasswordEmail("test@example.com", "http://reset"));
    }
}
