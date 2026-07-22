package com.example.vex360.features.mail;

import java.io.ByteArrayOutputStream;
import java.util.Base64;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import com.google.api.client.googleapis.auth.oauth2.GoogleRefreshTokenRequest;
import com.google.api.client.googleapis.auth.oauth2.GoogleTokenResponse;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.gson.GsonFactory;
import com.google.api.services.gmail.Gmail;
import com.google.api.services.gmail.model.Message;

import jakarta.mail.Message.RecipientType;
import jakarta.mail.Session;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;

@Component
@Profile("aiven")
class GmailEmailTransport implements EmailTransport {

    private final String clientId;
    private final String clientSecret;
    private final String refreshToken;
    private final String userEmail;

    GmailEmailTransport(
            @Value("${app.mail.gmail.client-id:}") String clientId,
            @Value("${app.mail.gmail.client-secret:}") String clientSecret,
            @Value("${app.mail.gmail.refresh-token:}") String refreshToken,
            @Value("${app.mail.gmail.user-email:}") String userEmail) {
        this.clientId = clientId;
        this.clientSecret = clientSecret;
        this.refreshToken = refreshToken;
        this.userEmail = userEmail;
    }

    @Override
    public void send(String toEmail, String subject, String htmlContent) {
        if (clientId == null || clientId.isBlank() || clientSecret == null || clientSecret.isBlank()
                || refreshToken == null || refreshToken.isBlank()) {
            throw new IllegalStateException(
                    "Gmail API email delivery failed: Gmail credentials (client-id, client-secret, refresh-token) are not configured.");
        }

        try {
            GoogleTokenResponse tokenResponse = new GoogleRefreshTokenRequest(
                    new NetHttpTransport(),
                    GsonFactory.getDefaultInstance(),
                    refreshToken,
                    clientId,
                    clientSecret)
                    .execute();

            String accessToken = tokenResponse.getAccessToken();

            Gmail service = new Gmail.Builder(
                    new NetHttpTransport(),
                    GsonFactory.getDefaultInstance(),
                    request -> request.getHeaders().setAuthorization("Bearer " + accessToken))
                    .setApplicationName("vex360")
                    .build();

            MimeMessage mimeMessage = new MimeMessage((Session) null);

            if (userEmail != null && !userEmail.isBlank()) {
                mimeMessage.setFrom(new InternetAddress(userEmail));
            }
            mimeMessage.addRecipient(RecipientType.TO, new InternetAddress(toEmail));
            mimeMessage.setSubject(subject, "UTF-8");
            mimeMessage.setContent(htmlContent, "text/html; charset=utf-8");

            ByteArrayOutputStream buffer = new ByteArrayOutputStream();
            mimeMessage.writeTo(buffer);
            String encodedEmail = Base64.getUrlEncoder().encodeToString(buffer.toByteArray());

            Message message = new Message();
            message.setRaw(encodedEmail);

            service.users().messages().send("me", message).execute();
        } catch (Exception exception) {
            throw new IllegalStateException("Gmail API email delivery failed: " + exception.getMessage(), exception);
        }
    }
}
