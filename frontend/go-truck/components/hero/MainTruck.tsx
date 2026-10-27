import { useMemo, useRef } from "react";
import { useFrame } from "@react-three/fiber";
import * as THREE from "three";

import {
  colors,
  animationTimeline,
  mainTruckConfig,
  truckMotion,
  easeInOutCubic,
  idleConfig,
} from "./heroAnimation";
import { createSoftTexture } from "./particleTexture";
import {
  reducedMotionRef,
  useDeviceTier,
  usePrefersReducedMotion,
} from "./motionPrefs";

const WHEEL_ROTATION = Math.PI / 2;
const GLASS_COLOR = "#1F2937";
const CHASSIS_COLOR = "#374151";
const TIRE_COLOR = "#1F2937";

const TRUCK_WIDTH = 2.3;

const WHEEL_POSITIONS: [number, number, number][] = [
  [-1.05, 0.6, 3.2],
  [1.05, 0.6, 3.2],
  [-1.05, 0.6, 1.3],
  [1.05, 0.6, 1.3],
  [-1.05, 0.6, -1.4],
  [1.05, 0.6, -1.4],
  [-1.05, 0.6, -3.1],
  [1.05, 0.6, -3.1],
  [-1.05, 0.6, -4.8],
  [1.05, 0.6, -4.8],
];

function Wheel({
  position,
  wheelRef,
}: {
  position: [number, number, number];
  wheelRef: (mesh: THREE.Mesh | null) => void;
}) {
  return (
    <group position={position} rotation={[0, 0, WHEEL_ROTATION]}>
      <mesh ref={wheelRef} castShadow>
        <cylinderGeometry args={[0.6, 0.6, 0.35, 20]} />
        <meshStandardMaterial
          color={TIRE_COLOR}
          roughness={0.8}
          metalness={0.1}
        />
      </mesh>

      <mesh>
        <cylinderGeometry args={[0.16, 0.16, 0.4, 12]} />
        <meshStandardMaterial
          color={CHASSIS_COLOR}
          roughness={0.5}
          metalness={0.6}
        />
      </mesh>
    </group>
  );
}

export default function MainTruck() {
  const truckRef = useRef<THREE.Group>(null);
  const wheelRefs = useRef<(THREE.Mesh | null)[]>([]);
  const reducedMotion = usePrefersReducedMotion();
  const tier = useDeviceTier();

  const scaleFactor =
    reducedMotion || tier === "mobile"
      ? 0.85
      : tier === "tablet"
        ? 0.9
        : 1;

  const dustTexture = useMemo(() => createSoftTexture(), []);
  const dustMaterialRef = useRef<THREE.MeshBasicMaterial | null>(null);

  const setWheelRef =
    (index: number) => (mesh: THREE.Mesh | null) => {
      wheelRefs.current[index] = mesh;
    };

  useFrame((state) => {
    const truck = truckRef.current;

    if (!truck) return;

    if (reducedMotionRef.current) {
      truck.position.set(0, 0, truckMotion.endZ);
      truck.rotation.z = 0;

      wheelRefs.current.forEach((wheel) => {
        if (wheel) {
          wheel.rotation.y = 0;
        }
      });

      if (dustMaterialRef.current) {
        dustMaterialRef.current.opacity = 0;
      }

      return;
    }

    const start = animationTimeline.truckApproach;
    const end = animationTimeline.crossing;
    const raw = THREE.MathUtils.clamp(
      (state.clock.elapsedTime - start) / (end - start),
      0,
      1,
    );
    const eased = easeInOutCubic(raw);

    truck.position.z = THREE.MathUtils.lerp(
      truckMotion.startZ,
      truckMotion.endZ,
      eased,
    );

    const distance =
      (truckMotion.endZ - truckMotion.startZ) * eased;
    const spin = distance / truckMotion.wheelRadius;

    wheelRefs.current.forEach((wheel) => {
      if (wheel) {
        wheel.rotation.y = spin;
      }
    });

    const settle = Math.max(
      0,
      1 -
        Math.max(0, state.clock.elapsedTime - animationTimeline.crossing) /
          1.4,
    );

    if (dustMaterialRef.current) {
      dustMaterialRef.current.opacity =
        0.22 *
        settle *
        (0.65 + 0.35 * Math.sin(state.clock.elapsedTime * 2.2));
    }

    const idleFactor = Math.max(
      0,
      Math.min(
        1,
        (state.clock.elapsedTime - idleConfig.startTime) /
          idleConfig.fadeIn,
      ),
    );

    truck.position.y =
      Math.sin(state.clock.elapsedTime * 1.6) * 0.03 * idleFactor;
    truck.rotation.z =
      Math.sin(state.clock.elapsedTime * 1.1) * 0.004 * idleFactor;
  });

  return (
    <group
      ref={truckRef}
      position={[
        mainTruckConfig.startPosition[0],
        mainTruckConfig.startPosition[1],
        mainTruckConfig.startPosition[2],
      ]}
      scale={mainTruckConfig.scale * scaleFactor}
    >
      <mesh position={[0, 0.25, 0]} castShadow>
        <boxGeometry args={[TRUCK_WIDTH, 0.5, 9.2]} />
        <meshStandardMaterial
          color={CHASSIS_COLOR}
          roughness={0.7}
          metalness={0.3}
        />
      </mesh>

      <mesh position={[0, 1.4, 1.9]} castShadow>
        <boxGeometry args={[2.2, 2.0, 2.4]} />
        <meshStandardMaterial
          color={colors.primary}
          roughness={0.35}
          metalness={0.05}
        />
      </mesh>

      <mesh position={[0, 2.0, 3.05]} rotation={[-0.22, 0, 0]}>
        <boxGeometry args={[1.95, 0.7, 0.08]} />
        <meshStandardMaterial
          color={GLASS_COLOR}
          roughness={0.15}
          metalness={0.5}
        />
      </mesh>

      <mesh position={[0, 0.35, -1.1]} castShadow>
        <boxGeometry args={[2.2, 0.25, 0.3]} />
        <meshStandardMaterial
          color={GLASS_COLOR}
          roughness={0.4}
          metalness={0.3}
        />
      </mesh>

      <mesh position={[0, 1.9, -3.0]} castShadow>
        <boxGeometry args={[2.4, 2.9, 6.4]} />
        <meshStandardMaterial
          color={colors.primary}
          roughness={0.3}
          metalness={0.05}
        />
      </mesh>

      <mesh position={[0, 1.0, -3.0]}>
        <boxGeometry args={[2.42, 0.14, 6.42]} />
        <meshStandardMaterial
          color={colors.card}
          roughness={0.4}
          metalness={0.05}
        />
      </mesh>

      <mesh position={[0, 0.05, -7.5]} rotation={[-Math.PI / 2, 0, 0]}>
        <planeGeometry args={[2.6, 6]} />
        <meshBasicMaterial
          ref={dustMaterialRef}
          map={dustTexture}
          color={colors.lightOrange}
          transparent
          opacity={0.22}
          depthWrite={false}
        />
      </mesh>

      {WHEEL_POSITIONS.map((position, index) => (
        <Wheel
          key={`${position[0]}-${position[1]}-${position[2]}`}
          position={position}
          wheelRef={setWheelRef(index)}
        />
      ))}
    </group>
  );
}