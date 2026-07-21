package com.example.vex360.features.designrequest.repositories;

import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import com.example.vex360.features.designrequest.entities.DesignRequestMessage;

public interface DesignRequestMessageRepository extends JpaRepository<DesignRequestMessage, UUID> {
    Page<DesignRequestMessage> findByDesignRequestId(UUID designRequestId, Pageable pageable);
}
