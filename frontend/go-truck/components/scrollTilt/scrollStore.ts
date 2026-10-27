import { useSyncExternalStore } from "react";

export type ScrollDirection = "up" | "down" | "none";

export interface ScrollSnapshot {
  y: number;
  velocity: number;
  direction: ScrollDirection;
}

const EMPTY_SNAPSHOT: ScrollSnapshot = {
  y: 0,
  velocity: 0,
  direction: "none",
};

const INPUT_SMOOTHING_FACTOR = 0.09;
const TILT_SMOOTHING_FACTOR = 0.045;
const VELOCITY_EPSILON = 0.0005;
const DELTA_EPSILON = 0.5;

class ScrollStore {
  private listeners = new Set<() => void>();
  private snapshot: ScrollSnapshot = EMPTY_SNAPSHOT;
  private lastY = 0;
  private lastTime = 0;
  private inputVelocity = 0;
  private velocity = 0;
  private rafId: number | null = null;
  private listening = false;

  private start = (): void => {
    if (this.listening || typeof window === "undefined") {
      return;
    }

    this.listening = true;
    this.lastY = window.scrollY;
    this.lastTime = performance.now();

    window.addEventListener("scroll", this.handleScroll, { passive: true });
    this.rafId = requestAnimationFrame(this.tick);
  };

  private handleScroll = (): void => {
    if (this.rafId === null) {
      this.rafId = requestAnimationFrame(this.tick);
    }
  };

  private tick = (): void => {
    const now = performance.now();
    const y = window.scrollY;
    const dt = Math.max(1, now - this.lastTime);
    const deltaY = y - this.lastY;
    const instVelocity = deltaY / dt;

    this.inputVelocity +=
      (instVelocity - this.inputVelocity) * INPUT_SMOOTHING_FACTOR;

    if (Math.abs(this.inputVelocity) < VELOCITY_EPSILON) {
      this.inputVelocity = 0;
    }

    this.velocity +=
      (this.inputVelocity - this.velocity) * TILT_SMOOTHING_FACTOR;

    if (Math.abs(this.velocity) < VELOCITY_EPSILON) {
      this.velocity = 0;
    }

    let direction: ScrollDirection = "none";

    if (Math.abs(deltaY) > DELTA_EPSILON) {
      direction = deltaY > 0 ? "down" : "up";
    } else if (Math.abs(this.inputVelocity) >= VELOCITY_EPSILON) {
      direction = this.inputVelocity > 0 ? "down" : "up";
    }

    const next: ScrollSnapshot = {
      y,
      velocity: this.velocity,
      direction,
    };

    const changed =
      next.y !== this.snapshot.y ||
      next.direction !== this.snapshot.direction ||
      Math.abs(next.velocity - this.snapshot.velocity) > 0.000001;

    this.lastY = y;
    this.lastTime = now;

    if (changed) {
      this.snapshot = next;
      this.listeners.forEach((listener) => listener());
    }

    if (
      Math.abs(instVelocity) > VELOCITY_EPSILON ||
      Math.abs(this.inputVelocity) > VELOCITY_EPSILON ||
      Math.abs(this.velocity) > VELOCITY_EPSILON
    ) {
      this.rafId = requestAnimationFrame(this.tick);
    } else {
      this.rafId = null;
    }
  };

  subscribe = (listener: () => void): (() => void) => {
    this.start();
    this.listeners.add(listener);

    return () => {
      this.listeners.delete(listener);
    };
  };

  getSnapshot = (): ScrollSnapshot => this.snapshot;
}

export const scrollStore = new ScrollStore();

export function useScrollState(): ScrollSnapshot {
  return useSyncExternalStore(
    scrollStore.subscribe,
    scrollStore.getSnapshot,
    () => EMPTY_SNAPSHOT,
  );
}