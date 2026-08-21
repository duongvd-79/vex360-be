package com.example.vex360.features.mail;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.web.util.HtmlUtils;

import com.example.vex360.shared.enums.DesignRequestStatus;
import com.example.vex360.shared.enums.ExhibitorRegistrationStatus;
import com.example.vex360.shared.enums.Role;

@ExtendWith({MockitoExtension.class, OutputCaptureExtension.class})
class MailServiceUnitTest {

    @Mock
    private EmailTransport emailTransport;

    private MailService mailService;

    @BeforeEach
    void setUp() {
        mailService = new MailService(emailTransport, "http://localhost:5175/login");
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
    void testSendPartnershipGuestApprovedEmail_Success() {
        mailService.sendPartnershipGuestApprovedEmail(
                "guest@example.com",
                "Guest User",
                "tempPass123",
                Role.EXHIBITOR,
                "Tech Corp");

        verify(emailTransport).send(
                eq("guest@example.com"),
                eq("Yêu cầu hợp tác đã được phê duyệt - Thông tin tài khoản VEX360"),
                contains("tempPass123"));
    }

    @Test
    void testSendExhibitionReviewResultEmail_ApprovedAndRejected() {
        mailService.sendExhibitionReviewResultEmail(
                "org@example.com",
                "Org Name",
                "AI Expo",
                LocalDate.of(2026, 9, 1),
                LocalDate.of(2026, 9, 5),
                "APPROVED",
                null,
                0,
                Instant.now());

        verify(emailTransport).send(
                eq("org@example.com"),
                eq("Triển lãm \"AI Expo\" đã được phê duyệt - VEX360"),
                contains("AI Expo"));

        mailService.sendExhibitionReviewResultEmail(
                "org@example.com",
                "Org Name",
                "AI Expo",
                LocalDate.of(2026, 9, 1),
                LocalDate.of(2026, 9, 5),
                "REJECTED",
                "Incomplete documents",
                2,
                Instant.now());

        verify(emailTransport).send(
                eq("org@example.com"),
                eq("Triển lãm \"AI Expo\" chưa được phê duyệt - VEX360"),
                contains("2/3"));
    }

    @Test
    void testSendExhibitionReviewResultEmail_InvalidStatus() {
        mailService.sendExhibitionReviewResultEmail(
                "org@example.com",
                "Org Name",
                "AI Expo",
                LocalDate.of(2026, 9, 1),
                LocalDate.of(2026, 9, 5),
                null,
                null,
                0,
                Instant.now());

        verify(emailTransport, never()).send(anyString(), anyString(), anyString());
    }

    @Test
    void testSendExhibitorRegistrationReviewResultEmail_PendingPaymentAndApprovedAndRejected() {
        mailService.sendExhibitorRegistrationReviewResultEmail(
                "exhibitor@example.com",
                "Exhibitor Name",
                "Tech Ltd",
                "Tech Expo 2026",
                "Gold Package",
                BigDecimal.valueOf(5000000),
                "VND",
                ExhibitorRegistrationStatus.PENDING_PAYMENT,
                null);

        verify(emailTransport).send(
                eq("exhibitor@example.com"),
                eq("Đăng ký tham gia \"Tech Expo 2026\" đã được duyệt - Vui lòng thanh toán"),
                contains("5,000,000 VND"));

        mailService.sendExhibitorRegistrationReviewResultEmail(
                "exhibitor@example.com",
                "Exhibitor Name",
                "Tech Ltd",
                "Tech Expo 2026",
                "Free Package",
                BigDecimal.ZERO,
                "VND",
                ExhibitorRegistrationStatus.APPROVED,
                null);

        verify(emailTransport).send(
                eq("exhibitor@example.com"),
                eq("Đăng ký tham gia \"Tech Expo 2026\" đã được phê duyệt - VEX360"),
                contains("Miễn phí"));

        mailService.sendExhibitorRegistrationReviewResultEmail(
                "exhibitor@example.com",
                "Exhibitor Name",
                "Tech Ltd",
                "Tech Expo 2026",
                "Gold Package",
                BigDecimal.valueOf(5000000),
                "VND",
                ExhibitorRegistrationStatus.REJECTED,
                "Ineligible category");

        verify(emailTransport).send(
                eq("exhibitor@example.com"),
                eq("Đăng ký tham gia \"Tech Expo 2026\" chưa được phê duyệt"),
                contains("Ineligible category"));
    }

    @Test
    void testSendExhibitorRegistrationReviewResultEmail_GenericApprovalDoesNotClaimFreePayment() {
        mailService.sendExhibitorRegistrationReviewResultEmail(
                "exhibitor@example.com", "Exhibitor Name", "Tech Ltd", "Tech Expo 2026",
                "Gold Package", BigDecimal.valueOf(5000000), "VND",
                ExhibitorRegistrationStatus.APPROVED, null);
        mailService.sendExhibitorRegistrationReviewResultEmail(
                "exhibitor@example.com", "Exhibitor Name", "Tech Ltd", "Tech Expo 2026",
                "Unknown Package", null, "VND",
                ExhibitorRegistrationStatus.APPROVED, null);

        ArgumentCaptor<String> htmlCaptor = ArgumentCaptor.forClass(String.class);
        verify(emailTransport, times(2)).send(
                eq("exhibitor@example.com"),
                eq("Đăng ký tham gia \"Tech Expo 2026\" đã được phê duyệt - VEX360"),
                htmlCaptor.capture());

        assertThat(htmlCaptor.getAllValues().get(0))
                .contains("5,000,000 VND")
                .doesNotContain("Gói miễn phí", "Thanh toán thành công");
        assertThat(htmlCaptor.getAllValues().get(1))
                .contains("Kh&ocirc;ng x&aacute;c")
                .doesNotContain("Gói miễn phí", "Thanh toán thành công", "0 VND");
    }

    @Test
    void testSendExhibitorRegistrationPaymentConfirmedEmail_Success() {
        mailService.sendExhibitorRegistrationPaymentConfirmedEmail(
                "paid@example.com",
                "Exhibitor Name",
                "Tech Ltd",
                "Tech Expo 2026",
                "VIP Package",
                BigDecimal.valueOf(10000000),
                "VND",
                123456789L,
                Instant.now());

        verify(emailTransport).send(
                eq("paid@example.com"),
                eq("Thanh toán thành công - Đăng ký \"Tech Expo 2026\" đã được xác nhận"),
                contains("123456789"));
    }

    @Test
    void testSendBoothReviewResultEmail_ApprovedAndRejected() {
        mailService.sendBoothReviewResultEmail(
                "booth@example.com",
                "Booth Owner",
                "Smart Home Booth",
                "Tech Expo 2026",
                1,
                "APPROVED",
                null,
                Instant.now());

        verify(emailTransport).send(
                eq("booth@example.com"),
                eq("Gian hàng \"Smart Home Booth\" đã được phê duyệt và xuất bản"),
                contains("v1"));

        mailService.sendBoothReviewResultEmail(
                "booth@example.com",
                "Booth Owner",
                "Smart Home Booth",
                "Tech Expo 2026",
                1,
                "REJECTED",
                "Low resolution panorama",
                Instant.now());

        verify(emailTransport).send(
                eq("booth@example.com"),
                eq("Gian hàng \"Smart Home Booth\" cần được chỉnh sửa"),
                contains("Low resolution panorama"));
    }

    @Test
    void testSendDesignDraftReviewResultEmail_ApprovedAndRevisionRequested() {
        mailService.sendDesignDraftReviewResultEmail(
                "designer@example.com",
                "Designer Name",
                "Design Co",
                "3D Booth A",
                2,
                DesignRequestStatus.APPROVED,
                null);

        verify(emailTransport).send(
                eq("designer@example.com"),
                eq("Bản thiết kế cho gian hàng \"3D Booth A\" đã được phê duyệt"),
                contains("v2"));

        mailService.sendDesignDraftReviewResultEmail(
                "designer@example.com",
                "Designer Name",
                "Design Co",
                "3D Booth A",
                2,
                DesignRequestStatus.REVISION_REQUESTED,
                "Adjust lighting");

        verify(emailTransport).send(
                eq("designer@example.com"),
                eq("Bản thiết kế cho gian hàng \"3D Booth A\" cần được chỉnh sửa"),
                contains("Adjust lighting"));
    }

    @Test
    void testSendDesignCancellationDecisionEmail_DesignerAndExhibitor() {
        mailService.sendDesignCancellationDecisionEmail(
                "exhibitor@example.com",
                "Exhibitor",
                false,
                "Company A",
                "Booth X",
                "APPROVED",
                "Budget issue",
                "Approved by admin",
                Instant.now());

        verify(emailTransport).send(
                eq("exhibitor@example.com"),
                eq("Yêu cầu hủy thiết kế gian hàng \"Booth X\" đã được chấp thuận"),
                contains("Approved by admin"));

        mailService.sendDesignCancellationDecisionEmail(
                "designer@example.com",
                "Designer",
                true,
                "Company A",
                "Booth X",
                "APPROVED",
                "Budget issue",
                "Approved by admin",
                Instant.now());

        verify(emailTransport).send(
                eq("designer@example.com"),
                eq("Yêu cầu thiết kế gian hàng \"Booth X\" đã bị hủy"),
                contains("Approved by admin"));
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
    void testSendMail_TransportThrows_LogsSanitizedProviderMessage(CapturedOutput output) {
        doThrow(new RuntimeException("Email provider failed\ninjected"))
                .when(emailTransport).send(eq("test@example.com"), contains("VEX360"), contains("http://reset"));

        assertDoesNotThrow(() -> mailService.sendForgotPasswordEmail("test@example.com", "http://reset"));
        assertThat(output).contains("Email provider failed_injected");
    }

    @Test
    void testSendEmail_InvalidOrNullEmail_Skipped() {
        mailService.sendForgotPasswordEmail(null, "http://reset");
        mailService.sendForgotPasswordEmail("   ", "http://reset");
        mailService.sendRegistrationVerificationEmail(null, "http://verify");
        mailService.sendPasswordChangeNotificationEmail(null);

        verify(emailTransport, never()).send(anyString(), anyString(), anyString());
    }

    @Test
    void testSendExhibitionReviewResultEmail_MaxRejectionsLimit() {
        mailService.sendExhibitionReviewResultEmail(
                "org@example.com",
                "Org Name",
                "AI Expo",
                LocalDate.of(2026, 9, 1),
                LocalDate.of(2026, 9, 5),
                "REJECTED",
                "Exceeded limits",
                3,
                Instant.now());

        verify(emailTransport).send(
                eq("org@example.com"),
                eq("Triển lãm \"AI Expo\" chưa được phê duyệt - VEX360"),
                contains("Triển lãm này đã đạt giới hạn tối đa 3 lần xét duyệt và không thể gửi lại."));
    }

    @Test
    void testSendExhibitionReviewResultEmail_NullDatesAndInstant() {
        mailService.sendExhibitionReviewResultEmail(
                "org@example.com",
                "Org Name",
                "AI Expo",
                null,
                null,
                "APPROVED",
                null,
                0,
                null);

        verify(emailTransport).send(
                eq("org@example.com"),
                eq("Triển lãm \"AI Expo\" đã được phê duyệt - VEX360"),
                contains("AI Expo"));
    }

    @Test
    void testSendExhibitorRegistrationReviewResultEmail_InvalidStatus() {
        mailService.sendExhibitorRegistrationReviewResultEmail(
                "exhibitor@example.com",
                "Exhibitor Name",
                "Tech Ltd",
                "Tech Expo 2026",
                "Gold Package",
                BigDecimal.valueOf(5000000),
                "VND",
                ExhibitorRegistrationStatus.PENDING,
                null);

        verify(emailTransport, never()).send(anyString(), anyString(), anyString());
    }

    @Test
    void testSendExhibitorRegistrationReviewResultEmail_NullPriceAndReason() {
        mailService.sendExhibitorRegistrationReviewResultEmail(
                "exhibitor@example.com",
                "Exhibitor Name",
                "Tech Ltd",
                "Tech Expo 2026",
                "Gold Package",
                null,
                null,
                ExhibitorRegistrationStatus.REJECTED,
                null);

        verify(emailTransport).send(
                eq("exhibitor@example.com"),
                eq("Đăng ký tham gia \"Tech Expo 2026\" chưa được phê duyệt"),
                contains(HtmlUtils.htmlEscape("Không có ghi chú bổ sung")));
    }

    @Test
    void testSendExhibitorRegistrationPaymentConfirmedEmail_NullOrderCodeAndPaidAtAndAmount() {
        mailService.sendExhibitorRegistrationPaymentConfirmedEmail(
                "paid@example.com",
                "Exhibitor Name",
                "Tech Ltd",
                "Tech Expo 2026",
                "VIP Package",
                null,
                null,
                null,
                null);

        verify(emailTransport).send(
                eq("paid@example.com"),
                eq("Thanh toán thành công - Đăng ký \"Tech Expo 2026\" đã được xác nhận"),
                contains(HtmlUtils.htmlEscape("Không có")));
    }

    @Test
    void testSendBoothReviewResultEmail_InvalidStatusAndNullValues() {
        mailService.sendBoothReviewResultEmail(
                "booth@example.com",
                "Booth Owner",
                "Smart Home Booth",
                "Tech Expo 2026",
                null,
                "INVALID_STATUS",
                null,
                null);

        verify(emailTransport, never()).send(anyString(), anyString(), anyString());

        mailService.sendBoothReviewResultEmail(
                "booth@example.com",
                "Booth Owner",
                "Smart Home Booth",
                "Tech Expo 2026",
                null,
                "APPROVED",
                null,
                null);

        verify(emailTransport).send(
                eq("booth@example.com"),
                eq("Gian hàng \"Smart Home Booth\" đã được phê duyệt và xuất bản"),
                contains("v1"));

        mailService.sendBoothReviewResultEmail(
                "booth@example.com",
                "Booth Owner",
                "Smart Home Booth",
                "Tech Expo 2026",
                null,
                "REJECTED",
                null,
                null);

        verify(emailTransport).send(
                eq("booth@example.com"),
                eq("Gian hàng \"Smart Home Booth\" cần được chỉnh sửa"),
                contains(HtmlUtils.htmlEscape("Không có ghi chú bổ sung")));
    }

    @Test
    void testSendDesignDraftReviewResultEmail_InvalidStatusAndNullValues() {
        mailService.sendDesignDraftReviewResultEmail(
                "designer@example.com",
                "Designer Name",
                "Design Co",
                "3D Booth A",
                null,
                DesignRequestStatus.PENDING,
                null);

        verify(emailTransport, never()).send(anyString(), anyString(), anyString());

        mailService.sendDesignDraftReviewResultEmail(
                "designer@example.com",
                "Designer Name",
                "Design Co",
                "3D Booth A",
                null,
                DesignRequestStatus.REVISION_REQUESTED,
                null);

        verify(emailTransport).send(
                eq("designer@example.com"),
                eq("Bản thiết kế cho gian hàng \"3D Booth A\" cần được chỉnh sửa"),
                contains(HtmlUtils.htmlEscape("Không có ghi chú bổ sung")));
    }

    @Test
    void testSendDesignCancellationDecisionEmail_DesignerRecipient_Rejected() {
        mailService.sendDesignCancellationDecisionEmail(
                "designer@example.com",
                "Designer",
                true,
                "Company A",
                "Booth X",
                "REJECTED",
                "Budget issue",
                "Rejected by admin",
                Instant.now());

        verify(emailTransport).send(
                eq("designer@example.com"),
                eq("Tiếp tục thực hiện thiết kế gian hàng \"Booth X\""),
                contains("Từ chối hủy (Tiếp tục)"));
    }

    @Test
    void testSendDesignCancellationDecisionEmail_ExhibitorRecipient_Rejected() {
        mailService.sendDesignCancellationDecisionEmail(
                "exhibitor@example.com",
                "Exhibitor",
                false,
                "Company A",
                "Booth X",
                "REJECTED",
                "Budget issue",
                "Rejected by admin",
                Instant.now());

        verify(emailTransport).send(
                eq("exhibitor@example.com"),
                eq("Yêu cầu hủy thiết kế gian hàng \"Booth X\" chưa được chấp thuận"),
                contains("Tiếp tục thực hiện"));
    }

    @Test
    void testSendDesignCancellationDecisionEmail_InvalidStatusAndNullValues() {
        mailService.sendDesignCancellationDecisionEmail(
                "exhibitor@example.com",
                "Exhibitor",
                false,
                "Company A",
                "Booth X",
                "INVALID_STATUS",
                null,
                null,
                null);

        verify(emailTransport, never()).send(anyString(), anyString(), anyString());

        mailService.sendDesignCancellationDecisionEmail(
                "exhibitor@example.com",
                "Exhibitor",
                false,
                "Company A",
                "Booth X",
                "APPROVED",
                null,
                null,
                null);

        verify(emailTransport).send(
                eq("exhibitor@example.com"),
                eq("Yêu cầu hủy thiết kế gian hàng \"Booth X\" đã được chấp thuận"),
                contains(HtmlUtils.htmlEscape("Không có ghi chú bổ sung")));
    }

    @Test
    void testSendMail_ExceptionWithNullMessage(CapturedOutput output) {
        doThrow(new RuntimeException((String) null))
                .when(emailTransport).send(eq("test@example.com"), contains("VEX360"), contains("http://reset"));

        assertDoesNotThrow(() -> mailService.sendForgotPasswordEmail("test@example.com", "http://reset"));
        assertThat(output).contains("RuntimeException");
    }
}
