package com.example.vex360.shared.dtos;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class CloudinaryResponse {
    private String url;          // Secure HTTPS link
    private String publicId;     // Unique ID on Cloudinary (for deletion or modification)
    private String fileName;     // Original file name
    private Long fileSize;       // File size in bytes
    private String fileType;     // File type (mime type, e.g., image/jpeg)
    private Integer width;       // Image width (pixels)
    private Integer height;      // Image height (pixels)
}
