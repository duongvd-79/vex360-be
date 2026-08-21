package com.example.vex360.features.auth.repositories;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import com.example.vex360.features.auth.entities.RefreshToken;
import com.example.vex360.features.user.entities.User;

import jakarta.persistence.LockModeType;

public interface RefreshTokenRepository extends JpaRepository<RefreshToken, Integer> {
    Optional<RefreshToken> findByToken(String token);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select rt from RefreshToken rt where rt.token = :token")
    Optional<RefreshToken> findByTokenForUpdate(@Param("token") String token);

    void deleteByToken(String token);

    void deleteByUser(User user);

    List<RefreshToken> findAllByUser(User user);

    @Modifying
    @Query("DELETE FROM RefreshToken token WHERE token.expiryDate < :cutoff")
    int deleteByExpiryDateBefore(@Param("cutoff") Instant cutoff);
}
