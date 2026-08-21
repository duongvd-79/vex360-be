package com.example.vex360.shared.config.security;

import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.UUID;

import org.hibernate.Session;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import jakarta.persistence.EntityManager;
import jakarta.servlet.http.HttpServletRequest;

@ExtendWith(MockitoExtension.class)
class TenantFilterAspectUnitTest {

    @Mock
    private EntityManager entityManager;

    @Mock
    private HttpServletRequest request;

    private TenantFilterAspect aspect;

    @BeforeEach
    void setUp() {
        aspect = new TenantFilterAspect(request);
        ReflectionTestUtils.setField(aspect, "entityManager", entityManager);
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
        TenantContext.setCurrentTenantId(UUID.randomUUID());
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
        RequestContextHolder.resetRequestAttributes();
    }

    @Test
    void visitorEndpointDoesNotEnableOrganizerTenantFilter() {
        when(request.getRequestURI()).thenReturn(
                "/api/v1/visitor/exhibitions/exhibition-id/booths/booth-id");

        aspect.enableTenantFilter();

        verify(entityManager, never()).unwrap(Session.class);
    }
}
