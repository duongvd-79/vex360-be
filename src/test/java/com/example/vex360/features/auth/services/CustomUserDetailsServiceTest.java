package com.example.vex360.features.auth.services;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.userdetails.UsernameNotFoundException;

import com.example.vex360.features.user.services.UserService;
import com.example.vex360.shared.exceptions.AppException;
import com.example.vex360.shared.exceptions.ErrorCode;

@ExtendWith(MockitoExtension.class)
class CustomUserDetailsServiceTest {

    @Mock
    private UserService userService;

    @Test
    void loadUserByUsername_MissingUser_ThrowsUsernameNotFoundException() {
        String email = "missing@example.com";
        lenient().when(userService.getUserByEmail(email)).thenThrow(new AppException(ErrorCode.USER_NOT_FOUND));
        when(userService.findUserByEmail(email)).thenReturn(Optional.empty());

        CustomUserDetailsService service = new CustomUserDetailsService(userService);

        assertThrows(UsernameNotFoundException.class, () -> service.loadUserByUsername(email));
    }
}
