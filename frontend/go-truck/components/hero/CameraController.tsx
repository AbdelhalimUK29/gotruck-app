import { useEffect, useRef } from "react";
import { useFrame } from "@react-three/fiber";
import * as THREE from "three";

import {
  animationTimeline,
  cameraMotion,
  easeInOutCubic,
} from "./heroAnimation";
import {
  reducedMotionRef,
  useDeviceTier,
  usePrefersReducedMotion,
} from "./motionPrefs";

export default function CameraController() {
  const initialized = useRef(false);
  const reducedMotion = usePrefersReducedMotion();
  const tier = useDeviceTier();
  const driftFactorRef = useRef(1);

  useEffect(() => {
    driftFactorRef.current =
      reducedMotion || tier === "mobile"
        ? 0
        : tier === "tablet"
          ? 0.5
          : 1;
  }, [reducedMotion, tier]);

  useFrame((state) => {
    const camera = state.camera;

    if (!initialized.current) {
      camera.position.set(
        cameraMotion.startPosition[0],
        cameraMotion.startPosition[1],
        cameraMotion.startPosition[2],
      );
      camera.lookAt(
        cameraMotion.lookAt[0],
        cameraMotion.lookAt[1],
        cameraMotion.lookAt[2],
      );
      initialized.current = true;
    }

    if (reducedMotionRef.current) {
      return;
    }

    const start = animationTimeline.truckApproach;
    const end = animationTimeline.crossing;
    const raw = THREE.MathUtils.clamp(
      (state.clock.elapsedTime - start) / (end - start),
      0,
      1,
    );
    const eased = easeInOutCubic(raw) * driftFactorRef.current;

    camera.position.x = THREE.MathUtils.lerp(
      cameraMotion.startPosition[0],
      cameraMotion.endPosition[0],
      eased,
    );
    camera.position.y = THREE.MathUtils.lerp(
      cameraMotion.startPosition[1],
      cameraMotion.endPosition[1],
      eased,
    );
    camera.position.z = THREE.MathUtils.lerp(
      cameraMotion.startPosition[2],
      cameraMotion.endPosition[2],
      eased,
    );

    camera.lookAt(
      cameraMotion.lookAt[0],
      cameraMotion.lookAt[1],
      cameraMotion.lookAt[2],
    );
  });

  return null;
}