"use client";

import {
  createContext,
  useContext,
  useEffect,
  useState,
  type ReactNode,
} from "react";

import keycloak from "./keycloak";
import { permissionService } from "@/src/services/permissionService";

interface KeycloakContextValue {
  initialized: boolean;
  authenticated: boolean;
  accessToken: string | undefined;
  idToken: string | undefined;
  user: {
    id: string;
    username?: string;
    email?: string;
    firstName?: string;
    lastName?: string;
  } | null;
  companyId: string | null;
  login: () => void;
  register: () => void;
  logout: () => void;
  refreshToken: () => Promise<string | undefined>;
}

const KeycloakContext = createContext<KeycloakContextValue | undefined>(
  undefined
);

export function KeycloakProvider({
  children,
}: {
  children: ReactNode;
}) {
  const [initialized, setInitialized] = useState(false);
  const [authenticated, setAuthenticated] = useState(false);
  const [accessToken, setAccessToken] = useState<string>();
  const [idToken, setIdToken] = useState<string>();
  const [user, setUser] = useState<{
  id: string;
  username?: string;
  email?: string;
  firstName?: string;
  lastName?: string;
} | null>(null);
  const [companyId, setCompanyId] = useState<string | null>(null);

  useEffect(() => {
    let mounted = true;

    const initialize = async () => {
      try {
        const authenticated = await keycloak.init({
          onLoad: "check-sso",
          pkceMethod: "S256",
          checkLoginIframe: false,
        });

        if (!mounted) return;

        setAuthenticated(authenticated);
        setAccessToken(keycloak.token);
        setInitialized(true);
        setIdToken(keycloak.idToken);
        if (authenticated && keycloak.tokenParsed) {
          const parsed = keycloak.tokenParsed;
          setUser({
            id: parsed.sub!,
            username: parsed.preferred_username,
            email: parsed.email,
            firstName: parsed.given_name,
            lastName: parsed.family_name,
          });
          // Extract company_id claim for multi-tenancy
          const companyIdClaim = parsed.company_id as string | undefined;
          setCompanyId(companyIdClaim ?? null);
        }
      } catch (error) {
        console.error("Keycloak initialization failed:", error);

        if (mounted) {
          setInitialized(true);
        }
      }
    };

    initialize();

    return () => {
      mounted = false;
    };
  }, []);

  useEffect(() => {
    if (!initialized || !authenticated) {
      return;
    }

    // Fetch and cache user permissions when authenticated
    permissionService.fetchAndCacheFeatures().catch((error) => {
      console.error("Failed to fetch user features:", error);
    });

    const interval = window.setInterval(async () => {
      try {
        const refreshed = await keycloak.updateToken(30);

        if (refreshed) {
          setAccessToken(keycloak.token);
        }
      } catch (error) {
        console.error("Failed to refresh Keycloak token:", error);

        setAuthenticated(false);
        setAccessToken(undefined);
      }
    }, 10_000);

    return () => {
      window.clearInterval(interval);
    };
  }, [initialized, authenticated]);

  // Clear permissions on logout
  useEffect(() => {
    if (!authenticated) {
      permissionService.clearCachedFeatures();
    }
  }, [authenticated]);

  const login = () => {
    keycloak.login();
  };

  const register = () => {
    keycloak.register();
  };

  const logout = () => {
    keycloak.logout({
      redirectUri: window.location.origin,
    });
  };

  const refreshToken = async () => {
    try {
      const wasRefreshed = await keycloak.updateToken(-1);

      if (wasRefreshed && keycloak.tokenParsed) {
        const parsed = keycloak.tokenParsed;

        setAccessToken(keycloak.token);
        setIdToken(keycloak.idToken);

        setUser({
          id: parsed.sub!,
          username: parsed.preferred_username,
          email: parsed.email,
          firstName: parsed.given_name,
          lastName: parsed.family_name,
        });

        const companyIdClaim = parsed.company_id as string | undefined;
        setCompanyId(companyIdClaim ?? null);

        return keycloak.token;
      }

      return keycloak.token;
    } catch (error) {
      console.error("Failed to refresh token:", error);

      setAuthenticated(false);
      setAccessToken(undefined);
      setCompanyId(null);
      setUser(null);

      return undefined;
    }
  };

  return (
    <KeycloakContext.Provider
      value={{
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
      }}
    >
      {children}
    </KeycloakContext.Provider>
  );
}

export function useKeycloak() {
  const context = useContext(KeycloakContext);

  if (!context) {
    throw new Error(
      "useKeycloak must be used inside KeycloakProvider"
    );
  }

  return context;
}