package com.example.vex360.features.auth.repositories;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.example.vex360.features.auth.entities.RegistrationToken;
import com.example.vex360.shared.entities.User;

@Repository
public interface RegistrationTokenRepository extends JpaRepository<RegistrationToken, Integer> {
    Optional<RegistrationToken> findByToken(String token);
    void deleteByUser(User user);
}
