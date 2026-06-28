package com.example.vex360.shared.config.security;

import java.util.UUID;

public class TenantContext {
    private static final ThreadLocal<UUID> currentTenantId = new ThreadLocal<>();

    public static void setCurrentTenantId(UUID tenantId) {
        currentTenantId.set(tenantId);
    }

    public static UUID getCurrentTenantId() {
        return currentTenantId.get();
    }

    public static void clear() {
        currentTenantId.remove();
    }
}
