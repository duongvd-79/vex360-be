package com.example.vex360.features.auth;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.example.vex360.features.auth.entities.CustomUserDetails;
import com.example.vex360.features.user.entities.User;
import com.example.vex360.shared.enums.Role;
import com.example.vex360.shared.enums.UserStatus;

class CustomUserDetailsUnitTest {

    private User user;

    @BeforeEach
    void setUp() {
        user = User.builder()
                .id(UUID.randomUUID())
                .email("user@example.com")
                .password("encoded_pass")
                .role(Role.VISITOR)
                .status(UserStatus.ACTIVE)
                .build();
    }

    @Test
    void userDetailsMethods_ReturnExpectedValues() {
        CustomUserDetails customUserDetails = new CustomUserDetails(user);

        assertEquals(user, customUserDetails.getUser());
        assertEquals("user@example.com", customUserDetails.getUsername());
        assertEquals("encoded_pass", customUserDetails.getPassword());
        assertTrue(customUserDetails.isAccountNonExpired());
        assertTrue(customUserDetails.isCredentialsNonExpired());

        assertEquals(1, customUserDetails.getAuthorities().size());
        assertEquals("VISITOR", customUserDetails.getAuthorities().iterator().next().getAuthority());
    }

    @Test
    void isAccountNonLocked_NullLockoutEnd_ReturnsTrue() {
        user.setLockoutEnd(null);
        CustomUserDetails customUserDetails = new CustomUserDetails(user);
        assertTrue(customUserDetails.isAccountNonLocked());
    }

    @Test
    void isAccountNonLocked_LockoutEndInPast_ReturnsTrue() {
        user.setLockoutEnd(Instant.now().minusSeconds(3600)); // 1 hr ago
        CustomUserDetails customUserDetails = new CustomUserDetails(user);
        assertTrue(customUserDetails.isAccountNonLocked());
    }

    @Test
    void isAccountNonLocked_LockoutEndInFuture_ReturnsFalse() {
        user.setLockoutEnd(Instant.now().plusSeconds(3600)); // 1 hr in future
        CustomUserDetails customUserDetails = new CustomUserDetails(user);
        assertFalse(customUserDetails.isAccountNonLocked());
    }

    @Test
    void isEnabled_ActiveStatus_ReturnsTrue() {
        user.setStatus(UserStatus.ACTIVE);
        CustomUserDetails customUserDetails = new CustomUserDetails(user);
        assertTrue(customUserDetails.isEnabled());
    }

    @Test
    void isEnabled_PendingOrBlockedStatus_ReturnsFalse() {
        user.setStatus(UserStatus.PENDING);
        CustomUserDetails userDetailsPending = new CustomUserDetails(user);
        assertFalse(userDetailsPending.isEnabled());

        user.setStatus(UserStatus.BLOCKED);
        CustomUserDetails userDetailsBlocked = new CustomUserDetails(user);
        assertFalse(userDetailsBlocked.isEnabled());
    }
}
