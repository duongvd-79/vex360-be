package com.example.vex360.shared.services;

import org.springframework.web.multipart.MultipartFile;

import com.example.vex360.shared.dtos.CloudinaryResponse;

public interface CloudService {
    CloudinaryResponse upload(MultipartFile file);

    void delete(String publicId, String resourceType);
}
