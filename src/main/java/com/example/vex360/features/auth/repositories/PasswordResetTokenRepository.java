package com.example.vex360.features.auth.repositories;

import java.time.Instant;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import com.example.vex360.features.auth.entities.PasswordResetToken;
import com.example.vex360.features.user.entities.User;

public interface PasswordResetTokenRepository extends JpaRepository<PasswordResetToken, Integer> {
    Optional<PasswordResetToken> findByToken(String token);

    void deleteByUser(User user);

    void deleteByToken(String token);

    @Modifying
    @Query("DELETE FROM PasswordResetToken token WHERE token.expiryDate < :cutoff")
    int deleteByExpiryDateBefore(@Param("cutoff") Instant cutoff);
}
