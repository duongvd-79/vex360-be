package com.example.vex360.features.company.security;

import java.util.Arrays;

import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

import com.example.vex360.features.auth.entities.CustomUserDetails;
import com.example.vex360.features.user.entities.User;
import com.example.vex360.shared.config.security.RequireActiveCompany;
import com.example.vex360.shared.exceptions.AppException;
import com.example.vex360.shared.exceptions.ErrorCode;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class CompanyProfileAccessInterceptor implements HandlerInterceptor {
    private final CompanyProfileAccessGuard companyProfileAccessGuard;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        if (!(handler instanceof HandlerMethod handlerMethod)) {
            return true;
        }

        RequireActiveCompany requirement = AnnotatedElementUtils.findMergedAnnotation(
                handlerMethod.getMethod(), RequireActiveCompany.class);
        if (requirement == null) {
            requirement = AnnotatedElementUtils.findMergedAnnotation(
                    handlerMethod.getBeanType(), RequireActiveCompany.class);
        }
        if (requirement == null) {
            return true;
        }

        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !(authentication.getPrincipal() instanceof CustomUserDetails userDetails)) {
            throw new AppException(ErrorCode.UNAUTHENTICATED);
        }

        User currentUser = userDetails.getUser();
        boolean appliesToRole = Arrays.stream(requirement.roles())
                .anyMatch(role -> role == currentUser.getRole());
        if (appliesToRole) {
            companyProfileAccessGuard.requireActiveCompany(currentUser);
        }
        return true;
    }
}
