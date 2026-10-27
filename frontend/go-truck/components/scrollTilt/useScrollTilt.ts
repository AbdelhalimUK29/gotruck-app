import { useScrollState } from "./scrollStore";
import { useDeviceTier, usePrefersReducedMotion } from "@/components/hero/motionPrefs";

const FULL_TILT_AT_VELOCITY = 3;
const DEAD_ZONE = 0.1;
const MAX_ROTATE_X = 10;
const MAX_ROTATE_Y = 18;

const TIER_TILT_FACTOR = {
  mobile: 0.45,
  tablet: 0.7,
  desktop: 1,
} as const;

export interface ScrollTilt {
  rotateX: number;
  rotateY: number;
}

export function useScrollTilt(): ScrollTilt {
  const { velocity } = useScrollState();
  const reducedMotion = usePrefersReducedMotion();
  const tier = useDeviceTier();

  if (reducedMotion) {
    return { rotateX: 0, rotateY: 0 };
  }

  const magnitude = Math.max(
    0,
    Math.min(
      1,
      (Math.abs(velocity) - DEAD_ZONE) /
        (FULL_TILT_AT_VELOCITY - DEAD_ZONE),
    ),
  );
  const factor = magnitude * TIER_TILT_FACTOR[tier];

  const direction = velocity < 0 ? -1 : 1;

  return {
    rotateX: direction * factor * MAX_ROTATE_X,
    rotateY: direction * factor * MAX_ROTATE_Y,
  };
}
