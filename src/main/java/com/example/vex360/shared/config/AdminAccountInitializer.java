package com.example.vex360.shared.config;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.example.vex360.features.user.repositories.UserRepository;
import com.example.vex360.shared.entities.User;
import com.example.vex360.shared.enums.AuthProvider;
import com.example.vex360.shared.enums.Role;
import com.example.vex360.shared.enums.UserStatus;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class AdminAccountInitializer implements ApplicationRunner {

    private static final String ADMIN_EMAIL = "admin@vex360.local";
    private static final String ADMIN_PASSWORD = "admin123";

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (userRepository.existsByEmail(ADMIN_EMAIL)) {
            return;
        }

        User admin = User.builder()
                .email(ADMIN_EMAIL)
                .password(passwordEncoder.encode(ADMIN_PASSWORD))
                .fullName("admin")
                .role(Role.ADMIN)
                .provider(AuthProvider.LOCAL)
                .status(UserStatus.ACTIVE)
                .build();

        userRepository.save(admin);
    }
}
