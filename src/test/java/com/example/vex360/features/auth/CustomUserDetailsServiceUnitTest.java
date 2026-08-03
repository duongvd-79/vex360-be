package com.example.vex360.features.auth;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;

import com.example.vex360.features.auth.services.CustomUserDetailsService;
import com.example.vex360.features.user.entities.User;
import com.example.vex360.features.user.services.UserService;
import com.example.vex360.shared.enums.Role;
import com.example.vex360.shared.enums.UserStatus;

@ExtendWith(MockitoExtension.class)
class CustomUserDetailsServiceUnitTest {

    @Mock
    private UserService userService;

    private CustomUserDetailsService customUserDetailsService;
    private User user;

    @BeforeEach
    void setUp() {
        customUserDetailsService = new CustomUserDetailsService(userService);
        user = User.builder()
                .id(UUID.randomUUID())
                .email("user@example.com")
                .password("encoded_pass")
                .role(Role.VISITOR)
                .status(UserStatus.ACTIVE)
                .build();
    }

    @Test
    void loadUserByUsername_UserFound_ReturnsUserDetails() {
        when(userService.findUserByEmail("user@example.com")).thenReturn(Optional.of(user));

        UserDetails userDetails = customUserDetailsService.loadUserByUsername("user@example.com");

        assertNotNull(userDetails);
        assertEquals("user@example.com", userDetails.getUsername());
        assertEquals("encoded_pass", userDetails.getPassword());
    }

    @Test
    void loadUserByUsername_UserNotFound_ThrowsUsernameNotFoundException() {
        when(userService.findUserByEmail("notfound@example.com")).thenReturn(Optional.empty());

        assertThrows(UsernameNotFoundException.class, () -> customUserDetailsService.loadUserByUsername("notfound@example.com"));
    }
}
