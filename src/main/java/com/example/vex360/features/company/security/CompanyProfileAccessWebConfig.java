package com.example.vex360.features.company.security;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import lombok.RequiredArgsConstructor;

@Configuration
@RequiredArgsConstructor
public class CompanyProfileAccessWebConfig implements WebMvcConfigurer {
    private final CompanyProfileAccessInterceptor companyProfileAccessInterceptor;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(companyProfileAccessInterceptor);
    }
}
