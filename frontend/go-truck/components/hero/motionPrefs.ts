import { useEffect, useSyncExternalStore } from "react";

export type DeviceTier = "mobile" | "tablet" | "desktop";

const REDUCED_MOTION_QUERY = "(prefers-reduced-motion: reduce)";
const MOBILE_QUERY = "(max-width: 767px)";
const TABLET_QUERY = "(min-width: 768px) and (max-width: 1023px)";

export const reducedMotionRef = { current: false };

function getReducedMotionSnapshot(): boolean {
  if (typeof window === "undefined") {
    return false;
  }

  return window.matchMedia(REDUCED_MOTION_QUERY).matches;
}

function subscribeReducedMotion(callback: () => void): () => void {
  if (typeof window === "undefined") {
    return () => {};
  }

  const mql = window.matchMedia(REDUCED_MOTION_QUERY);

  mql.addEventListener("change", callback);

  return () => {
    mql.removeEventListener("change", callback);
  };
}

export function usePrefersReducedMotion(): boolean {
  const reduced = useSyncExternalStore(
    subscribeReducedMotion,
    getReducedMotionSnapshot,
    () => false,
  );

  useEffect(() => {
    reducedMotionRef.current = reduced;
  }, [reduced]);

  return reduced;
}

function getTierSnapshot(): DeviceTier {
  if (typeof window === "undefined") {
    return "desktop";
  }

  if (window.matchMedia(MOBILE_QUERY).matches) {
    return "mobile";
  }

  if (window.matchMedia(TABLET_QUERY).matches) {
    return "tablet";
  }

  return "desktop";
}

function subscribeTier(callback: () => void): () => void {
  if (typeof window === "undefined") {
    return () => {};
  }

  const mqls = [
    window.matchMedia(MOBILE_QUERY),
    window.matchMedia(TABLET_QUERY),
  ];

  mqls.forEach((mql) => {
    mql.addEventListener("change", callback);
  });

  return () => {
    mqls.forEach((mql) => {
      mql.removeEventListener("change", callback);
    });
  };
}

export function useDeviceTier(): DeviceTier {
  return useSyncExternalStore(
    subscribeTier,
    getTierSnapshot,
    () => "desktop",
  );
}