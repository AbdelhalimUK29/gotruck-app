import { useSyncExternalStore } from "react";

export type HeroAnimationState =
  | "INTRO"
  | "MAIN_TRUCK_APPROACH"
  | "OPPOSING_TRAFFIC"
  | "CROSSING"
  | "REVEAL"
  | "IDLE";

export const animationTimeline = {
  intro: 0,
  truckApproach: 1,
  traffic: 2.8,
  crossing: 4,
  reveal: 4.4,
  idle: 6,
} as const;

export const colors = {
  primary: "#F97316",
  primaryHover: "#EA580C",
  lightOrange: "#FED7AA",
  bg: "#FAFAFA",
  card: "#FFFFFF",
  textPrimary: "#111827",
  textSecondary: "#6B7280",
  border: "#E5E7EB",
} as const;

export const cameraConfig = {
  fov: 45,
  position: [0, 2.4, 9] as const,
  lookAt: [0, 0.6, -14] as const,
} as const;

export const mainTruckConfig = {
  startPosition: [0, 0, -32] as const,
  scale: 1,
} as const;

export const truckMotion = {
  startZ: mainTruckConfig.startPosition[2],
  endZ: -5,
  wheelRadius: 0.6,
} as const;

export const cameraMotion = {
  startPosition: [...cameraConfig.position] as const,
  endPosition: [0, 2.6, 11] as const,
  lookAt: cameraConfig.lookAt,
} as const;

export const trafficMotion = {
  enterTime: animationTimeline.traffic,
  enterZ: 11.5,
  speed: 14,
  scale: 0.5,
  lanes: [-2.2, 2.2] as const,
  loopFarZ: -120,
} as const;

export const trafficLoopLength =
  (trafficMotion.enterZ - trafficMotion.loopFarZ) / trafficMotion.speed;

export const idleConfig = {
  startTime: animationTimeline.idle,
  fadeIn: 1.5,
} as const;

export function easeInOutCubic(x: number): number {
  return x < 0.5 ? 4 * x * x * x : 1 - Math.pow(-2 * x + 2, 3) / 2;
}

type StateListener = (state: HeroAnimationState) => void;

class AnimationStateMachine {
  private state: HeroAnimationState = "INTRO";
  private listeners = new Set<StateListener>();

  get current(): HeroAnimationState {
    return this.state;
  }

  setState(next: HeroAnimationState): void {
    if (next === this.state) {
      return;
    }

    this.state = next;

    this.listeners.forEach((listener) => {
      listener(next);
    });
  }

  subscribe(listener: StateListener): () => void {
    this.listeners.add(listener);

    return () => {
      this.listeners.delete(listener);
    };
  }
}

export const heroAnimationState = new AnimationStateMachine();

function subscribe(listener: () => void): () => void {
  return heroAnimationState.subscribe(listener);
}

function getSnapshot(): HeroAnimationState {
  return heroAnimationState.current;
}

function getServerSnapshot(): HeroAnimationState {
  return "INTRO";
}

export function useHeroAnimationState(): HeroAnimationState {
  return useSyncExternalStore(subscribe, getSnapshot, getServerSnapshot);
}