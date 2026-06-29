package com.example.vex360.features.exhibition.repositories;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.example.vex360.shared.entities.Payment;

@Repository
public interface PaymentRepository extends JpaRepository<Payment, Integer> {
    Optional<Payment> findByOrderCode(Long orderCode);
    Optional<Payment> findFirstByExhibitorRegistrationIdOrderByCreatedAtDesc(Integer exhibitorRegistrationId);
}
