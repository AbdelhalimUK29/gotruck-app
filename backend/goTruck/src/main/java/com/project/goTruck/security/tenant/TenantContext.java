package com.project.goTruck.security.tenant;

public final class TenantContext {

    private static final ThreadLocal<Long> currentCompanyId = new ThreadLocal<>();

    private TenantContext() {
    }

    public static void setCurrentCompanyId(Long companyId) {
        currentCompanyId.set(companyId);
    }

    public static Long getCurrentCompanyId() {
        return currentCompanyId.get();
    }

    public static void clear() {
        currentCompanyId.remove();
    }
}