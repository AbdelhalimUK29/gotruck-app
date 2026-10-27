import { useRef } from "react";
import { useFrame } from "@react-three/fiber";

import {
  animationTimeline,
  heroAnimationState,
  type HeroAnimationState,
} from "./heroAnimation";
import { reducedMotionRef } from "./motionPrefs";

export default function AnimationStateDriver() {
  const lastState = useRef<HeroAnimationState | null>(null);

  useFrame((state) => {
    if (reducedMotionRef.current) {
      heroAnimationState.setState("REVEAL");
      lastState.current = "REVEAL";

      return;
    }

    const t = state.clock.elapsedTime;
    let next: HeroAnimationState = "INTRO";

    if (t >= animationTimeline.idle) {
      next = "IDLE";
    } else if (t >= animationTimeline.reveal) {
      next = "REVEAL";
    } else if (t >= animationTimeline.crossing) {
      next = "CROSSING";
    } else if (t >= animationTimeline.traffic) {
      next = "OPPOSING_TRAFFIC";
    } else if (t >= animationTimeline.truckApproach) {
      next = "MAIN_TRUCK_APPROACH";
    }

    if (next !== lastState.current) {
      lastState.current = next;
      heroAnimationState.setState(next);
    }
  });

  return null;
}