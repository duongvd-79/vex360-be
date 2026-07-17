package com.example.vex360.features.company;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.lang.reflect.Method;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.method.HandlerMethod;

import com.example.vex360.features.auth.entities.CustomUserDetails;
import com.example.vex360.features.company.security.CompanyProfileAccessGuard;
import com.example.vex360.features.company.security.CompanyProfileAccessInterceptor;
import com.example.vex360.shared.config.security.RequireActiveCompany;
import com.example.vex360.features.user.entities.User;
import com.example.vex360.shared.enums.Role;
import com.example.vex360.shared.exceptions.AppException;
import com.example.vex360.shared.exceptions.ErrorCode;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

class CompanyProfileAccessInterceptorUnitTest {
    private final CompanyProfileAccessGuard guard = mock(CompanyProfileAccessGuard.class);
    private final CompanyProfileAccessInterceptor interceptor = new CompanyProfileAccessInterceptor(guard);
    private final HttpServletRequest request = mock(HttpServletRequest.class);
    private final HttpServletResponse response = mock(HttpServletResponse.class);

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void matchingRoleRequiresActiveCompany() throws Exception {
        User user = authenticate(Role.EXHIBITOR);

        interceptor.preHandle(request, response, handler(new ActiveExhibitorController()));

        verify(guard).requireActiveCompany(user);
    }

    @Test
    void differentRoleIsLeftForMethodSecurity() throws Exception {
        User user = authenticate(Role.ORGANIZER);

        interceptor.preHandle(request, response, handler(new ActiveExhibitorController()));

        verify(guard, never()).requireActiveCompany(user);
    }

    @Test
    void controllerWithoutRequirementIsNotGuarded() throws Exception {
        User user = authenticate(Role.EXHIBITOR);

        interceptor.preHandle(request, response, handler(new OnboardingController()));

        verify(guard, never()).requireActiveCompany(user);
    }

    @Test
    void protectedControllerRequiresAuthentication() {
        AppException exception = assertThrows(AppException.class,
                () -> interceptor.preHandle(
                        request,
                        response,
                        handler(new ActiveExhibitorController())));

        assertEquals(ErrorCode.UNAUTHENTICATED, exception.getErrorCode());
    }

    private User authenticate(Role role) {
        User user = new User();
        user.setRole(role);
        CustomUserDetails userDetails = new CustomUserDetails(user);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(userDetails, null, List.copyOf(userDetails.getAuthorities())));
        return user;
    }

    private HandlerMethod handler(Object controller) throws NoSuchMethodException {
        Method method = controller.getClass().getDeclaredMethod("handle");
        return new HandlerMethod(controller, method);
    }

    @RequireActiveCompany(roles = Role.EXHIBITOR)
    private static class ActiveExhibitorController {
        public void handle() {
        }
    }

    private static class OnboardingController {
        public void handle() {
        }
    }
}
