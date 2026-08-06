package com.example.vex360.features.assetcleanup.controllers;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.example.vex360.features.assetcleanup.jobs.CloudinaryOrphanCleanupJob;
import com.example.vex360.shared.controllers.BaseController;
import com.example.vex360.shared.dtos.ApiResponse;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/v1/admin/cloudinary")
@RequiredArgsConstructor
@PreAuthorize("hasAuthority('ADMIN')")
@ConditionalOnProperty(prefix = "app.cloudinary.orphan-cleanup", name = "enabled", havingValue = "true")
public class CloudinaryOrphanCleanupController extends BaseController {

    private final CloudinaryOrphanCleanupJob cleanupJob;

    @PostMapping("/orphan-cleanup")
    public ResponseEntity<ApiResponse<Void>> cleanupOrphans() {
        cleanupJob.cleanupOrphans();
        return ok(null, "Cloudinary orphan cleanup completed");
    }
}
