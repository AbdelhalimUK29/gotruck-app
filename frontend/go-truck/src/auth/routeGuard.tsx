"use client";

import { useEffect, useCallback } from "react";
import { useRouter, usePathname } from "next/navigation";
import { useAuth } from "./useAuth";

const ONBOARDING_PATH = "/manager/onboarding";

interface RouteGuardProps {
  children: React.ReactNode;
  /** Whether this route requires a companyId (default: true) */
  requireCompanyId?: boolean;
}

export function RouteGuard({
  children,
  requireCompanyId = true,
}: RouteGuardProps) {
  const { isLoading, isAuthenticated, companyId } = useAuth();
  const router = useRouter();
  const pathname = usePathname();

  const pushToDashboard = useCallback(() => {
    if (companyId) {
      router.push(`/manager/${companyId}`);
    }
  }, [router, companyId]);

  const pushToOnboarding = useCallback(() => {
    router.push("/manager/onboarding");
  }, [router]);

  useEffect(() => {
    // Wait for Keycloak to finish initialization
    if (isLoading) return;

    // Not authenticated → redirect to Keycloak login
    if (!isAuthenticated) {
      return;
    }
    
    // Has companyId but onboarding → redirect to company dashboard
    if (requireCompanyId && companyId && pathname === ONBOARDING_PATH) {
      pushToDashboard();
      return;
    }

    // Require companyId but missing and not onboarding → redirect to onboarding
    if (requireCompanyId && !companyId && pathname !== ONBOARDING_PATH) {
      pushToOnboarding();
      return;
    }
  }, [isLoading, isAuthenticated, companyId, pathname, requireCompanyId, pushToDashboard, pushToOnboarding]);
  
  // Show loading state while initializing
  if (isLoading) {
    return (
      <div className="flex min-h-screen items-center justify-center bg-bg">
        <div className="space-y-4 text-center">
          <div className="h-8 w-8 border-4 border-primary border-t-transparent rounded-full animate-spin mx-auto" />
          <p className="text-sm text-text-secondary">Loading authentication…</p>
        </div>
      </div>
    );
  }

  // Don't render children if redirecting (auth check will handle redirect)
  if (!isAuthenticated) {
    return null;
  }

  if (requireCompanyId && !companyId && pathname !== "/manager/onboarding") {
    return null;
  }

  if (requireCompanyId && companyId && pathname === "/manager/onboarding") {
    return null;
  }

  return <>{children}</>;
}