"use client";

import { useState, useEffect, useCallback } from "react";
import { permissionService } from "@/src/services/permissionService";
import { useAuth } from "@/src/auth/useAuth";
import type { FeatureRoleEnum } from "@/types/user-permission";

interface PermissionState {
  features: FeatureRoleEnum[];
  isLoading: boolean;
  error: string | null;
  isOwner: boolean;
  isSuperAdmin: boolean;
}

interface UsePermissionsResult {
  permissions: {
    hasAdminAccess: boolean;
    hasRentalAccess: boolean;
    hasEmployeeAccess: boolean;
    hasTrucksAccess: boolean;
    features: FeatureRoleEnum[];
  };
  isLoading: boolean;
  error: string | null;
  refreshPermissions: () => Promise<void>;
  hasFeature: (feature: FeatureRoleEnum) => boolean;
  canAccessSettings: boolean;
  canAccessEmployees: boolean;
  canAccessRentals: boolean;
}

/**
 * Custom hook for checking user permissions based on features
 *
 * Usage:
 * ```tsx
 * const {
 *   canAccessSettings,
 *   canAccessEmployees,
 *   canAccessRentals,
 *   refreshPermissions
 * } = usePermission();
 *
 * return (
 *   <>
 *     {canAccessSettings && <CompanySettings />}
 *     {canAccessEmployees && <EmployeeList />}
 *     {canAccessRentals && <RentalList />}
 *   </>
 * );
 * ```
 */
export function usePermission(): UsePermissionsResult {
  const { user, isAuthenticated } = useAuth();
  const [state, setState] = useState<PermissionState>({
    features: [],
    isLoading: true,
    error: null,
    isOwner: false,
    isSuperAdmin: false,
  });

  /**
   * Check if user has a specific feature
   */
  const hasFeature = useCallback(
    (feature: FeatureRoleEnum): boolean => {
      return permissionService.hasFeature(state.features, feature);
    },
    [state.features]
  );

  /**
   * Fetch and cache user features
   */
  const fetchFeatures = useCallback(async () => {
    setState((prev) => ({ ...prev, isLoading: true, error: null }));

    try {
      const profile = await permissionService.getUserProfile();
      // Also fetch available features for validation
      await permissionService.getFeatures();

      // Update state with profile data
      setState({
        features: profile.features,
        isLoading: false,
        error: null,
        isOwner: profile.owner,
        isSuperAdmin: profile.superAdmin,
      });
    } catch (error) {
      const errorMessage =
        error instanceof Error ? error.message : "Failed to load permissions";

      // Fall back to cached features
      const cachedFeatures = permissionService.getCachedFeatures();
      setState({
        features: cachedFeatures,
        isLoading: false,
        error: errorMessage,
        isOwner: false,
        isSuperAdmin: false,
      });

      throw error;
    }
  }, []);

  /**
   * Refresh permissions from server (call when needed)
   */
  const refreshPermissions = useCallback(async () => {
    try {
      await permissionService.refreshFeatures();
      // Re-fetch cached features
      setState((prev) => ({
        ...prev,
        features: permissionService.getCachedFeatures(),
      }));
    } catch (error) {
      console.error("Failed to refresh permissions:", error);
      throw error;
    }
  }, []);

  // Initial fetch and whenever auth state changes
  useEffect(() => {
    if (isAuthenticated) {
      fetchFeatures();
    } else {
      // Clear permissions when not authenticated
      setState({
        features: [],
        isLoading: false,
        error: null,
        isOwner: false,
        isSuperAdmin: false,
      });
    }
  }, [isAuthenticated, fetchFeatures]);

  // Derived permissions - check owner, superAdmin, or feature
  const canAccessSettings = permissionService.hasAdminAccess(
    state.features,
    state.isOwner,
    state.isSuperAdmin
  );
  const canAccessEmployees = permissionService.hasEmployeeAccess(
    state.features,
    state.isOwner,
    state.isSuperAdmin
  );
  const canAccessRentals = permissionService.hasRentalAccess(
    state.features,
    state.isOwner,
    state.isSuperAdmin
  );

  return {
    permissions: {
      hasAdminAccess: permissionService.hasAdminAccess(
        state.features,
        state.isOwner,
        state.isSuperAdmin
      ),
      hasRentalAccess: permissionService.hasRentalAccess(
        state.features,
        state.isOwner,
        state.isSuperAdmin
      ),
      hasEmployeeAccess: permissionService.hasEmployeeAccess(
        state.features,
        state.isOwner,
        state.isSuperAdmin
      ),
      hasTrucksAccess: permissionService.hasTrucksAccess(
        state.features,
        state.isOwner,
        state.isSuperAdmin
      ),
      features: state.features,
    },
    isLoading: state.isLoading,
    error: state.error,
    refreshPermissions,
    hasFeature,
    canAccessSettings,
    canAccessEmployees,
    canAccessRentals,
  };
}
