package com.example.vex360.features.designrequest.repositories;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import com.example.vex360.features.designrequest.entities.DesignDraft;

public interface DesignDraftRepository extends JpaRepository<DesignDraft, UUID> {
    Optional<DesignDraft> findFirstByDesignRequestIdOrderByVersionNumberDesc(UUID designRequestId);
}
