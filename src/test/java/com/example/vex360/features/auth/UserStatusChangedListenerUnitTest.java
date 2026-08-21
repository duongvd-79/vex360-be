package com.example.vex360.features.auth;

import static org.mockito.Mockito.verify;

import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.example.vex360.features.auth.listeners.UserStatusChangedListener;
import com.example.vex360.features.auth.repositories.RefreshTokenRepository;
import com.example.vex360.features.user.entities.User;
import com.example.vex360.features.user.events.UserStatusChangedEvent;
import com.example.vex360.shared.enums.Role;
import com.example.vex360.shared.enums.UserStatus;

@ExtendWith(MockitoExtension.class)
class UserStatusChangedListenerUnitTest {

    @Mock
    private RefreshTokenRepository refreshTokenRepository;

    @Test
    void handleUserStatusChanged_DeletesUserTokens() {
        UserStatusChangedListener listener = new UserStatusChangedListener(refreshTokenRepository);

        User user = User.builder()
                .id(UUID.randomUUID())
                .email("user@example.com")
                .role(Role.VISITOR)
                .status(UserStatus.BLOCKED)
                .build();

        UserStatusChangedEvent event = new UserStatusChangedEvent(this, user);

        listener.handleUserStatusChanged(event);

        verify(refreshTokenRepository).deleteByUser(user);
    }
}
