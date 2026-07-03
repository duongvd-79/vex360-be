package com.example.vex360.shared.config;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.ApplicationArguments;
import com.example.vex360.features.user.services.UserService;

@ExtendWith(MockitoExtension.class)
class AdminAccountInitializerTest {

    @Mock
    private UserService userService;

    @Mock
    private ApplicationArguments applicationArguments;

    @Test
    void createsDefaultAdminWhenMissing() throws Exception {
        AdminAccountInitializer initializer = new AdminAccountInitializer(userService);
        when(userService.existsByEmail("admin@vex360.local")).thenReturn(false);

        initializer.run(applicationArguments);

        verify(userService).createAdminUser("admin@vex360.local", "admin123", "admin");
    }

    @Test
    void skipsDefaultAdminWhenEmailAlreadyExists() throws Exception {
        AdminAccountInitializer initializer = new AdminAccountInitializer(userService);
        when(userService.existsByEmail("admin@vex360.local")).thenReturn(true);

        initializer.run(applicationArguments);

        verify(userService, never()).createAdminUser(any(), any(), any());
    }
}
