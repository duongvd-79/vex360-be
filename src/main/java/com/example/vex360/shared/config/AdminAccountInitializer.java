package com.example.vex360.shared.config;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.example.vex360.features.user.services.UserService;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class AdminAccountInitializer implements ApplicationRunner {

    private static final String ADMIN_EMAIL = "admin@vex360.local";
    private static final String ADMIN_PASSWORD = "admin123";

    private final UserService userService;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (userService.existsByEmail(ADMIN_EMAIL)) {
            return;
        }

        userService.createAdminUser(ADMIN_EMAIL, ADMIN_PASSWORD, "admin");
    }
}
