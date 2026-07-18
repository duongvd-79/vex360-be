package com.example.vex360.features.auth.services;

import com.example.vex360.features.auth.entities.CustomUserDetails;
import com.example.vex360.features.user.services.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

/**
 * Adapts application users to Spring Security's {@link UserDetailsService}
 * contract.
 */
@Service
@RequiredArgsConstructor
public class CustomUserDetailsService implements UserDetailsService {

    private final UserService userService;

    /**
     * Loads a user by email without exposing whether the account exists.
     *
     * @param email email used as the authentication username
     * @return Spring Security user details for the matching account
     * @throws UsernameNotFoundException when no matching account exists
     */
    @Override
    public UserDetails loadUserByUsername(String email) throws UsernameNotFoundException {
        return userService.findUserByEmail(email)
                .map(CustomUserDetails::new)
                .orElseThrow(() -> new UsernameNotFoundException("Invalid credentials"));
    }
}
