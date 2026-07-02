package com.example.vex360.features.auth.services;

import com.example.vex360.features.auth.entities.CustomUserDetails;
import com.example.vex360.features.user.services.UserService;
import com.example.vex360.features.user.entities.User;

import lombok.RequiredArgsConstructor;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class CustomUserDetailsService implements UserDetailsService {

    private final UserService userService;

    @Override
    public UserDetails loadUserByUsername(String email) throws UsernameNotFoundException {
        User user = userService.getUserByEmail(email);
        return new CustomUserDetails(user);
    }
}
