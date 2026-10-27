import { colors } from "./heroAnimation";

export default function Environment() {
  return (
    <>
      <color attach="background" args={[colors.bg]} />

      <fog attach="fog" args={[colors.bg, 30, 85]} />

      <hemisphereLight
        args={[colors.card, colors.lightOrange, 0.9]}
      />

      <directionalLight
        position={[8, 12, 6]}
        intensity={1.4}
        color={colors.card}
        castShadow
        shadow-mapSize={[1024, 1024]}
        shadow-camera-left={-24}
        shadow-camera-right={24}
        shadow-camera-top={24}
        shadow-camera-bottom={-24}
        shadow-camera-near={1}
        shadow-camera-far={120}
        shadow-bias={-0.0004}
      />

      <directionalLight
        position={[-6, 4, -6]}
        intensity={0.35}
        color={colors.lightOrange}
      />
    </>
  );
}