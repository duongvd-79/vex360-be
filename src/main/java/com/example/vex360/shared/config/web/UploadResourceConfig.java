package com.example.vex360.shared.config.web;

import java.nio.file.Path;
import java.nio.file.Paths;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class UploadResourceConfig implements WebMvcConfigurer {
    @Value("${app.upload.panorama-directory:uploads/panoramas}")
    private String panoramaDirectory;

    @Value("${app.upload.panorama-url-prefix:/uploads/panoramas}")
    private String panoramaUrlPrefix;

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        Path uploadDir = Paths.get(panoramaDirectory).toAbsolutePath().normalize();
        String pattern = panoramaUrlPrefix.endsWith("/")
                ? panoramaUrlPrefix + "**"
                : panoramaUrlPrefix + "/**";

        registry.addResourceHandler(pattern)
                .addResourceLocations(uploadDir.toUri().toString());
    }
}
