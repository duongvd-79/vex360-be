package com.example.vex360.shared.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.ApplicationArguments;
import org.springframework.security.crypto.password.PasswordEncoder;

import com.example.vex360.features.user.repositories.UserRepository;
import com.example.vex360.shared.entities.User;
import com.example.vex360.shared.enums.AuthProvider;
import com.example.vex360.shared.enums.Role;
import com.example.vex360.shared.enums.UserStatus;

@ExtendWith(MockitoExtension.class)
class AdminAccountInitializerTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private ApplicationArguments applicationArguments;

    @Test
    void createsDefaultAdminWhenMissing() throws Exception {
        AdminAccountInitializer initializer = new AdminAccountInitializer(userRepository, passwordEncoder);
        when(userRepository.existsByEmail("admin@vex360.local")).thenReturn(false);
        when(passwordEncoder.encode("admin123")).thenReturn("encodedAdminPassword");

        initializer.run(applicationArguments);

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(captor.capture());
        User savedUser = captor.getValue();
        assertEquals("admin@vex360.local", savedUser.getEmail());
        assertEquals("encodedAdminPassword", savedUser.getPassword());
        assertEquals("admin", savedUser.getFullName());
        assertEquals(Role.ADMIN, savedUser.getRole());
        assertEquals(AuthProvider.LOCAL, savedUser.getProvider());
        assertEquals(UserStatus.ACTIVE, savedUser.getStatus());
    }

    @Test
    void skipsDefaultAdminWhenEmailAlreadyExists() throws Exception {
        AdminAccountInitializer initializer = new AdminAccountInitializer(userRepository, passwordEncoder);
        when(userRepository.existsByEmail("admin@vex360.local")).thenReturn(true);

        initializer.run(applicationArguments);

        verify(passwordEncoder, never()).encode(any());
        verify(userRepository, never()).save(any());
    }
}
