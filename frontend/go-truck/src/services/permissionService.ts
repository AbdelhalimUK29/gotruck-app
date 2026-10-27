import { api } from "@/src/api/apiClient";
import type {
  FeatureResponse,
  UserProfileWithFeaturesResponse,
} from "@/types/user-permission";
import { FeatureRoleEnum } from "@/types/user-permission";

// Cache key for localStorage
const USER_FEATURES_CACHE_KEY = "userFeatures";

/**
 * Permission service - handles fetching and caching user permissions
 */
export const permissionService = {
  /**
   * Fetch user profile with features from /manager/users/me
   */
  async getUserProfile(): Promise<UserProfileWithFeaturesResponse> {
    return api.get<UserProfileWithFeaturesResponse>("/manager/users/me");
  },

  /**
   * Fetch all available features from /manager/features
   */
  async getFeatures(): Promise<FeatureResponse[]> {
    return api.get<FeatureResponse[]>("/manager/features");
  },

  /**
   * Get user features from cache (localStorage)
   */
  getCachedFeatures(): FeatureRoleEnum[] {
    if (typeof window === "undefined") return [];

    try {
      const cached = localStorage.getItem(USER_FEATURES_CACHE_KEY);
      if (cached) {
        return JSON.parse(cached) as FeatureRoleEnum[];
      }
    } catch {
      // Silently handle parse errors
    }

    return [];
  },

  /**
   * Cache user features in localStorage
   */
  cacheFeatures(features: FeatureRoleEnum[]): void {
    if (typeof window === "undefined") return;

    try {
      localStorage.setItem(USER_FEATURES_CACHE_KEY, JSON.stringify(features));
    } catch {
      // Silently handle storage errors
    }
  },

  /**
   * Clear cached features
   */
  clearCachedFeatures(): void {
    if (typeof window === "undefined") return;

    try {
      localStorage.removeItem(USER_FEATURES_CACHE_KEY);
    } catch {
      // Silently handle storage errors
    }
  },

  /**
   * Check if user has a specific feature
   */
  hasFeature(features: FeatureRoleEnum[], feature: FeatureRoleEnum): boolean {
    return features.includes(feature);
  },

  /**
   * Check if user has administrative access (owner, superAdmin, or ADMIN feature)
   */
  hasAdminAccess(
    features: FeatureRoleEnum[],
    isOwner: boolean,
    isSuperAdmin: boolean
  ): boolean {
    return (
      isOwner || isSuperAdmin || this.hasFeature(features, FeatureRoleEnum.ADMIN)
    );
  },

  /**
   * Check if user has rental access (owner, superAdmin, or RENTAL feature)
   */
  hasRentalAccess(
    features: FeatureRoleEnum[],
    isOwner: boolean,
    isSuperAdmin: boolean
  ): boolean {
    return (
      isOwner ||
      isSuperAdmin ||
      this.hasFeature(features, FeatureRoleEnum.RENTAL)
    );
  },

  /**
   * Check if user has employee management access (owner, superAdmin, or ADMIN feature)
   */
  hasEmployeeAccess(
    features: FeatureRoleEnum[],
    isOwner: boolean,
    isSuperAdmin: boolean
  ): boolean {
    return (
      isOwner ||
      isSuperAdmin ||
      this.hasFeature(features, FeatureRoleEnum.ADMIN)
    );
  },

  /**
   * Check if user has trucks access (owner, superAdmin, or TRUCKS feature)
   */
  hasTrucksAccess(
    features: FeatureRoleEnum[],
    isOwner: boolean,
    isSuperAdmin: boolean
  ): boolean {
    return (
      isOwner ||
      isSuperAdmin ||
      this.hasFeature(features, FeatureRoleEnum.TRUCKS)
    );
  },

  /**
   * Fetch and cache user features (call on auth init)
   */
  async fetchAndCacheFeatures(): Promise<FeatureRoleEnum[]> {
    try {
      const [profile] = await Promise.all([
        this.getUserProfile(),
        this.getFeatures(),
      ]);

      // Cache the features
      this.cacheFeatures(profile.features);

      return profile.features;
    } catch (error) {
      console.error("Failed to fetch user features:", error);
      // Fall back to cached features or empty array
      return this.getCachedFeatures();
    }
  },

  /**
   * Refresh user features from server (call when permissions may have changed)
   */
  async refreshFeatures(): Promise<FeatureRoleEnum[]> {
    try {
      // Invalidate cache
      this.clearCachedFeatures();

      // Fetch fresh data
      const features = await this.fetchAndCacheFeatures();

      return features;
    } catch (error) {
      console.error("Failed to refresh user features:", error);
      // Fall back to cached features, if any
      return this.getCachedFeatures();
    }
  },

  /**
   * Get user's permissions summary
   */
  getUserPermissions(profile?: UserProfileWithFeaturesResponse): {
    hasAdminAccess: boolean;
    hasRentalAccess: boolean;
    hasEmployeeAccess: boolean;
    hasTrucksAccess: boolean;
    features: FeatureRoleEnum[];
    isOwner: boolean;
    isSuperAdmin: boolean;
  } {
    const cachedFeatures = this.getCachedFeatures();
    const features = profile?.features || cachedFeatures;
    const isOwner = profile?.owner ?? false;
    const isSuperAdmin = profile?.superAdmin ?? false;

    return {
      hasAdminAccess: this.hasAdminAccess(features, isOwner, isSuperAdmin),
      hasRentalAccess: this.hasRentalAccess(features, isOwner, isSuperAdmin),
      hasEmployeeAccess: this.hasEmployeeAccess(features, isOwner, isSuperAdmin),
      hasTrucksAccess: this.hasTrucksAccess(features, isOwner, isSuperAdmin),
      features,
      isOwner,
      isSuperAdmin,
    };
  },
};
