"use client";

import { useKeycloak } from "@/src/auth/keycloakProvider";

export function useAuth() {
  const {
    initialized,
    authenticated,
    accessToken,
    idToken,
    user,
    companyId,
    login,
    register,
    logout,
    refreshToken,
  } = useKeycloak();

  return {
    isLoading: !initialized,
    isAuthenticated: authenticated,
    accessToken,
    idToken,
    user,
    companyId,
    login,
    register,
    logout,
    refreshToken,
  };
}