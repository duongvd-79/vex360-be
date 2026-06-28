package com.example.vex360.shared.config.security;

import java.util.UUID;

import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.hibernate.Filter;
import org.hibernate.Session;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;

@Aspect
@Component
@Slf4j
public class TenantFilterAspect {

    @PersistenceContext
    private EntityManager entityManager;

    @Autowired(required = false)
    private HttpServletRequest request;

    @Before("execution(* com.example.vex360..*Repository.*(..))")
    public void enableTenantFilter() {
        try {
            if (request != null) {
                String path = request.getRequestURI();
                if (path != null && path.startsWith("/api/v1/public/")) {
                    log.debug("Bypassing tenant filter for public endpoint: {}", path);
                    return;
                }
            }

            UUID tenantId = TenantContext.getCurrentTenantId();
            if (tenantId != null) {
                Session session = entityManager.unwrap(Session.class);
                Filter filter = session.enableFilter("tenantFilter");
                filter.setParameter("organizerId", tenantId);
                log.debug("Enabled tenantFilter with organizerId: {}", tenantId);
            }
        } catch (Exception e) {
            log.error("Failed to enable tenant filter", e);
        }
    }
}
