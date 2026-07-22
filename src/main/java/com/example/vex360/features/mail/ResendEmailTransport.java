package com.example.vex360.features.mail;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import com.resend.Resend;
import com.resend.core.exception.ResendException;
import com.resend.services.emails.model.CreateEmailOptions;

@Component
@Profile("aiven")
class ResendEmailTransport implements EmailTransport {

    private final Resend resend;
    private final String from;

    @Autowired
    ResendEmailTransport(
            @Value("${app.mail.resend.api-key}") String apiKey,
            @Value("${app.mail.resend.from}") String from) {
        this(new Resend(apiKey), from);
    }

    ResendEmailTransport(Resend resend, String from) {
        this.resend = resend;
        this.from = from;
    }

    @Override
    public void send(String toEmail, String subject, String htmlContent) {
        CreateEmailOptions options = CreateEmailOptions.builder()
                .from(from)
                .to(toEmail)
                .subject(subject)
                .html(htmlContent)
                .build();
        try {
            resend.emails().send(options);
        } catch (ResendException exception) {
            throw new IllegalStateException("Resend email delivery failed", exception);
        }
    }
}
