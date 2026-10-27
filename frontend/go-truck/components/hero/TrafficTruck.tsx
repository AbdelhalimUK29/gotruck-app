import { useRef } from "react";
import { useFrame } from "@react-three/fiber";
import * as THREE from "three";

import { trafficMotion } from "./heroAnimation";

const WHEEL_ROTATION = Math.PI / 2;
const GLASS_COLOR = "#1F2937";
const CHASSIS_COLOR = "#374151";
const TIRE_COLOR = "#1F2937";
const WHEEL_RADIUS = 0.45;

const WHEEL_POSITIONS: [number, number, number][] = [
  [-0.85, 0.45, 1.9],
  [0.85, 0.45, 1.9],
  [-0.85, 0.45, -1.9],
  [0.85, 0.45, -1.9],
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
        <cylinderGeometry args={[WHEEL_RADIUS, WHEEL_RADIUS, 0.28, 16]} />
        <meshStandardMaterial
          color={TIRE_COLOR}
          roughness={0.8}
          metalness={0.1}
        />
      </mesh>
    </group>
  );
}

export default function TrafficTruck({
  color,
  laneOffsetX,
  phase,
}: {
  color: string;
  laneOffsetX: number;
  phase: number;
}) {
  const groupRef = useRef<THREE.Group>(null);
  const wheelRefs = useRef<(THREE.Mesh | null)[]>([]);

  const setWheelRef =
    (index: number) => (mesh: THREE.Mesh | null) => {
      wheelRefs.current[index] = mesh;
    };

  useFrame((state) => {
    const group = groupRef.current;

    if (!group) return;

    const loopLength =
      (trafficMotion.enterZ - trafficMotion.loopFarZ) /
      trafficMotion.speed;

    const rawTravel = Math.max(
      0,
      state.clock.elapsedTime - trafficMotion.enterTime,
    );

    const passes = Math.floor(rawTravel / loopLength);
    let cycleTime = rawTravel % loopLength;

    if (passes >= 1) {
      cycleTime = (cycleTime + phase) % loopLength;
    }

    const distance = cycleTime * trafficMotion.speed;

    group.position.z = trafficMotion.enterZ - distance;

    const spin = distance / WHEEL_RADIUS;

    wheelRefs.current.forEach((wheel) => {
      if (wheel) {
        wheel.rotation.y = -spin;
      }
    });
  });

  return (
    <group
      ref={groupRef}
      position={[laneOffsetX, 0, trafficMotion.enterZ]}
      rotation={[0, Math.PI, 0]}
      scale={trafficMotion.scale}
    >
      <mesh position={[0, 1.35, -0.5]} castShadow>
        <boxGeometry args={[1.9, 2.2, 4.6]} />
        <meshStandardMaterial
          color={color}
          roughness={0.35}
          metalness={0.05}
        />
      </mesh>

      <mesh position={[0, 1.15, 2.4]} castShadow>
        <boxGeometry args={[1.8, 1.6, 1.8]} />
        <meshStandardMaterial
          color={color}
          roughness={0.35}
          metalness={0.05}
        />
      </mesh>

      <mesh position={[0, 1.7, 3.25]} rotation={[-0.2, 0, 0]}>
        <boxGeometry args={[1.6, 0.6, 0.08]} />
        <meshStandardMaterial
          color={GLASS_COLOR}
          roughness={0.15}
          metalness={0.5}
        />
      </mesh>

      <mesh position={[0, 0.22, 0]} castShadow>
        <boxGeometry args={[1.8, 0.45, 6.6]} />
        <meshStandardMaterial
          color={CHASSIS_COLOR}
          roughness={0.7}
          metalness={0.3}
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