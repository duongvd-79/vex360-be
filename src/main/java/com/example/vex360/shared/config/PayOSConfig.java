package com.example.vex360.shared.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;

import vn.payos.PayOS;

@Configuration
public class PayOSConfig {

    @Value("${app.payos.client-id}")
    private String clientId;

    @Value("${app.payos.api-key}")
    private String apiKey;

    @Value("${app.payos.checksum-key}")
    private String checksumKey;

    @Bean
    public PayOS payOS(Environment env) {
        boolean isLocalOrTest = env.acceptsProfiles(Profiles.of("local", "test"));
        if (!isLocalOrTest) {
            if (isInvalidCredential(clientId) || isInvalidCredential(apiKey) || isInvalidCredential(checksumKey)) {
                throw new IllegalStateException(
                        "PayOS credentials (client-id, api-key, checksum-key) must be configured in production environment.");
            }
        }
        return new PayOS(clientId, apiKey, checksumKey);
    }

    private boolean isInvalidCredential(String value) {
        return value == null || value.isBlank() || value.startsWith("mock_");
    }
}
