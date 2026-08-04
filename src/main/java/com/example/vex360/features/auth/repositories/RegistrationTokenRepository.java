package com.example.vex360.features.auth.repositories;

import java.time.Instant;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import com.example.vex360.features.auth.entities.RegistrationToken;
import com.example.vex360.features.user.entities.User;

public interface RegistrationTokenRepository extends JpaRepository<RegistrationToken, Integer> {
    Optional<RegistrationToken> findByToken(String token);

    void deleteByUser(User user);

    @Modifying
    @Query("DELETE FROM RegistrationToken token WHERE token.expiryDate < :cutoff")
    int deleteByExpiryDateBefore(@Param("cutoff") Instant cutoff);
}
