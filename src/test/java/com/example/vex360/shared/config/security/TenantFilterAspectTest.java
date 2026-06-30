package com.example.vex360.shared.config.security;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.test.util.ReflectionTestUtils;

import jakarta.persistence.EntityManager;
import jakarta.servlet.http.HttpServletRequest;

@ExtendWith(OutputCaptureExtension.class)
class TenantFilterAspectTest {

    @Test
    void skipsRequestPathLookupWhenRepositoryRunsOutsideWebRequest(CapturedOutput output) {
        HttpServletRequest request = mock(HttpServletRequest.class);
        TenantFilterAspect aspect = new TenantFilterAspect(request);
        when(request.getRequestURI()).thenThrow(new IllegalStateException("No thread-bound request found"));
        ReflectionTestUtils.setField(aspect, "entityManager", mock(EntityManager.class));

        aspect.enableTenantFilter();

        assertFalse(output.getOut().contains("Failed to enable tenant filter"));
        assertFalse(output.getErr().contains("Failed to enable tenant filter"));
    }
}
