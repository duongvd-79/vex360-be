package com.example.vex360.features.exhibition.repositories;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import com.example.vex360.features.exhibition.entities.Payment;

public interface PaymentRepository extends JpaRepository<Payment, Integer> {
    Optional<Payment> findByOrderCode(Long orderCode);

    Optional<Payment> findFirstByExhibitorRegistrationIdOrderByCreatedAtDesc(Integer exhibitorRegistrationId);

    List<Payment> findByExhibitorRegistrationIdIn(List<Integer> exhibitorRegistrationIds);
}
