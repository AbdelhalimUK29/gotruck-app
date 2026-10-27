import { useMemo } from "react";
import * as THREE from "three";

import { colors } from "./heroAnimation";

const ROAD_NEAR_HALF = 9;
const ROAD_FAR_HALF = 3.2;
const ROAD_NEAR_Z = 5;
const ROAD_FAR_Z = -70;
const ROAD_COLOR = "#4B4B4B";

function buildRoadGeometry(): THREE.BufferGeometry {
  const positions = new Float32Array([
    -ROAD_FAR_HALF, 0, ROAD_FAR_Z,
    ROAD_FAR_HALF, 0, ROAD_FAR_Z,
    ROAD_NEAR_HALF, 0, ROAD_NEAR_Z,
    -ROAD_NEAR_HALF, 0, ROAD_NEAR_Z,
  ]);

  const geometry = new THREE.BufferGeometry();

  geometry.setAttribute(
    "position",
    new THREE.BufferAttribute(positions, 3),
  );
  geometry.setIndex([0, 2, 1, 0, 3, 2]);
  geometry.computeVertexNormals();

  return geometry;
}

function buildCenterDashGeometry(
  dashCount: number,
): THREE.BufferGeometry {
  const dashLength = 4.2;
  const gap = (ROAD_NEAR_Z - ROAD_FAR_Z) / dashCount;
  const positions: number[] = [];

  for (let i = 0; i < dashCount; i += 1) {
    const z = ROAD_FAR_Z + i * gap;
    const nextZ = Math.min(z + dashLength, ROAD_NEAR_Z);

    positions.push(
      -0.09, 0.012, z,
      0.09, 0.012, z,
      0.09, 0.012, nextZ,
      -0.09, 0.012, nextZ,
    );
  }

  const geometry = new THREE.BufferGeometry();

  geometry.setAttribute(
    "position",
    new THREE.Float32BufferAttribute(positions, 3),
  );

  const index: number[] = [];

  for (let i = 0; i < dashCount; i += 1) {
    const o = i * 4;

    index.push(o, o + 2, o + 1, o, o + 3, o + 2);
  }

  geometry.setIndex(index);
  geometry.computeVertexNormals();

  return geometry;
}

export default function Road() {
  const roadGeometry = useMemo(() => buildRoadGeometry(), []);
  const dashGeometry = useMemo(
    () => buildCenterDashGeometry(10),
    [],
  );

  return (
    <group>
      <mesh
        geometry={roadGeometry}
        receiveShadow
      >
        <meshStandardMaterial
          color={ROAD_COLOR}
          roughness={0.95}
          metalness={0.05}
        />
      </mesh>

      <mesh geometry={dashGeometry}>
        <meshStandardMaterial
          color={colors.border}
          roughness={0.9}
          metalness={0}
        />
      </mesh>
    </group>
  );
}