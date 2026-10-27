import { useMemo, useRef } from "react";
import { useFrame } from "@react-three/fiber";
import * as THREE from "three";

import { animationTimeline, colors } from "./heroAnimation";
import { createSoftTexture } from "./particleTexture";
import { useDeviceTier, usePrefersReducedMotion } from "./motionPrefs";

const BURST_COUNT = 40;
const BURST_LIFETIME = 1.4;
const BURST_START = animationTimeline.crossing;
const GLOW_DURATION = 1.6;
const SPREAD_X = 2.6;
const SPREAD_Y = 1.1;
const SPREAD_Z = 1.6;

const CROSSING_POINT = new THREE.Vector3(0, 1.3, -5);

const BURST_VERTEX_SHADER = `
  attribute float aOpacity;
  attribute float aSize;
  attribute vec3 color;

  uniform float uPixelRatio;

  varying float vOpacity;
  varying vec3 vColor;

  void main() {
    vOpacity = aOpacity;
    vColor = color;

    vec4 mvPosition = modelViewMatrix * vec4(position, 1.0);

    gl_PointSize = aSize * uPixelRatio * (250.0 / -mvPosition.z);
    gl_Position = projectionMatrix * mvPosition;
  }
`;

const BURST_FRAGMENT_SHADER = `
  uniform sampler2D uTexture;

  varying float vOpacity;
  varying vec3 vColor;

  void main() {
    vec4 tex = texture2D(uTexture, gl_PointCoord);

    if (vOpacity <= 0.0) discard;

    gl_FragColor = vec4(vColor, vOpacity * tex.a);
  }
`;

function mulberry32(seed: number) {
  return function random() {
    let t = (seed += 0x6d2b79f5);

    t = Math.imul(t ^ (t >>> 15), t | 1);
    t ^= t + Math.imul(t ^ (t >>> 7), t | 61);

    return ((t ^ (t >>> 14)) >>> 0) / 4294967296;
  };
}

function easeOutCubic(x: number): number {
  return 1 - Math.pow(1 - x, 3);
}

export default function Particles() {
  const pointsRef = useRef<THREE.Points>(null);
  const glowRef = useRef<THREE.Sprite>(null);
  const reducedMotion = usePrefersReducedMotion();
  const tier = useDeviceTier();
  const count = reducedMotion
    ? 0
    : tier === "mobile"
      ? 16
      : tier === "tablet"
        ? 28
        : BURST_COUNT;

  const texture = useMemo(() => createSoftTexture(), []);

  const particles = useMemo(() => {
    const rand = mulberry32(1337);
    const position = new Float32Array(count * 3);
    const color = new Float32Array(count * 3);
    const velocity = new Float32Array(count * 3);
    const delay = new Float32Array(count);
    const size = new Float32Array(count);

    const base = new THREE.Color(colors.primary);
    const light = new THREE.Color(colors.lightOrange);
    const tmp = new THREE.Color();

    for (let i = 0; i < count; i += 1) {
      const ox = (rand() - 0.5) * 2 * SPREAD_X;
      const oy = rand() * SPREAD_Y + 0.3;
      const oz = (rand() - 0.5) * 2 * SPREAD_Z;

      position[i * 3] = CROSSING_POINT.x + ox;
      position[i * 3 + 1] = CROSSING_POINT.y + oy;
      position[i * 3 + 2] = CROSSING_POINT.z + oz;

      tmp.copy(base).lerp(light, rand());
      color[i * 3] = tmp.r;
      color[i * 3 + 1] = tmp.g;
      color[i * 3 + 2] = tmp.b;

      velocity[i * 3] = (Math.sign(ox) || 1) * (0.4 + rand() * 0.8);
      velocity[i * 3 + 1] = 0.5 + rand() * 0.9;
      velocity[i * 3 + 2] = (rand() - 0.5) * 1.2;

      delay[i] = rand() * 0.3;
      size[i] = 0.4 + rand() * 0.7;
    }

    return { position, color, velocity, delay, size };
  }, [count]);

  useFrame((state) => {
    const points = pointsRef.current;

    if (points) {
      const geometry = points.geometry;
      const t = state.clock.elapsedTime;

      for (let i = 0; i < count; i += 1) {
        const start = BURST_START + particles.delay[i];
        const age = (t - start) / BURST_LIFETIME;

        let alpha = 0;
        let px = particles.position[i * 3];
        let py = particles.position[i * 3 + 1];
        let pz = particles.position[i * 3 + 2];

        if (age >= 0 && age <= 1) {
          alpha = Math.sin(age * Math.PI);
          const p = easeOutCubic(age);

          px += particles.velocity[i * 3] * p;
          py += particles.velocity[i * 3 + 1] * p;
          pz += particles.velocity[i * 3 + 2] * p;
        }

        geometry.attributes.position.array[i * 3] = px;
        geometry.attributes.position.array[i * 3 + 1] = py;
        geometry.attributes.position.array[i * 3 + 2] = pz;
        (
          geometry.attributes.aOpacity as THREE.BufferAttribute
        ).array[i] = alpha;
      }

      geometry.attributes.position.needsUpdate = true;
      (geometry.attributes.aOpacity as THREE.BufferAttribute).needsUpdate =
        true;

      (
        points.material as THREE.ShaderMaterial
      ).uniforms.uPixelRatio.value = state.gl.getPixelRatio();
    }

    const glow = glowRef.current;

    if (glow) {
      const material = glow.material as THREE.SpriteMaterial;
      const age = (state.clock.elapsedTime - BURST_START) / GLOW_DURATION;

      if (age >= 0 && age <= 1) {
        material.opacity = 0.16 * Math.sin(age * Math.PI);
        const s = 4 + 6 * age;

        glow.scale.set(s, s, 1);
      } else {
        material.opacity = 0;
      }
    }
  });

  if (count === 0) {
    return null;
  }

  return (
    <>
      <points ref={pointsRef} frustumCulled={false}>
        <bufferGeometry>
          <bufferAttribute
            attach="attributes-position"
            args={[particles.position, 3]}
          />
          <bufferAttribute
            attach="attributes-color"
            args={[particles.color, 3]}
          />
          <bufferAttribute
            attach="attributes-aOpacity"
            args={[new Float32Array(count), 1]}
          />
          <bufferAttribute
            attach="attributes-aSize"
            args={[particles.size, 1]}
          />
        </bufferGeometry>

        <shaderMaterial
          transparent
          depthWrite={false}
          blending={THREE.NormalBlending}
          vertexShader={BURST_VERTEX_SHADER}
          fragmentShader={BURST_FRAGMENT_SHADER}
          uniforms={{
            uPixelRatio: { value: 1 },
            uTexture: { value: texture },
          }}
        />
      </points>

      <sprite ref={glowRef} position={[0, 1.1, -5]}>
        <spriteMaterial
          map={texture}
          color={colors.primary}
          transparent
          opacity={0}
          depthWrite={false}
          blending={THREE.NormalBlending}
        />
      </sprite>
    </>
  );
}