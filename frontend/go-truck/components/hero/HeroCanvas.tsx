"use client";

import { Canvas } from "@react-three/fiber";

import Environment from "./Environment";
import Road from "./Road";
import MainTruck from "./MainTruck";
import TrafficTruck from "./TrafficTruck";
import Particles from "./Particles";
import CameraController from "./CameraController";
import AnimationStateDriver from "./AnimationStateDriver";
import {
  cameraConfig,
  colors,
  trafficMotion,
  trafficLoopLength,
} from "./heroAnimation";
import {
  useDeviceTier,
  usePrefersReducedMotion,
} from "./motionPrefs";

export default function HeroCanvas() {
  const reducedMotion = usePrefersReducedMotion();
  const tier = useDeviceTier();
  const showTraffic = !reducedMotion && tier !== "mobile";

  return (
    <Canvas
      dpr={[1, 2]}
      shadows={!reducedMotion && tier !== "mobile"}
      gl={{ antialias: true, alpha: false, powerPreference: "high-performance" }}
      camera={{
        fov: cameraConfig.fov,
        near: 0.1,
        far: 200,
        position: [
          cameraConfig.position[0],
          cameraConfig.position[1],
          cameraConfig.position[2],
        ],
      }}
    >
      <Environment />
      <CameraController />
      <AnimationStateDriver />
      <Road />
      <MainTruck />
      {showTraffic && (
        <>
          <TrafficTruck
            color={colors.card}
            laneOffsetX={trafficMotion.lanes[0]}
            phase={0}
          />
          <TrafficTruck
            color={colors.lightOrange}
            laneOffsetX={trafficMotion.lanes[1]}
            phase={trafficLoopLength / 2}
          />
        </>
      )}
      <Particles />
    </Canvas>
  );
}