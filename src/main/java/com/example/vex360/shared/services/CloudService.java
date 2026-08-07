package com.example.vex360.shared.services;

import org.springframework.web.multipart.MultipartFile;

import com.example.vex360.shared.dtos.CloudinaryResponse;

public interface CloudService {
    CloudinaryResponse upload(MultipartFile file);

    /**
     * Upload a file to a specific Cloudinary folder (e.g. "panorama").
     */
    CloudinaryResponse uploadToFolder(MultipartFile file, String folder);

    void delete(String publicId, String resourceType);

    long deleteAndGetSize(String publicId, String resourceType);
}
