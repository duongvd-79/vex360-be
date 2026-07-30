package com.example.vex360.features.user.repositories;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import com.example.vex360.features.user.entities.User;
import com.example.vex360.shared.enums.Role;
import com.example.vex360.shared.enums.UserStatus;

import java.util.Optional;
import java.util.List;
import java.util.UUID;
import java.time.Instant;

import jakarta.persistence.LockModeType;

public interface UserRepository extends JpaRepository<User, UUID> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT u FROM User u WHERE u.id = :id")
    Optional<User> findByIdForUpdate(@Param("id") UUID id);

    Optional<User> findByEmail(String email);
    boolean existsByEmail(String email);

    @Query("""
            SELECT u FROM User u
            WHERE (:keyword IS NULL
                OR LOWER(u.fullName) LIKE LOWER(CONCAT('%', :keyword, '%'))
                OR LOWER(u.email) LIKE LOWER(CONCAT('%', :keyword, '%')))
              AND (:role IS NULL OR u.role = :role)
              AND (:status IS NULL OR u.status = :status)
            """)
    Page<User> searchUsers(
            @Param("keyword") String keyword,
            @Param("role") Role role,
            @Param("status") UserStatus status,
            Pageable pageable);

    List<User> findByRoleAndStatusOrderByFullNameAsc(Role role, UserStatus status);

    long countByCreatedAtBetween(Instant start, Instant end);

    @Query("SELECT u.role, COUNT(u) FROM User u GROUP BY u.role")
    List<Object[]> countGroupedByRole();

    @Query("SELECT u.status, COUNT(u) FROM User u GROUP BY u.status")
    List<Object[]> countGroupedByStatus();

    @Query("""
            SELECT FUNCTION('DATE', u.createdAt), COUNT(u)
            FROM User u
            WHERE u.createdAt BETWEEN :start AND :end
            GROUP BY FUNCTION('DATE', u.createdAt)
            ORDER BY FUNCTION('DATE', u.createdAt)
            """)
    List<Object[]> aggregateDailyRegistrations(
            @Param("start") Instant start,
            @Param("end") Instant end);
}
